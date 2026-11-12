import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const sourceUrl = pathToFileURL(new URL('../cloudflare/worker.js', import.meta.url).pathname);
const source = await readFile(sourceUrl, 'utf8');
const moduleUrl = 'data:text/javascript;base64,' + Buffer.from(source).toString('base64');
const { MeizzoSignaling, default: Worker } = await import(moduleUrl);

const originalFetch = globalThis.fetch;
globalThis.fetch = async (url, options = {}) => {
  if (String(url).startsWith('https://rtc.live.cloudflare.com/v1/turn/keys/')) {
    if (options.headers?.Authorization !== 'Bearer test-turn-secret') {
      throw new Error('Worker did not use the server-side TURN API secret');
    }
    const body = JSON.parse(options.body || '{}');
    if (body.ttl !== 86400) throw new Error('Worker did not request the expected short-lived TURN TTL');
    return new Response(JSON.stringify({
      iceServers: [{
        urls: ['turn:turn.cloudflare.com:3478?transport=udp'],
        username: 'temporary-turn-user',
        credential: 'temporary-turn-password'
      }]
    }), { status: 201, headers: { 'content-type': 'application/json' } });
  }
  return originalFetch(url, options);
};

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
  const health = await Worker.fetch(
    new Request('https://meizzo-signaling.test/healthz'),
    { REALTIME_TURN_TOKEN_ID: '', REALTIME_TURN_TOKEN_SECRET: '' }
  );
  const healthBody = await health.json();
  if (healthBody.status !== 'ok' || healthBody.mediaTransport !== 'webrtc' || healthBody.turnConfigured !== false) {
    throw new Error('Worker health endpoint did not report the STUN-only configuration safely');
  }
  const configuredHealth = await Worker.fetch(
    new Request('https://meizzo-signaling.test/healthz'),
    { REALTIME_TURN_TOKEN_ID: 'configured-key', REALTIME_TURN_TOKEN_SECRET: 'configured-secret' }
  );
  if ((await configuredHealth.json()).turnConfigured !== true) {
    throw new Error('Worker health endpoint did not detect configured TURN credentials');
  }

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
  const shadowController = new FakeSocket({
    role: 'controller', controllerId: 'controller-1',
    token: 'controller-token', tokens: ['controller-token'], connId: 'other-controller-connection'
  });
  const sockets = [client, controller, shadowController];
  const ctx = {
    storage: {
      async get(key) { return key === 'state' ? stored : alarmAt; },
      async put(key, value) { if (key === 'state') stored = value; },
      async getAlarm() { return alarmAt; },
      async setAlarm(value) { alarmAt = value; }
    },
    getWebSockets() { return sockets; }
  };
  const signaling = new MeizzoSignaling(ctx, {
    REALTIME_TURN_TOKEN_ID: 'test-turn-key-id',
    REALTIME_TURN_TOKEN_SECRET: 'test-turn-secret'
  });

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
  const hasTurn = config => config.iceServers?.some(server =>
    server.urls?.includes('turn:turn.cloudflare.com:3478?transport=udp') &&
    server.username === 'temporary-turn-user' &&
    server.credential === 'temporary-turn-password'
  );
  if (!hasTurn(controllerIce) || !hasTurn(clientIce)) {
    throw new Error('Short-lived Cloudflare TURN credentials were not delivered to both media peers');
  }
  if (shadowController.messages.some(m => m.type === 'media.ice_servers')) {
    throw new Error('TURN credentials leaked to a different Controller socket');
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
  if (shadowController.messages.some(m => m.type === 'media.signal')) {
    throw new Error('Media offer leaked to a different Controller socket');
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

  globalThis.fetch = originalFetch;
  console.log('worker-sessions.test: OK');
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
