'use strict';

const { spawn } = require('child_process');
const WebSocket = require('ws');

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function openSocket(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    socket.once('open', () => resolve(socket));
    socket.once('error', reject);
  });
}

function nextMessage(socket, predicate, timeoutMs = 2000, label = 'message') {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timed out waiting for ${label}`)), timeoutMs);
    const onMessage = (raw) => {
      const message = JSON.parse(raw.toString());
      if (predicate(message)) {
        clearTimeout(timer);
        socket.off('message', onMessage);
        resolve(message);
      }
    };
    socket.on('message', onMessage);
  });
}

async function pair(client, controller, code, clientId) {
  const registered = nextMessage(client, (message) => message.type === 'client.pairing_offer_registered');
  client.send(JSON.stringify({
    type: 'client.register_pairing_offer',
    clientId,
    pairingCode: code,
    expiresAtMs: Date.now() + 60_000,
    pairingAcceptanceEnabled: true,
    metadata: { model: clientId }
  }));
  await registered;
  const requestPromise = nextMessage(client, (message) => message.type === 'client.pairing_request');
  controller.send(JSON.stringify({ type: 'controller.request_pairing', controllerId: 'controller-multi', pairingCode: code }));
  const request = await requestPromise;
  const clientTokenPromise = nextMessage(client, (message) => message.type === 'client.pairing_completed');
  const resultPromise = nextMessage(controller, (message) => message.type === 'controller.pairing_result' && message.status === 'paired');
  client.send(JSON.stringify({ type: 'client.pairing_decision', requestId: request.requestId, approved: true }));
  const result = await resultPromise;
  const clientToken = await clientTokenPromise;
  const authenticated = nextMessage(controller, (message) => message.type === 'auth.connected');
  controller.send(JSON.stringify({
    type: 'auth.connect',
    role: 'controller',
    clientId,
    authToken: result.authToken
  }));
  await authenticated;
  return { ...result, clientToken: clientToken.authToken };
}

async function run() {
  const server = spawn(process.execPath, ['src/server.js'], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: '8101' },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  const sockets = [];
  try {
    await wait(300);
    const controller = await openSocket('ws://127.0.0.1:8101');
    const clientA = await openSocket('ws://127.0.0.1:8101');
    const clientB = await openSocket('ws://127.0.0.1:8101');
    sockets.push(controller, clientA, clientB);

    const pairedA = await pair(clientA, controller, '111111', 'client-a');
    const pairedB = await pair(clientB, controller, '222222', 'client-b');

    const listPromise = nextMessage(controller, (message) => message.type === 'controller.device_list');
    controller.send(JSON.stringify({ type: 'controller.list_devices' }));
    const list = await listPromise;
    if (list.devices.length !== 2 || list.devices.map((item) => item.clientId).sort().join(',') !== 'client-a,client-b') {
      throw new Error(`Expected two independently paired devices: ${JSON.stringify(list.devices)}`);
    }
    const renamedPromise = nextMessage(controller, (message) => message.type === 'device.renamed');
    controller.send(JSON.stringify({
      type: 'controller.rename_device',
      clientId: 'client-a',
      alias: 'Living room'
    }));
    const renamed = await renamedPromise;
    if (renamed.alias !== 'Living room') throw new Error('Device rename was not acknowledged');

    const commandPromise = nextMessage(clientA, (message) => message.type === 'client.snapshot_request');
    controller.send(JSON.stringify({
      type: 'controller.request_snapshot',
      clientId: 'client-a'
    }));
    const command = await commandPromise;
    let clientBReceivedCommand = false;
    clientB.once('message', () => { clientBReceivedCommand = true; });
    const resultPromise = nextMessage(controller, (message) => message.type === 'device.result');
    clientA.send(JSON.stringify({
      type: 'client.result',
      requestId: command.requestId,
      result: { batteryPercent: 80 }
    }));
    const result = await resultPromise;
    if (result.clientId !== 'client-a' || result.result.batteryPercent !== 80) {
      throw new Error('Snapshot result was not routed to Client A');
    }
    await wait(100);
    if (clientBReceivedCommand) throw new Error('Client B received Client A command');

    const offlinePromise = nextMessage(controller, (message) => message.type === 'device.status' && message.clientId === 'client-a');
    clientA.close();
    const offline = await offlinePromise;
    if (offline.status !== 'offline') throw new Error('Client A did not transition offline');

    const clientAReconnect = await openSocket('ws://127.0.0.1:8101');
    sockets.push(clientAReconnect);
    const reconnectedPromise = nextMessage(clientAReconnect, (message) => message.type === 'auth.connected');
    const onlinePromise = nextMessage(controller, (message) => message.type === 'device.status' && message.clientId === 'client-a');
    clientAReconnect.send(JSON.stringify({
      type: 'auth.connect',
      role: 'client',
      clientId: 'client-a',
      authToken: pairedA.clientToken
    }));
    await reconnectedPromise;
    clientAReconnect.send(JSON.stringify({ type: 'heartbeat' }));
    const online = await onlinePromise;
    if (online.status !== 'online') throw new Error('Client A did not recover online');
    const telemetryPromise = nextMessage(controller, (message) =>
      message.type === 'device.status' && message.clientId === 'client-a' &&
      message.metadata?.telemetry?.batteryPercent === 81
    );
    clientAReconnect.send(JSON.stringify({
      type: 'heartbeat',
      telemetry: { batteryPercent: 81, network: 'wifi', timestamp: Date.now() }
    }));
    await telemetryPromise;

    const sessionRequest = nextMessage(clientAReconnect, (message) =>
      message.type === 'client.session_status' && message.state === 'pending', 2000, 'client session pending');
    const sessionPending = nextMessage(controller, (message) =>
      message.type === 'session.status' && message.state === 'pending', 2000, 'controller session pending');
    controller.send(JSON.stringify({
      type: 'controller.start_session',
      clientId: 'client-a',
      sessionType: 'monitoring'
    }));
    const pending = await sessionPending;
    await sessionRequest;
    const sessionApproved = nextMessage(controller, (message) =>
      message.type === 'session.status' && message.sessionId === pending.sessionId &&
      message.state === 'approved', 2000, 'session approved');
    clientAReconnect.send(JSON.stringify({
      type: 'client.session_decision',
      sessionId: pending.sessionId,
      approved: true
    }));
    await sessionApproved;
    const sessionEnded = nextMessage(controller, (message) =>
      message.type === 'session.status' && message.sessionId === pending.sessionId &&
      message.state === 'ended', 2000, 'session ended');
    controller.send(JSON.stringify({
      type: 'controller.end_session',
      sessionId: pending.sessionId,
      reason: 'test_complete'
    }));
    const ended = await sessionEnded;
    if (ended.terminationReason !== 'test_complete') {
      throw new Error('Session termination reason was not preserved');
    }

    const disconnectPendingClient = nextMessage(clientAReconnect, (message) =>
      message.type === 'client.session_status' && message.state === 'pending', 2000, 'disconnect session pending');
    const disconnectPendingController = nextMessage(controller, (message) =>
      message.type === 'session.status' && message.state === 'pending', 2000, 'disconnect session pending at controller');
    controller.send(JSON.stringify({
      type: 'controller.start_session',
      clientId: 'client-a',
      sessionType: 'monitoring'
    }));
    const disconnectSession = await disconnectPendingController;
    await disconnectPendingClient;
    const disconnectApproved = nextMessage(controller, (message) =>
      message.type === 'session.status' && message.sessionId === disconnectSession.sessionId &&
      message.state === 'approved', 2000, 'disconnect session approved');
    clientAReconnect.send(JSON.stringify({
      type: 'client.session_decision',
      sessionId: disconnectSession.sessionId,
      approved: true
    }));
    await disconnectApproved;
    const failedSession = nextMessage(controller, (message) =>
      message.type === 'session.status' && message.sessionId === disconnectSession.sessionId &&
      message.state === 'failed', 2000, 'session failed after Client disconnect');
    const offlinePromise = nextMessage(controller, (message) =>
      message.type === 'device.status' && message.clientId === 'client-a' && message.status === 'offline');
    clientAReconnect.close();
    const failed = await failedSession;
    if (failed.terminationReason !== 'client_disconnected') {
      throw new Error('Client disconnect did not terminate its active session');
    }
    await offlinePromise;

    controller.close();
    const replacementController = await openSocket('ws://127.0.0.1:8101');
    sockets.push(replacementController);
    const reconnectA = nextMessage(replacementController, (message) => message.type === 'auth.connected' && message.clientId === 'client-a');
    const reconnectB = nextMessage(replacementController, (message) => message.type === 'auth.connected' && message.clientId === 'client-b');
    replacementController.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'client-a',
      authToken: pairedA.authToken
    }));
    replacementController.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'client-b',
      authToken: pairedB.authToken
    }));
    await Promise.all([reconnectA, reconnectB]);

    console.log('multi-device.test: OK');
  } finally {
    sockets.forEach((socket) => socket.close());
    server.kill('SIGTERM');
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
