import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const sourceUrl = pathToFileURL(new URL('../cloudflare/worker.js', import.meta.url).pathname);
const source = await readFile(sourceUrl, 'utf8');
const moduleUrl = 'data:text/javascript;base64,' + Buffer.from(source).toString('base64');
const { MeizzoSignaling } = await import(moduleUrl);

class FakeSocket {
  constructor(attachment) {
    this.attachment = attachment;
    this.readyState = 1;
    this.messages = [];
  }
  deserializeAttachment() { return this.attachment; }
  serializeAttachment(value) { this.attachment = value; }
  send(raw) { this.messages.push(JSON.parse(raw)); }
  take(predicate, label) {
    const index = this.messages.findIndex(predicate);
    if (index < 0) throw new Error(`Missing ${label}; received: ${JSON.stringify(this.messages)}`);
    return this.messages.splice(index, 1)[0];
  }
}

async function run() {
  let stored = {
    offers: {}, attempts: {}, ipAttempts: {}, pending: {},
    devices: {
      'client-1': {
        clientId: 'client-1',
        clientToken: 'client-token',
        controllerTokens: { 'controller-token': 'controller-1' },
        controllerIds: ['controller-1'],
        metadata: { model: 'Test device' },
        status: 'online',
        lastSeen: Date.now(),
        clientHeartbeat: Date.now(),
        pendingRequests: {},
        clientConnId: 'client-connection'
      }
    },
    controllers: { 'controller-token': { controllerId: 'controller-1' } },
    sessions: {}
  };
  let alarmAt = null;
  const ctx = {
    storage: {
      async get(key) { return key === 'state' ? stored : alarmAt; },
      async put(key, value) { if (key === 'state') stored = value; },
      async getAlarm() { return alarmAt; },
      async setAlarm(value) { alarmAt = value; }
    },
    getWebSockets() { return [client, controller]; }
  };
  const client = new FakeSocket({ role: 'client', clientId: 'client-1', connId: 'client-connection' });
  const controller = new FakeSocket({
    role: 'controller', controllerId: 'controller-1',
    token: 'controller-token', tokens: ['controller-token'], connId: 'controller-connection'
  });
  const signaling = new MeizzoSignaling(ctx, {});

  const start = () => signaling.webSocketMessage(controller, JSON.stringify({
    type: 'controller.start_session',
    clientId: 'client-1',
    sessionType: 'interaction'
  }));
  const approve = (sessionId) => signaling.webSocketMessage(client, JSON.stringify({
    type: 'client.session_decision', sessionId, approved: true
  }));
  const end = (sessionId) => signaling.webSocketMessage(controller, JSON.stringify({
    type: 'controller.end_session', sessionId, reason: 'controller_ended'
  }));

  await start();
  const firstPending = controller.take(m => m.type === 'session.status' && m.state === 'pending', 'first pending session');
  client.take(m => m.type === 'client.session_status' && m.sessionId === firstPending.sessionId, 'Client first consent request');

  await start();
  controller.take(m => m.type === 'error' && m.code === 'session_already_active', 'duplicate session rejection');

  await approve(firstPending.sessionId);
  controller.take(m => m.type === 'session.status' && m.sessionId === firstPending.sessionId && m.state === 'approved', 'first session approval');

  await end(firstPending.sessionId);
  controller.take(m => m.type === 'session.status' && m.sessionId === firstPending.sessionId && m.state === 'ended', 'first session termination');
  client.take(m => m.type === 'client.session_status' && m.sessionId === firstPending.sessionId && m.state === 'ended', 'Client first session termination');
  if (Object.keys(stored.sessions).length !== 0) throw new Error('Ended session was not removed from durable state');

  await start();
  const secondPending = controller.take(m => m.type === 'session.status' && m.state === 'pending', 'second pending session after end');
  client.take(m => m.type === 'client.session_status' && m.sessionId === secondPending.sessionId && m.state === 'pending', 'Client second consent request');
  if (secondPending.sessionId === firstPending.sessionId) throw new Error('A fresh request reused the previous session ID');

  await approve(secondPending.sessionId);
  controller.take(m => m.type === 'session.status' && m.sessionId === secondPending.sessionId && m.state === 'approved', 'second session approval');
  await signaling.webSocketClose(controller);
  client.take(m => m.type === 'client.session_status' && m.sessionId === secondPending.sessionId &&
    m.state === 'ended' && m.terminationReason === 'controller_disconnected', 'Client cleanup after Controller disconnect');
  if (Object.keys(stored.sessions).length !== 0) throw new Error('Controller disconnect left an orphaned session');

  console.log('worker-sessions.test: OK');
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
