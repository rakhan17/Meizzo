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
  const client = new FakeSocket({ role: 'client', clientId: 'client-1', connId: 'client-connection' });
  const controller = new FakeSocket({
    role: 'controller', controllerId: 'controller-1',
    token: 'controller-token', tokens: ['controller-token'], connId: 'controller-connection'
  });
  const sockets = [client, controller];
  const ctx = {
    storage: {
      async get(key) { return key === 'state' ? stored : alarmAt; },
      async put(key, value) { if (key === 'state') stored = value; },
      async getAlarm() { return alarmAt; },
      async setAlarm(value) { alarmAt = value; }
    },
    getWebSockets() { return sockets; }
  };
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

  const screenStart = await signaling.webSocketMessage(controller, JSON.stringify({
    type: 'controller.start_session', clientId: 'client-1', sessionType: 'screen'
  }));
  const screenPending = controller.take(m => m.type === 'session.status' && m.sessionType === 'screen' && m.state === 'pending', 'screen session pending');
  client.take(m => m.type === 'client.session_status' && m.sessionId === screenPending.sessionId, 'screen consent request');
  await signaling.webSocketMessage(client, JSON.stringify({
    type: 'client.session_decision', sessionId: screenPending.sessionId, approved: true
  }));
  const controllerIce = controller.take(m => m.type === 'media.ice_servers' && m.sessionId === screenPending.sessionId, 'Controller ICE server configuration');
  const clientIce = client.take(m => m.type === 'media.ice_servers' && m.sessionId === screenPending.sessionId, 'Client ICE server configuration');
  if (!controllerIce.iceServers?.some(server => server.urls?.includes('stun:stun.cloudflare.com:3478')) ||
      !clientIce.iceServers?.some(server => server.urls?.includes('stun:stun.cloudflare.com:3478'))) {
    throw new Error('Cloudflare STUN fallback was not delivered to both media peers');
  }
  controller.take(m => m.type === 'session.status' && m.sessionId === screenPending.sessionId && m.state === 'approved', 'screen session approved');

  await signaling.webSocketMessage(controller, JSON.stringify({
    type: 'media.signal', sessionId: screenPending.sessionId,
    signal: { type: 'offer', sdp: 'v=0\\r\\no=worker-test' }
  }));
  const workerOffer = client.take(m => m.type === 'media.signal' && m.sessionId === screenPending.sessionId, 'WebRTC offer forwarded to Client');
  if (workerOffer.fromRole !== 'controller' || workerOffer.signal.type !== 'offer') {
    throw new Error('Cloudflare Worker did not relay the authorized media offer');
  }
  await signaling.webSocketMessage(client, JSON.stringify({
    type: 'media.signal', sessionId: screenPending.sessionId,
    signal: { type: 'answer', sdp: 'v=0\\r\\na=worker-answer' }
  }));
  const workerAnswer = controller.take(m => m.type === 'media.signal' && m.sessionId === screenPending.sessionId, 'WebRTC answer forwarded to Controller');
  if (workerAnswer.fromRole !== 'client' || workerAnswer.signal.type !== 'answer') {
    throw new Error('Cloudflare Worker did not relay the authorized media answer');
  }
  await signaling.webSocketMessage(client, JSON.stringify({
    type: 'client.end_session',
    sessionId: screenPending.sessionId,
    reason: 'screen_capture_consent_denied'
  }));
  controller.take(m => m.type === 'session.status' && m.sessionId === screenPending.sessionId &&
    m.state === 'ended' && m.terminationReason === 'screen_capture_consent_denied', 'Client ends screen session');
  client.take(m => m.type === 'client.session_status' && m.sessionId === screenPending.sessionId &&
    m.state === 'ended', 'Client screen session ended');
  if (Object.keys(stored.sessions).length !== 0) throw new Error('Client-ended media session remained in durable state');

  await start();
  const beforeReconnect = controller.take(m => m.type === 'session.status' && m.state === 'pending', 'session pending before Client reconnect');
  client.take(m => m.type === 'client.session_status' && m.sessionId === beforeReconnect.sessionId, 'Client consent before reconnect');
  await approve(beforeReconnect.sessionId);
  controller.take(m => m.type === 'session.status' && m.sessionId === beforeReconnect.sessionId && m.state === 'approved', 'session approved before Client reconnect');

  const replacementClient = new FakeSocket({ role: 'guest', connId: 'replacement-client-connection' });
  sockets.push(replacementClient);
  await signaling.webSocketMessage(replacementClient, JSON.stringify({
    type: 'auth.connect', role: 'client', clientId: 'client-1', authToken: 'client-token'
  }));
  controller.take(m => m.type === 'session.status' && m.sessionId === beforeReconnect.sessionId &&
    m.state === 'failed' && m.terminationReason === 'client_reconnected', 'session cleanup on Client reconnect');
  if (Object.keys(stored.sessions).length !== 0) throw new Error('Client reconnect left an orphaned session');

  console.log('worker-sessions.test: OK');
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
