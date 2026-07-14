import 'dart:io';

import 'package:a005_local_queue_experiment/local_queue.dart';
import 'package:test/test.dart';

void main() {
  late Directory temporaryDirectory;
  late File databaseFile;
  late QueueScope orgA;
  late DateTime now;

  setUp(() {
    temporaryDirectory = Directory.systemTemp.createTempSync(
      'a005-local-queue-',
    );
    databaseFile = File('${temporaryDirectory.path}/opaque-profile.db');
    orgA = const QueueScope.organization('teacher-a', 'org-a');
    now = DateTime.utc(2026, 7, 14, 12);
  });

  tearDown(() => temporaryDirectory.deleteSync(recursive: true));

  Future<LocalQueue> open([String key = 'correct-key']) => LocalQueue.open(
    databaseFile: databaseFile,
    databaseKey: key,
    now: () => now,
  );

  Future<void> enqueue(
    LocalQueue queue,
    QueueReference reference, {
    String mutation = 'mutation-1',
    String target = 'attendance:student-a',
    List<QueueReference> dependsOn = const [],
  }) => queue.enqueue(
    reference: reference,
    clientMutationId: mutation,
    targetOrderingKey: target,
    method: 'PATCH',
    path: '/v1/attendance',
    requestBody: '{"status":"PRESENT"}',
    expectedRowVersion: 4,
    dependsOn: dependsOn,
  );

  test(
    'sqlite3mc cipher etkin; aynı anahtarla kapat-aç kayıt ve anahtarı korur',
    () async {
      final reference = QueueReference(orgA, 'op-1');
      final queue = await open();
      expect(queue.cipherVersion, isNotEmpty);
      await enqueue(queue, reference, mutation: 'mutation-unchanged');
      await queue.markTransientFailure(reference, 'NETWORK_UNAVAILABLE');
      await queue.close();

      final reopened = await open();
      final item = await reopened.find(reference);
      expect(item!.state, QueueState.retryWait);
      expect(item.clientMutationId, 'mutation-unchanged');
      await reopened.close();
    },
  );

  test('yanlış anahtar şifreli veritabanını açıp okuyamaz', () async {
    final queue = await open();
    await enqueue(queue, QueueReference(orgA, 'op-1'));
    await queue.close();

    expect(open('wrong-key'), throwsA(isA<Object>()));
  });

  test('foreign key denetimi bağlantı setup aşamasında etkindir', () async {
    final queue = await open();
    expect(queue.foreignKeysEnabled, isTrue);
    await queue.close();
  });

  test(
    'yarım SYNCING kayıt yeniden açılışta aynı anahtarla PENDING olur',
    () async {
      final reference = QueueReference(orgA, 'op-1');
      final queue = await open();
      await enqueue(queue, reference, mutation: 'mutation-unchanged');
      await queue.markSyncing(reference);
      await queue.close();

      final reopened = await open();
      await reopened.recoverAfterRestart(orgA);
      final item = await reopened.find(reference);
      expect(item!.state, QueueState.pending);
      expect(item.clientMutationId, 'mutation-unchanged');
      await reopened.close();
    },
  );

  test(
    'kesin başarıdan önce temizlik bağlamlı kuyruk satırını silemez',
    () async {
      final reference = QueueReference(orgA, 'op-1');
      final queue = await open();
      await enqueue(queue, reference);
      await queue.cleanupSucceeded(reference);
      expect(await queue.find(reference), isNotNull);
      await queue.markSucceeded(reference);
      await queue.cleanupSucceeded(reference);
      expect(await queue.find(reference), isNull);
      await queue.close();
    },
  );

  test(
    'başka kullanıcı aynı localOperationId ile kaydı okuyamaz, değiştiremez veya silemez',
    () async {
      final owner = QueueReference(orgA, 'known-operation');
      const foreignScope = QueueScope.organization('teacher-b', 'org-a');
      final foreign = QueueReference(foreignScope, 'known-operation');
      final queue = await open();
      await enqueue(queue, owner);
      expect(await queue.find(foreign), isNull);
      await queue.markSucceeded(foreign);
      await queue.cleanupSucceeded(foreign);
      expect((await queue.find(owner))!.state, QueueState.pending);
      await queue.close();
    },
  );

  test(
    'başka kurum aynı localOperationId ile kaydı okuyamaz, değiştiremez veya silemez',
    () async {
      final owner = QueueReference(orgA, 'known-operation');
      const foreignScope = QueueScope.organization('teacher-a', 'org-b');
      final foreign = QueueReference(foreignScope, 'known-operation');
      final queue = await open();
      await enqueue(queue, owner);
      expect(await queue.find(foreign), isNull);
      await queue.markTerminalFailure(foreign, 'FORBIDDEN');
      await queue.cleanupSucceeded(foreign);
      expect((await queue.find(owner))!.state, QueueState.pending);
      await queue.close();
    },
  );

  test(
    'GLOBAL ve ORGANIZATION kapsamları NULL-güvenli tekillikle ayrılır',
    () async {
      const global = QueueScope.global('teacher-a');
      final queue = await open();
      await enqueue(
        queue,
        QueueReference(global, 'op-global'),
        mutation: 'shared',
      );
      await enqueue(queue, QueueReference(orgA, 'op-org'), mutation: 'shared');
      expect(await queue.find(QueueReference(global, 'op-global')), isNotNull);
      expect(await queue.find(QueueReference(orgA, 'op-org')), isNotNull);
      await queue.close();
    },
  );

  test(
    'ters alfabetik kimliklerde gönderim createdSequence sırasını korur',
    () async {
      final queue = await open();
      await enqueue(
        queue,
        QueueReference(orgA, 'z-first'),
        target: 'student-a',
      );
      await enqueue(
        queue,
        QueueReference(orgA, 'a-second'),
        mutation: 'mutation-2',
        target: 'student-b',
      );
      final sendable = await queue.sendableFor(orgA);
      expect(sendable.map((item) => item.reference.localOperationId), [
        'z-first',
        'a-second',
      ]);
      await queue.close();
    },
  );

  test('aynı hedefte sonraki yazı önceki başarıya kadar seçilemez', () async {
    final first = QueueReference(orgA, 'first');
    final second = QueueReference(orgA, 'second');
    final queue = await open();
    await enqueue(queue, first, target: 'student-a');
    await enqueue(queue, second, target: 'student-a', mutation: 'mutation-2');
    expect(
      (await queue.sendableFor(
        orgA,
      )).map((item) => item.reference.localOperationId),
      ['first'],
    );
    await queue.markSucceeded(first);
    expect(
      (await queue.sendableFor(
        orgA,
      )).map((item) => item.reference.localOperationId),
      ['second'],
    );
    await queue.close();
  });

  test(
    'ikinci aynı-hedef kayıt ilk başarıdan önce doğrudan claim edilemez',
    () async {
      final first = QueueReference(orgA, 'first');
      final second = QueueReference(orgA, 'second');
      final queue = await open();
      await enqueue(queue, first, target: 'student-a');
      await enqueue(queue, second, target: 'student-a', mutation: 'mutation-2');
      expect(await queue.claim(second), isNull);
      await queue.markSucceeded(first);
      expect((await queue.claim(second))!.state, QueueState.syncing);
      await queue.close();
    },
  );

  test(
    'tamamlanmamış bağımlılık bağlı işlemi engeller; başarıdan sonra açar',
    () async {
      final dependency = QueueReference(orgA, 'create');
      final dependent = QueueReference(orgA, 'update');
      final queue = await open();
      await enqueue(queue, dependency, target: 'student-a');
      await enqueue(
        queue,
        dependent,
        mutation: 'mutation-2',
        target: 'student-b',
        dependsOn: [dependency],
      );
      expect(
        (await queue.sendableFor(
          orgA,
        )).map((item) => item.reference.localOperationId),
        ['create'],
      );
      await queue.markSucceeded(dependency);
      expect(
        (await queue.sendableFor(
          orgA,
        )).map((item) => item.reference.localOperationId),
        ['update'],
      );
      await queue.close();
    },
  );

  test('eksik dependency enqueue işleminin tamamını rollback eder', () async {
    final reference = QueueReference(orgA, 'dependent');
    final missing = QueueReference(orgA, 'missing');
    final queue = await open();
    await expectLater(
      enqueue(queue, reference, dependsOn: [missing]),
      throwsStateError,
    );
    expect(await queue.find(reference), isNull);
    expect(await queue.sendableFor(orgA), isEmpty);
    await queue.close();
  });

  test(
    'iki eşzamanlı claim denemesinden yalnız biri kaydı SYNCING yapar',
    () async {
      final reference = QueueReference(orgA, 'op-1');
      final queue = await open();
      await enqueue(queue, reference);
      final claims = await Future.wait([
        queue.claim(reference),
        queue.claim(reference),
      ]);
      expect(claims.whereType<QueueItem>(), hasLength(1));
      expect((await queue.find(reference))!.state, QueueState.syncing);
      await queue.close();
    },
  );

  test('iptal edilmiş oturumun kuyruğu BLOCKED kalır ve seçilemez', () async {
    final reference = QueueReference(orgA, 'op-1');
    final queue = await open();
    await enqueue(queue, reference);
    await queue.markBlocked(reference, 'SESSION_REVOKED');
    expect((await queue.find(reference))!.state, QueueState.blocked);
    expect(await queue.sendableFor(orgA), isEmpty);
    await queue.close();
  });

  test(
    'gelecekteki RETRY_WAIT yeniden açılışta seçilemez, zamanı gelince aynı anahtarla seçilir',
    () async {
      final reference = QueueReference(orgA, 'op-1');
      final queue = await open();
      await enqueue(queue, reference, mutation: 'mutation-unchanged');
      await queue.claim(reference);
      await queue.markTransientFailure(
        reference,
        'NETWORK_UNAVAILABLE',
        retryAfter: const Duration(minutes: 5),
      );
      await queue.close();

      final reopened = await open();
      expect(await reopened.sendableFor(orgA), isEmpty);
      expect(await reopened.claim(reference), isNull);
      now = now.add(const Duration(minutes: 5));
      expect(
        (await reopened.claim(reference))!.clientMutationId,
        'mutation-unchanged',
      );
      await reopened.close();
    },
  );

  test(
    'claim ve geçici hata tek ağ denemesi için attemptCountı bir kez artırır',
    () async {
      final reference = QueueReference(orgA, 'op-1');
      final queue = await open();
      await enqueue(queue, reference);
      await queue.claim(reference);
      await queue.markTransientFailure(reference, 'NETWORK_UNAVAILABLE');
      expect((await queue.find(reference))!.attemptCount, 1);
      await queue.close();
    },
  );
}
