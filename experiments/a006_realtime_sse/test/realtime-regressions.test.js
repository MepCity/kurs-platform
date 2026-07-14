import test from 'node:test';
import assert from 'node:assert/strict';
import { ExperimentClient, writeAttendance } from '../lib/experiment-client.js';
import { createRealtimeExperimentServer } from '../lib/realtime-server.js';

async function stop(server, clients = []) {
  for (const client of clients) client.disconnect();
  await Promise.allSettled(clients.map((client) => client.readLoop));
  await server.stop();
}

test('stream.ready yalnız doğrulanmış kurum ve sınıf kapsamının yüksek su işaretini verir', async () => {
  const server = createRealtimeExperimentServer();
  const baseUrl = await server.start();
  const client = new ExperimentClient({ baseUrl, classId: 'class-a', token: 'teacher-a' });

  try {
    server.createAttendanceChange({
      token: 'same-organization-other-class-teacher',
      classId: 'class-b',
      studentId: 'same-org-other-class-student',
      status: 'PRESENT',
      deliver: false,
    });
    server.createAttendanceChange({
      token: 'other-organization-teacher',
      classId: 'class-b',
      studentId: 'other-student',
      status: 'PRESENT',
      deliver: false,
    });
    const initialReady = await client.connect();
    assert.equal(initialReady.data.headSequence, 0);

    server.createAttendanceChange({
      token: 'teacher-a',
      classId: 'class-a',
      studentId: 'student-1',
      status: 'PRESENT',
    });
    const gap = await client.waitFor('reconciliation.required');
    assert.equal(gap.data.reason, 'SEQUENCE_GAP');
    const firstSync = await client.waitForReconciliation();
    assert.deepEqual(firstSync.changeFeed.changes.map((change) => change.changeSequence), [3]);
    assert.equal(firstSync.canonicals['student-1'].status, 'PRESENT');

    client.disconnect();
    await client.readLoop;
    server.createAttendanceChange({
      token: 'same-organization-other-class-teacher',
      classId: 'class-b',
      studentId: 'same-org-other-class-student',
      status: 'ABSENT',
      deliver: false,
    });
    server.createAttendanceChange({
      token: 'other-organization-teacher',
      classId: 'class-b',
      studentId: 'other-student',
      status: 'ABSENT',
      deliver: false,
    });

    const reconnectedReady = await client.connect();
    assert.equal(reconnectedReady.data.headSequence, 3);
    const scopedSync = await client.reconcile('student-1');
    assert.deepEqual(scopedSync.changeFeed.changes, []);
    assert.equal(scopedSync.canonical.status, 'PRESENT');
    assert.equal(scopedSync.canonical.rowVersion, 1);
  } finally {
    await stop(server, [client]);
  }
});

test('sınıf ataması veya oturumu iptal edilen aboneye sonraki olay verilmez ve akış kapanır', async () => {
  const server = createRealtimeExperimentServer();
  const baseUrl = await server.start();
  const assignmentRevoked = new ExperimentClient({
    baseUrl,
    classId: 'class-a',
    token: 'teacher-b',
  });
  const sessionRevoked = new ExperimentClient({
    baseUrl,
    classId: 'class-a',
    token: 'teacher-c',
  });

  try {
    await Promise.all([assignmentRevoked.connect(), sessionRevoked.connect()]);
    server.revokeClassAssignment('teacher-b', 'class-a');
    const firstWrite = await writeAttendance({
      baseUrl,
      classId: 'class-a',
      token: 'teacher-a',
      studentId: 'student-1',
      status: 'PRESENT',
    });
    assert.equal(firstWrite.response.status, 200);
    await assignmentRevoked.waitFor('stream.closed');
    const stillAuthorizedEvent = await sessionRevoked.waitFor('entity.changed');
    assert.equal(stillAuthorizedEvent.data.entityId, 'student-1');
    await assert.rejects(assignmentRevoked.waitFor('entity.changed', 25), /Timed out/);
    assert.equal(assignmentRevoked.pendingWaiterCount, 0);

    server.revokeSession('teacher-c');
    const secondWrite = await writeAttendance({
      baseUrl,
      classId: 'class-a',
      token: 'teacher-a',
      studentId: 'student-2',
      status: 'ABSENT',
    });
    assert.equal(secondWrite.response.status, 200);
    await sessionRevoked.waitFor('stream.closed');
    await assert.rejects(sessionRevoked.waitFor('entity.changed', 25), /Timed out/);
    assert.equal(sessionRevoked.pendingWaiterCount, 0);
  } finally {
    await stop(server, [assignmentRevoked, sessionRevoked]);
  }
});

test('ters sıralı farklı olaylar sync ister, aynı eventId yinelenmesi ayıklanır', async () => {
  const server = createRealtimeExperimentServer();
  const baseUrl = await server.start();
  const client = new ExperimentClient({ baseUrl, classId: 'class-a', token: 'teacher-b' });

  try {
    const first = server.createAttendanceChange({
      token: 'teacher-a',
      classId: 'class-a',
      studentId: 'student-1',
      status: 'PRESENT',
      deliver: false,
    });
    const second = server.createAttendanceChange({
      token: 'teacher-a',
      classId: 'class-a',
      studentId: 'student-2',
      status: 'ABSENT',
      deliver: false,
    });
    const ready = await client.connect();
    assert.equal(ready.data.headSequence, 2);

    server.deliverEventsForTest('teacher-b', [second.event, first.event, first.event]);
    const gap = await client.waitFor('reconciliation.required');
    const outOfOrder = await client.waitFor('reconciliation.required');
    assert.equal(gap.data.reason, 'SEQUENCE_GAP');
    assert.equal(gap.data.event.entityId, 'student-2');
    assert.equal(outOfOrder.data.reason, 'OUT_OF_ORDER');
    assert.equal(outOfOrder.data.event.entityId, 'student-1');
    assert.equal(client.duplicateEventCount, 1);

    const reconciliation = await client.waitForReconciliation();
    assert.deepEqual(
      reconciliation.changeFeed.changes.map((change) => change.entityId),
      ['student-1', 'student-2'],
    );
    assert.equal(reconciliation.canonicals['student-1'].status, 'PRESENT');
    assert.equal(reconciliation.canonicals['student-1'].rowVersion, 1);
    assert.equal(reconciliation.canonicals['student-2'].status, 'ABSENT');
    assert.equal(reconciliation.canonicals['student-2'].rowVersion, 1);
    assert.equal(client.reconciliationRunCount, 1);
  } finally {
    await stop(server, [client]);
  }
});

test('waitFor timeout ilgili waiter kaydını temizler', async () => {
  const client = new ExperimentClient({
    baseUrl: 'http://127.0.0.1:1',
    classId: 'class-a',
    token: 'teacher-a',
  });

  await assert.rejects(client.waitFor('never-arrives', 5), /Timed out/);
  assert.equal(client.pendingWaiterCount, 0);
});

test('bağlayıcı kök /api/v1 olur ve eski /v1 yolu sunulmaz', async () => {
  const server = createRealtimeExperimentServer();
  const baseUrl = await server.start();

  try {
    const legacy = await fetch(`${baseUrl}/v1/realtime/classes/class-a/events`, {
      headers: { authorization: 'Bearer teacher-a' },
    });
    assert.equal(legacy.status, 404);
    assert.deepEqual(await legacy.json(), { code: 'NOT_FOUND' });
  } finally {
    await stop(server);
  }
});
