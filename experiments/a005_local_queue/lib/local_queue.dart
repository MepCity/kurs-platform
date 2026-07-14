import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';

enum ScopeType {
  global('GLOBAL'),
  organization('ORGANIZATION');

  const ScopeType(this.value);
  final String value;
}

enum QueueState {
  pending('PENDING'),
  syncing('SYNCING'),
  retryWait('RETRY_WAIT'),
  succeeded('SUCCEEDED'),
  needsAttention('NEEDS_ATTENTION'),
  blocked('BLOCKED');

  const QueueState(this.value);
  final String value;

  static QueueState parse(String value) =>
      QueueState.values.singleWhere((state) => state.value == value);
}

class QueueScope {
  const QueueScope.global(this.actorUserId)
    : organizationId = null,
      type = ScopeType.global;

  const QueueScope.organization(this.actorUserId, this.organizationId)
    : assert(organizationId != null),
      type = ScopeType.organization;

  final String actorUserId;
  final ScopeType type;
  final String? organizationId;
}

class QueueReference {
  const QueueReference(this.scope, this.localOperationId);

  final QueueScope scope;
  final String localOperationId;
}

class QueueItem {
  const QueueItem({
    required this.reference,
    required this.clientMutationId,
    required this.state,
    required this.createdSequence,
    required this.targetOrderingKey,
    required this.attemptCount,
    this.lastErrorCode,
  });

  final QueueReference reference;
  final String clientMutationId;
  final QueueState state;
  final int createdSequence;
  final String targetOrderingKey;
  final int attemptCount;
  final String? lastErrorCode;
}

/// A-005 deneyi için tek Drift NativeDatabase yürütücüsü.
///
/// Üretimde foreground ve OS background göndericileri bu tek dispatcher'ın arkasından
/// çalışır. Claim işlemi koşullu UPDATE ... RETURNING ile tek SQL ifadesidir; böylece iki
/// yürütücü aynı kaydı gönderemez.
class LocalQueue {
  LocalQueue._(
    this._executor,
    this.cipherVersion,
    this.foreignKeysEnabled,
    this._now,
  );

  final NativeDatabase _executor;
  final String cipherVersion;
  final bool foreignKeysEnabled;
  final DateTime Function() _now;

  static Future<LocalQueue> open({
    required File databaseFile,
    required String databaseKey,
    DateTime Function()? now,
  }) async {
    final executor = NativeDatabase(
      databaseFile,
      setup: (database) {
        database.execute("PRAGMA key = '${_escape(databaseKey)}'");
        database.execute('PRAGMA foreign_keys = ON');
        final cipher = database.select('PRAGMA cipher');
        if (cipher.isEmpty || cipher.first.values.first == null) {
          throw StateError('sqlite3mc şifreleme desteği etkin değil');
        }
      },
    );

    try {
      // İlk sorgu setup'ı veritabanı açılmadan önce çalıştırır. Yanlış anahtar burada
      // şema okuma/yazma sırasında hata verir; yeni boş veritabanı oluşturulmaz.
      await executor.ensureOpen(_QueueExecutorUser());
      final cipherRows = await executor.runSelect('PRAGMA cipher', const []);
      final sqliteRows = await executor.runSelect(
        'SELECT sqlite_version() AS sqlite_version',
        const [],
      );
      final foreignKeyRows = await executor.runSelect(
        'PRAGMA foreign_keys',
        const [],
      );
      final cipher = cipherRows.single.values.single?.toString();
      final sqliteVersion = sqliteRows.single['sqlite_version']?.toString();
      if (cipher == null ||
          cipher.isEmpty ||
          sqliteVersion == null ||
          sqliteVersion.isEmpty) {
        throw StateError('sqlite3mc cipher/SQLite sürümü doğrulanamadı');
      }
      final foreignKeysEnabled = foreignKeyRows.single.values.single == 1;
      if (!foreignKeysEnabled)
        throw StateError('SQLite foreign_keys etkin değil');
      await _createSchema(executor);
      return LocalQueue._(
        executor,
        '$cipher / SQLite $sqliteVersion',
        foreignKeysEnabled,
        now ?? DateTime.now,
      );
    } catch (_) {
      await executor.close();
      rethrow;
    }
  }

  static Future<void> _createSchema(NativeDatabase executor) async {
    await executor.runCustom('''
      CREATE TABLE IF NOT EXISTS pending_mutations (
        id INTEGER PRIMARY KEY,
        local_operation_id TEXT NOT NULL,
        actor_user_id TEXT NOT NULL,
        scope_type TEXT NOT NULL CHECK (scope_type IN ('GLOBAL', 'ORGANIZATION')),
        organization_id TEXT,
        client_mutation_id TEXT NOT NULL,
        state TEXT NOT NULL CHECK (state IN (
          'PENDING', 'SYNCING', 'RETRY_WAIT', 'SUCCEEDED', 'NEEDS_ATTENTION', 'BLOCKED'
        )),
        created_sequence INTEGER NOT NULL UNIQUE CHECK (created_sequence > 0),
        target_ordering_key TEXT NOT NULL,
        expected_row_version INTEGER,
        method TEXT NOT NULL,
        path TEXT NOT NULL,
        request_body TEXT NOT NULL,
        created_at TEXT NOT NULL,
        last_attempt_at TEXT,
        next_attempt_at TEXT,
        attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
        last_error_code TEXT,
        CHECK (
          (scope_type = 'GLOBAL' AND organization_id IS NULL) OR
          (scope_type = 'ORGANIZATION' AND organization_id IS NOT NULL)
        )
      )
    ''');
    await executor.runCustom('''
      CREATE TABLE IF NOT EXISTS mutation_dependencies (
        dependent_mutation_id INTEGER NOT NULL REFERENCES pending_mutations(id),
        dependency_mutation_id INTEGER NOT NULL REFERENCES pending_mutations(id),
        PRIMARY KEY (dependent_mutation_id, dependency_mutation_id),
        CHECK (dependent_mutation_id <> dependency_mutation_id)
      )
    ''');
    await executor.runCustom('''
      CREATE UNIQUE INDEX IF NOT EXISTS pending_mutations_global_operation_uq
      ON pending_mutations (actor_user_id, scope_type, local_operation_id)
      WHERE scope_type = 'GLOBAL'
    ''');
    await executor.runCustom('''
      CREATE UNIQUE INDEX IF NOT EXISTS pending_mutations_organization_operation_uq
      ON pending_mutations (actor_user_id, scope_type, organization_id, local_operation_id)
      WHERE scope_type = 'ORGANIZATION'
    ''');
    await executor.runCustom('''
      CREATE UNIQUE INDEX IF NOT EXISTS pending_mutations_global_mutation_uq
      ON pending_mutations (actor_user_id, scope_type, client_mutation_id)
      WHERE scope_type = 'GLOBAL'
    ''');
    await executor.runCustom('''
      CREATE UNIQUE INDEX IF NOT EXISTS pending_mutations_organization_mutation_uq
      ON pending_mutations (actor_user_id, scope_type, organization_id, client_mutation_id)
      WHERE scope_type = 'ORGANIZATION'
    ''');
  }

  Future<void> enqueue({
    required QueueReference reference,
    required String clientMutationId,
    required String targetOrderingKey,
    required String method,
    required String path,
    required String requestBody,
    int? expectedRowVersion,
    List<QueueReference> dependsOn = const [],
  }) async {
    for (final dependency in dependsOn) {
      _requireSameScope(reference.scope, dependency.scope);
    }
    final transaction = _executor.beginTransaction();
    await transaction.ensureOpen(_QueueExecutorUser());
    try {
      await transaction.runCustom(
        '''INSERT INTO pending_mutations (
        local_operation_id, actor_user_id, scope_type, organization_id,
        client_mutation_id, state, created_sequence, target_ordering_key,
        expected_row_version, method, path, request_body, created_at
      ) VALUES (?, ?, ?, ?, ?, ?,
        COALESCE((SELECT MAX(created_sequence) + 1 FROM pending_mutations), 1),
        ?, ?, ?, ?, ?, ?)''',
        [
          reference.localOperationId,
          reference.scope.actorUserId,
          reference.scope.type.value,
          reference.scope.organizationId,
          clientMutationId,
          QueueState.pending.value,
          targetOrderingKey,
          expectedRowVersion,
          method,
          path,
          requestBody,
          _now().toUtc().toIso8601String(),
        ],
      );
      final inserted = await _rowFor(reference, transaction);
      for (final dependency in dependsOn) {
        final dependencyRow = await _rowFor(dependency, transaction);
        await transaction.runCustom(
          '''INSERT INTO mutation_dependencies (
          dependent_mutation_id, dependency_mutation_id
        ) VALUES (?, ?)''',
          [inserted['id'], dependencyRow['id']],
        );
      }
      await transaction.send();
    } catch (_) {
      await transaction.rollback();
      rethrow;
    }
  }

  Future<QueueItem?> find(QueueReference reference) async {
    final rows = await _selectForReference(reference);
    return rows.isEmpty ? null : _item(rows.single);
  }

  Future<void> markSyncing(QueueReference reference) =>
      _transition(reference, QueueState.syncing);

  Future<void> markTransientFailure(
    QueueReference reference,
    String errorCode, {
    Duration retryAfter = const Duration(seconds: 30),
  }) => _transition(
    reference,
    QueueState.retryWait,
    errorCode: errorCode,
    nextAttemptAt: _now().add(retryAfter),
  );

  Future<void> markTerminalFailure(
    QueueReference reference,
    String errorCode,
  ) => _transition(reference, QueueState.needsAttention, errorCode: errorCode);

  Future<void> markBlocked(QueueReference reference, String errorCode) =>
      _transition(reference, QueueState.blocked, errorCode: errorCode);

  Future<void> markSucceeded(QueueReference reference) =>
      _transition(reference, QueueState.succeeded);

  Future<void> cleanupSucceeded(QueueReference reference) async {
    await _executor.runCustom(
      '''DELETE FROM pending_mutations
         WHERE local_operation_id = ? AND actor_user_id = ? AND scope_type = ?
           AND organization_id IS ? AND state = ?''',
      [
        reference.localOperationId,
        reference.scope.actorUserId,
        reference.scope.type.value,
        reference.scope.organizationId,
        QueueState.succeeded.value,
      ],
    );
  }

  Future<void> recoverAfterRestart(QueueScope scope) async {
    await _executor.runCustom(
      '''UPDATE pending_mutations SET state = ?
         WHERE actor_user_id = ? AND scope_type = ? AND organization_id IS ? AND state = ?''',
      [
        QueueState.pending.value,
        scope.actorUserId,
        scope.type.value,
        scope.organizationId,
        QueueState.syncing.value,
      ],
    );
  }

  Future<List<QueueItem>> sendableFor(QueueScope scope) async {
    final rows = await _executor.runSelect(
      '''SELECT q.* FROM pending_mutations q
         WHERE q.actor_user_id = ? AND q.scope_type = ? AND q.organization_id IS ?
           ${_availabilityConditions('q')}
         ORDER BY q.created_sequence''',
      [
        scope.actorUserId,
        scope.type.value,
        scope.organizationId,
        ..._availabilityArguments(_now()),
      ],
    );
    return rows.map(_item).toList();
  }

  /// Atomik claim: sadece etkin bağlamdaki gönderilebilir kayıt PENDING/RETRY_WAIT'ten
  /// SYNCING'e geçer. RETURNING satırı olmayan çağrı claim'i kaybetmiştir.
  Future<QueueItem?> claim(QueueReference reference) async {
    final rows = await _executor.runSelect(
      '''UPDATE pending_mutations
         SET state = ?, last_attempt_at = ?, attempt_count = attempt_count + 1
         WHERE id = (
           SELECT q.id FROM pending_mutations q
           WHERE q.local_operation_id = ? AND q.actor_user_id = ?
             AND q.scope_type = ? AND q.organization_id IS ?
             ${_availabilityConditions('q')}
           LIMIT 1
         )
         RETURNING *''',
      [
        QueueState.syncing.value,
        _now().toUtc().toIso8601String(),
        reference.localOperationId,
        reference.scope.actorUserId,
        reference.scope.type.value,
        reference.scope.organizationId,
        ..._availabilityArguments(_now()),
      ],
    );
    return rows.isEmpty ? null : _item(rows.single);
  }

  Future<void> _transition(
    QueueReference reference,
    QueueState state, {
    String? errorCode,
    DateTime? nextAttemptAt,
  }) async {
    await _executor.runCustom(
      '''UPDATE pending_mutations
         SET state = ?, last_error_code = ?, next_attempt_at = ?
         WHERE local_operation_id = ? AND actor_user_id = ? AND scope_type = ?
           AND organization_id IS ?''',
      [
        state.value,
        errorCode,
        nextAttemptAt?.toUtc().toIso8601String(),
        reference.localOperationId,
        reference.scope.actorUserId,
        reference.scope.type.value,
        reference.scope.organizationId,
      ],
    );
  }

  Future<Map<String, Object?>> _rowFor(
    QueueReference reference, [
    QueryExecutor? executor,
  ]) async {
    final rows = await _selectForReference(reference, executor);
    if (rows.isEmpty) throw StateError('Kuyruk girdisi bulunamadı');
    return rows.single;
  }

  Future<List<Map<String, Object?>>> _selectForReference(
    QueueReference reference, [
    QueryExecutor? executor,
  ]) => (executor ?? _executor).runSelect(
    '''SELECT * FROM pending_mutations
           WHERE local_operation_id = ? AND actor_user_id = ? AND scope_type = ?
             AND organization_id IS ?''',
    [
      reference.localOperationId,
      reference.scope.actorUserId,
      reference.scope.type.value,
      reference.scope.organizationId,
    ],
  );

  QueueItem _item(Map<String, Object?> row) {
    final scope = switch (row['scope_type']) {
      'GLOBAL' => QueueScope.global(row['actor_user_id']! as String),
      'ORGANIZATION' => QueueScope.organization(
        row['actor_user_id']! as String,
        row['organization_id']! as String,
      ),
      _ => throw StateError('Geçersiz kapsam'),
    };
    return QueueItem(
      reference: QueueReference(scope, row['local_operation_id']! as String),
      clientMutationId: row['client_mutation_id']! as String,
      state: QueueState.parse(row['state']! as String),
      createdSequence: row['created_sequence']! as int,
      targetOrderingKey: row['target_ordering_key']! as String,
      attemptCount: row['attempt_count']! as int,
      lastErrorCode: row['last_error_code'] as String?,
    );
  }

  static String _availabilityConditions(String alias) =>
      '''
AND $alias.state IN (?, ?)
AND ($alias.next_attempt_at IS NULL OR $alias.next_attempt_at <= ?)
AND NOT EXISTS (
  SELECT 1 FROM mutation_dependencies md
  JOIN pending_mutations dependency ON dependency.id = md.dependency_mutation_id
  WHERE md.dependent_mutation_id = $alias.id AND dependency.state <> ?
)
AND NOT EXISTS (
  SELECT 1 FROM pending_mutations earlier
  WHERE earlier.actor_user_id = $alias.actor_user_id
    AND earlier.scope_type = $alias.scope_type
    AND earlier.organization_id IS $alias.organization_id
    AND earlier.target_ordering_key = $alias.target_ordering_key
    AND earlier.created_sequence < $alias.created_sequence
    AND earlier.state <> ?
)''';

  static List<Object?> _availabilityArguments(DateTime now) => [
    QueueState.pending.value,
    QueueState.retryWait.value,
    now.toUtc().toIso8601String(),
    QueueState.succeeded.value,
    QueueState.succeeded.value,
  ];

  static String _escape(String value) => value.replaceAll("'", "''");

  static void _requireSameScope(QueueScope a, QueueScope b) {
    if (a.actorUserId != b.actorUserId ||
        a.type != b.type ||
        a.organizationId != b.organizationId) {
      throw ArgumentError('Bağımlılık aynı yerel bağlamda olmalıdır');
    }
  }

  Future<void> close() => _executor.close();
}

class _QueueExecutorUser extends QueryExecutorUser {
  @override
  int get schemaVersion => 1;

  @override
  Future<void> beforeOpen(
    QueryExecutor executor,
    OpeningDetails details,
  ) async {}
}
