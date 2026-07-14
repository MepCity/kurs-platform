function authorization(token) {
  return { authorization: `Bearer ${token}` };
}

export class ExperimentClient {
  constructor({ baseUrl, classId, token }) {
    this.baseUrl = baseUrl;
    this.classId = classId;
    this.token = token;
    this.lastSequence = 0;
    this.syncToken = undefined;
    this.events = [];
    this.waiters = [];
    this.seenEventIds = new Set();
    this.duplicateEventCount = 0;
    this.reconciliationInFlight = undefined;
    this.lastReconciliationPromise = undefined;
    this.reconciliationRunCount = 0;
  }

  get pendingWaiterCount() {
    return this.waiters.length;
  }

  async connect() {
    this.abortController = new AbortController();
    const response = await fetch(
      `${this.baseUrl}/api/v1/realtime/classes/${this.classId}/events`,
      {
        headers: authorization(this.token),
        signal: this.abortController.signal,
      },
    );
    if (!response.ok) throw new Error(`Stream rejected with ${response.status}`);
    this.reader = response.body.getReader();
    this.readLoop = this.#readStream();
    return this.waitFor('stream.ready');
  }

  disconnect() {
    this.abortController?.abort();
  }

  async waitFor(type, timeoutMs = 2_000) {
    const existingIndex = this.events.findIndex((event) => event.type === type);
    if (existingIndex >= 0) return this.events.splice(existingIndex, 1)[0];

    return new Promise((resolve, reject) => {
      const waiter = {
        type,
        resolve: (event) => {
          clearTimeout(waiter.timeout);
          resolve(event);
        },
      };
      waiter.timeout = setTimeout(() => {
        const waiterIndex = this.waiters.indexOf(waiter);
        if (waiterIndex >= 0) this.waiters.splice(waiterIndex, 1);
        reject(new Error(`Timed out waiting for ${type}`));
      }, timeoutMs);
      this.waiters.push(waiter);
    });
  }

  async reconcile(studentId) {
    const changeFeed = await this.#pullChanges();
    return { changeFeed, canonical: await this.readCanonical(studentId) };
  }

  async waitForReconciliation() {
    if (!this.lastReconciliationPromise) {
      throw new Error('No reconciliation has been requested');
    }
    return this.lastReconciliationPromise;
  }

  async #pullChanges() {
    const tokenQuery = this.syncToken
      ? `?syncToken=${encodeURIComponent(this.syncToken)}`
      : '';
    const changesResponse = await fetch(
      `${this.baseUrl}/api/v1/sync/classes/${this.classId}/changes${tokenQuery}`,
      { headers: authorization(this.token) },
    );
    if (!changesResponse.ok) throw new Error(`Changes rejected with ${changesResponse.status}`);
    const changeFeed = await changesResponse.json();
    this.syncToken = changeFeed.syncToken;
    for (const change of changeFeed.changes) {
      this.seenEventIds.add(change.eventId);
      this.lastSequence = Math.max(this.lastSequence, change.changeSequence);
    }
    return changeFeed;
  }

  async #reconcileChangedAttendance() {
    const changeFeed = await this.#pullChanges();
    const studentIds = [...new Set(
      changeFeed.changes
        .filter((change) => change.entityType === 'ATTENDANCE_RECORD')
        .map((change) => change.entityId),
    )];
    const canonicalEntries = await Promise.all(studentIds.map(async (studentId) => (
      [studentId, await this.readCanonical(studentId)]
    )));
    return { changeFeed, canonicals: Object.fromEntries(canonicalEntries) };
  }

  async readCanonical(studentId) {
    const canonicalResponse = await fetch(
      `${this.baseUrl}/api/v1/classes/${this.classId}/attendance/${studentId}`,
      { headers: authorization(this.token) },
    );
    if (!canonicalResponse.ok) {
      throw new Error(`Canonical read rejected with ${canonicalResponse.status}`);
    }
    return canonicalResponse.json();
  }

  async #readStream() {
    const decoder = new TextDecoder();
    let buffer = '';
    try {
      while (true) {
        const { done, value } = await this.reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        let boundary;
        while ((boundary = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          this.#acceptBlock(block);
        }
      }
    } catch (error) {
      if (error.name !== 'AbortError') throw error;
    } finally {
      this.#emit({ type: 'stream.closed', receivedAt: performance.now() });
    }
  }

  #acceptBlock(block) {
    let type = 'message';
    const dataLines = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) type = line.slice(6).trim();
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
    }
    const data = dataLines.length ? JSON.parse(dataLines.join('\n')) : undefined;
    const receivedAt = performance.now();

    if (type !== 'entity.changed') {
      this.#emit({ type, data, receivedAt });
      return;
    }
    if (this.seenEventIds.has(data.eventId)) {
      this.duplicateEventCount += 1;
      return;
    }

    this.seenEventIds.add(data.eventId);
    const expectedSequence = this.lastSequence + 1;
    let reason;
    if (data.changeSequence > expectedSequence) reason = 'SEQUENCE_GAP';
    else if (data.changeSequence <= this.lastSequence) reason = 'OUT_OF_ORDER';
    this.lastSequence = Math.max(this.lastSequence, data.changeSequence);

    if (reason) {
      this.#emit({
        type: 'reconciliation.required',
        data: { reason, event: data },
        receivedAt,
      });
      if (!this.reconciliationInFlight) {
        this.reconciliationRunCount += 1;
        const reconciliation = this.#reconcileChangedAttendance();
        this.reconciliationInFlight = reconciliation;
        this.lastReconciliationPromise = reconciliation;
        const clearInFlight = () => {
          if (this.reconciliationInFlight === reconciliation) {
            this.reconciliationInFlight = undefined;
          }
        };
        reconciliation.then(clearInFlight, clearInFlight);
      }
      return;
    }
    this.#emit({ type, data, receivedAt });
  }

  #emit(event) {
    const waiterIndex = this.waiters.findIndex((waiter) => waiter.type === event.type);
    if (waiterIndex >= 0) this.waiters.splice(waiterIndex, 1)[0].resolve(event);
    else this.events.push(event);
  }
}

export async function writeAttendance({ baseUrl, classId, token, studentId, status }) {
  const sentAt = performance.now();
  const response = await fetch(`${baseUrl}/api/v1/test/classes/${classId}/attendance`, {
    method: 'POST',
    headers: { ...authorization(token), 'content-type': 'application/json' },
    body: JSON.stringify({ studentId, status }),
  });
  return { response, sentAt, body: await response.json() };
}
