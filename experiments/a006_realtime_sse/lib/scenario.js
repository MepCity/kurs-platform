import assert from 'node:assert/strict';
import { ExperimentClient, writeAttendance } from './experiment-client.js';
import { createRealtimeExperimentServer } from './realtime-server.js';

export async function runScenario() {
  const server = createRealtimeExperimentServer();
  const baseUrl = await server.start();
  const clientA = new ExperimentClient({ baseUrl, classId: 'class-a', token: 'teacher-a' });
  const clientB = new ExperimentClient({ baseUrl, classId: 'class-a', token: 'teacher-b' });

  try {
    await Promise.all([clientA.connect(), clientB.connect()]);

    const firstWrite = await writeAttendance({
      baseUrl,
      classId: 'class-a',
      token: 'teacher-a',
      studentId: 'student-1',
      status: 'PRESENT',
    });
    assert.equal(firstWrite.response.status, 200);
    const [firstA, firstB] = await Promise.all([
      clientA.waitFor('entity.changed'),
      clientB.waitFor('entity.changed'),
    ]);
    assert.equal(firstA.data.eventId, firstB.data.eventId);
    assert.equal(firstA.data.changeSequence, 1);
    assert.equal(firstB.data.changeSequence, 1);
    assert.equal('status' in firstA.data, false);

    const initialReconciliation = await clientB.reconcile('student-1');
    assert.deepEqual(
      initialReconciliation.changeFeed.changes.map((change) => change.changeSequence),
      [1],
    );
    assert.equal(initialReconciliation.canonical.status, 'PRESENT');

    clientB.disconnect();
    await clientB.readLoop;

    const secondWrite = await writeAttendance({
      baseUrl,
      classId: 'class-a',
      token: 'teacher-a',
      studentId: 'student-1',
      status: 'ABSENT',
    });
    assert.equal(secondWrite.response.status, 200);
    const secondA = await clientA.waitFor('entity.changed');
    assert.equal(secondA.data.changeSequence, 2);
    assert.equal(clientB.lastSequence, 1);

    const reconnectStartedAt = performance.now();
    const ready = await clientB.connect();
    assert.equal(ready.data.headSequence, 2);
    const reconciliation = await clientB.reconcile('student-1');
    const reconciledAt = performance.now();
    assert.deepEqual(reconciliation.changeFeed.changes.map((change) => change.changeSequence), [2]);
    assert.equal(reconciliation.canonical.status, 'ABSENT');
    assert.equal(reconciliation.canonical.rowVersion, 2);
    assert.equal(clientB.lastSequence, 2);

    const forbidden = await fetch(`${baseUrl}/api/v1/realtime/classes/class-a/events`, {
      headers: { authorization: 'Bearer other-organization-teacher' },
    });
    assert.equal(forbidden.status, 403);
    assert.deepEqual(await forbidden.json(), { code: 'FORBIDDEN' });

    const forbiddenClass = await fetch(`${baseUrl}/api/v1/realtime/classes/class-a/events`, {
      headers: { authorization: 'Bearer same-organization-other-class-teacher' },
    });
    assert.equal(forbiddenClass.status, 403);
    assert.deepEqual(await forbiddenClass.json(), { code: 'FORBIDDEN' });

    const foreignTokenUse = await fetch(
      `${baseUrl}/api/v1/sync/classes/class-a/changes?syncToken=${clientB.syncToken}`,
      { headers: { authorization: 'Bearer teacher-a' } },
    );
    assert.equal(foreignTokenUse.status, 409);
    assert.deepEqual(await foreignTokenUse.json(), { code: 'SYNC_TOKEN_INVALID' });

    const unauthenticated = await fetch(`${baseUrl}/api/v1/realtime/classes/class-a/events`);
    assert.equal(unauthenticated.status, 401);
    assert.deepEqual(await unauthenticated.json(), { code: 'UNAUTHENTICATED' });

    return {
      firstEventId: firstA.data.eventId,
      firstPropagationClientAMs: Number((firstA.receivedAt - firstWrite.sentAt).toFixed(2)),
      firstPropagationClientBMs: Number((firstB.receivedAt - firstWrite.sentAt).toFixed(2)),
      reconnectAndReconcileMs: Number((reconciledAt - reconnectStartedAt).toFixed(2)),
      recoveredSequences: reconciliation.changeFeed.changes.map(
        (change) => change.changeSequence,
      ),
      canonicalStatus: reconciliation.canonical.status,
      canonicalRowVersion: reconciliation.canonical.rowVersion,
      crossOrganizationSubscriptionStatus: forbidden.status,
      unauthorizedClassSubscriptionStatus: forbiddenClass.status,
      foreignUserSyncTokenStatus: foreignTokenUse.status,
      unauthenticatedSubscriptionStatus: unauthenticated.status,
    };
  } finally {
    clientA.disconnect();
    clientB.disconnect();
    await Promise.allSettled([clientA.readLoop, clientB.readLoop]);
    await server.stop();
  }
}
