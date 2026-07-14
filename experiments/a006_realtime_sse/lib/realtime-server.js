import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

const IDENTITY_FIXTURES = [
  ['teacher-a', { userId: 'teacher-a', organizationId: 'org-a', classIds: ['class-a'] }],
  ['teacher-b', { userId: 'teacher-b', organizationId: 'org-a', classIds: ['class-a'] }],
  ['teacher-c', { userId: 'teacher-c', organizationId: 'org-a', classIds: ['class-a'] }],
  ['same-organization-other-class-teacher', {
    userId: 'same-organization-other-class-teacher',
    organizationId: 'org-a',
    classIds: ['class-b'],
  }],
  ['other-organization-teacher', {
    userId: 'other-organization-teacher',
    organizationId: 'org-b',
    classIds: ['class-b'],
  }],
];

function createIdentities() {
  return new Map(IDENTITY_FIXTURES.map(([token, fixture]) => [token, {
    ...fixture,
    classIds: new Set(fixture.classIds),
    sessionActive: true,
  }]));
}

function json(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function attendanceKey(organizationId, classId, studentId) {
  return `${organizationId}:${classId}:${studentId}`;
}

export function createRealtimeExperimentServer() {
  let changeSequence = 0;
  const identities = createIdentities();
  const changes = [];
  const attendance = new Map();
  const subscribers = new Set();
  const syncTokens = new Map();

  function authenticate(request) {
    const authorization = request.headers.authorization ?? '';
    const token = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
    const identity = identities.get(token);
    return identity?.sessionActive ? identity : undefined;
  }

  function authorizeClass(identity, classId) {
    return identity?.sessionActive && identity.classIds.has(classId);
  }

  function scopedHeadSequence(organizationId, classId) {
    return changes.reduce((head, change) => (
      change.organizationId === organizationId && change.classId === classId
        ? Math.max(head, change.changeSequence)
        : head
    ), 0);
  }

  function isSubscriberStillAuthorized(subscriber) {
    const liveIdentity = identities.get(subscriber.token);
    return Boolean(
      liveIdentity?.sessionActive
      && liveIdentity.organizationId === subscriber.organizationId
      && liveIdentity.classIds.has(subscriber.classId),
    );
  }

  function deliverEvents(events, selectedUserId) {
    for (const subscriber of [...subscribers]) {
      if (selectedUserId && subscriber.userId !== selectedUserId) continue;
      if (!isSubscriberStillAuthorized(subscriber)) {
        subscribers.delete(subscriber);
        subscriber.response.end();
        continue;
      }
      for (const event of events) {
        if (
          subscriber.organizationId === event.organizationId
          && subscriber.classId === event.classId
        ) {
          subscriber.response.write(
            `id: ${event.eventId}\nevent: entity.changed\ndata: ${JSON.stringify(event)}\n\n`,
          );
        }
      }
    }
  }

  function createAttendanceChange({ token, classId, studentId, status, deliver = true }) {
    const identity = identities.get(token);
    if (!authorizeClass(identity, classId)) throw new Error('Synthetic writer is not authorized');

    const key = attendanceKey(identity.organizationId, classId, studentId);
    const previous = attendance.get(key);
    const rowVersion = (previous?.rowVersion ?? 0) + 1;
    const occurredAt = new Date().toISOString();
    const canonical = {
      organizationId: identity.organizationId,
      classId,
      studentId,
      status,
      rowVersion,
      updatedAt: occurredAt,
    };
    attendance.set(key, canonical);

    const event = {
      eventId: randomUUID(),
      changeSequence: ++changeSequence,
      organizationId: identity.organizationId,
      classId,
      entityType: 'ATTENDANCE_RECORD',
      entityId: studentId,
      changeType: 'UPSERT',
      rowVersion,
      occurredAt,
    };
    changes.push(event);
    if (deliver) deliverEvents([event]);
    return { canonical, event };
  }

  const server = createServer(async (request, response) => {
    const url = new URL(request.url, 'http://127.0.0.1');
    const identity = authenticate(request);

    if (!identity) {
      json(response, 401, { code: 'UNAUTHENTICATED' });
      return;
    }

    const streamMatch = url.pathname.match(
      /^\/api\/v1\/realtime\/classes\/([^/]+)\/events$/,
    );
    if (request.method === 'GET' && streamMatch) {
      const classId = streamMatch[1];
      if (!authorizeClass(identity, classId)) {
        json(response, 403, { code: 'FORBIDDEN' });
        return;
      }

      response.writeHead(200, {
        'cache-control': 'no-cache, no-transform',
        connection: 'keep-alive',
        'content-type': 'text/event-stream; charset=utf-8',
        'x-accel-buffering': 'no',
      });
      response.flushHeaders();
      response.write(`event: stream.ready\ndata: ${JSON.stringify({
        headSequence: scopedHeadSequence(identity.organizationId, classId),
      })}\n\n`);

      const subscriber = {
        token: request.headers.authorization.slice(7),
        userId: identity.userId,
        organizationId: identity.organizationId,
        classId,
        response,
      };
      subscribers.add(subscriber);
      request.on('close', () => subscribers.delete(subscriber));
      return;
    }

    const changeMatch = url.pathname.match(
      /^\/api\/v1\/test\/classes\/([^/]+)\/attendance$/,
    );
    if (request.method === 'POST' && changeMatch) {
      const classId = changeMatch[1];
      if (!authorizeClass(identity, classId)) {
        json(response, 403, { code: 'FORBIDDEN' });
        return;
      }

      const body = await readJson(request);
      const { canonical } = createAttendanceChange({
        token: request.headers.authorization.slice(7),
        classId,
        studentId: body.studentId,
        status: body.status,
      });
      json(response, 200, canonical);
      return;
    }

    const changesMatch = url.pathname.match(/^\/api\/v1\/sync\/classes\/([^/]+)\/changes$/);
    if (request.method === 'GET' && changesMatch) {
      const classId = changesMatch[1];
      if (!authorizeClass(identity, classId)) {
        json(response, 403, { code: 'FORBIDDEN' });
        return;
      }
      const suppliedToken = url.searchParams.get('syncToken');
      const tokenState = suppliedToken ? syncTokens.get(suppliedToken) : undefined;
      if (suppliedToken && (
        !tokenState
        || tokenState.userId !== identity.userId
        || tokenState.organizationId !== identity.organizationId
        || tokenState.classId !== classId
      )) {
        json(response, 409, { code: 'SYNC_TOKEN_INVALID' });
        return;
      }
      const after = tokenState?.changeSequence ?? 0;
      const scopedChanges = changes.filter((change) => (
        change.organizationId === identity.organizationId
        && change.classId === classId
        && change.changeSequence > after
      ));
      const nextToken = randomUUID();
      const nextSequence = scopedChanges.at(-1)?.changeSequence ?? after;
      syncTokens.set(nextToken, {
        userId: identity.userId,
        organizationId: identity.organizationId,
        classId,
        changeSequence: nextSequence,
      });
      json(response, 200, { changes: scopedChanges, syncToken: nextToken });
      return;
    }

    const canonicalMatch = url.pathname.match(
      /^\/api\/v1\/classes\/([^/]+)\/attendance\/([^/]+)$/,
    );
    if (request.method === 'GET' && canonicalMatch) {
      const [, classId, studentId] = canonicalMatch;
      if (!authorizeClass(identity, classId)) {
        json(response, 403, { code: 'FORBIDDEN' });
        return;
      }
      const canonical = attendance.get(attendanceKey(identity.organizationId, classId, studentId));
      if (!canonical) {
        json(response, 404, { code: 'NOT_FOUND' });
        return;
      }
      json(response, 200, canonical);
      return;
    }

    json(response, 404, { code: 'NOT_FOUND' });
  });

  return {
    async start() {
      await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
      const address = server.address();
      return `http://127.0.0.1:${address.port}`;
    },
    async stop() {
      for (const subscriber of subscribers) subscriber.response.destroy();
      await new Promise((resolve, reject) => server.close((error) => (
        error ? reject(error) : resolve()
      )));
    },
    createAttendanceChange,
    deliverEventsForTest(userId, events) {
      deliverEvents(events, userId);
    },
    revokeClassAssignment(userId, classId) {
      const identity = [...identities.values()].find((candidate) => candidate.userId === userId);
      identity?.classIds.delete(classId);
    },
    revokeSession(userId) {
      const identity = [...identities.values()].find((candidate) => candidate.userId === userId);
      if (identity) identity.sessionActive = false;
    },
  };
}
