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

function nextMessage(socket, predicate, label = 'message', timeoutMs = 2000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      socket.off('message', onMessage);
      reject(new Error(`Timed out waiting for ${label}`));
    }, timeoutMs);
    const onMessage = (raw) => {
      const message = JSON.parse(raw.toString());
      if (!predicate(message)) return;
      clearTimeout(timer);
      socket.off('message', onMessage);
      resolve(message);
    };
    socket.on('message', onMessage);
  });
}

async function run() {
  const server = spawn(process.execPath, ['src/server.js'], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: '8102' },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  const sockets = [];
  try {
    await wait(300);
    const client = await openSocket('ws://127.0.0.1:8102');
    const controller = await openSocket('ws://127.0.0.1:8102');
    sockets.push(client, controller);

    const firstOffer = nextMessage(client, (m) => m.type === 'client.pairing_offer_registered', 'initial pairing offer');
    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'revocation-client',
      pairingCode: '314159',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));
    await firstOffer;

    const firstRequest = nextMessage(client, (m) => m.type === 'client.pairing_request', 'initial pairing request');
    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'revocation-controller',
      pairingCode: '314159'
    }));
    const request = await firstRequest;
    const firstPaired = nextMessage(controller, (m) => m.type === 'controller.pairing_result' && m.status === 'paired', 'initial pairing result');
    const firstClientCompleted = nextMessage(client, (m) => m.type === 'client.pairing_completed', 'initial Client credentials');
    client.send(JSON.stringify({
      type: 'client.pairing_decision',
      requestId: request.requestId,
      approved: true
    }));
    const first = await firstPaired;
    const firstClient = await firstClientCompleted;
    if (!firstClient.authToken) throw new Error('Initial Client token was not issued');

    const firstAuthenticated = nextMessage(controller, (m) => m.type === 'auth.connected', 'initial Controller authentication');
    controller.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'revocation-client',
      authToken: first.authToken
    }));
    await firstAuthenticated;

    // Re-pairing the same Controller ID must rotate its token without replacing
    // the Client credential or leaving the previous token usable.
    const secondOffer = nextMessage(client, (m) => m.type === 'client.pairing_offer_registered', 'rotated pairing offer');
    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'revocation-client',
      pairingCode: '271828',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));
    await secondOffer;
    const secondRequest = nextMessage(client, (m) => m.type === 'client.pairing_request', 'rotated pairing request');
    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'revocation-controller',
      pairingCode: '271828'
    }));
    const request2 = await secondRequest;
    const secondPaired = nextMessage(controller, (m) => m.type === 'controller.pairing_result' && m.status === 'paired', 'rotated pairing result');
    const secondClientCompleted = nextMessage(client, (m) => m.type === 'client.pairing_completed', 'preserved Client credentials');
    client.send(JSON.stringify({
      type: 'client.pairing_decision',
      requestId: request2.requestId,
      approved: true
    }));
    const second = await secondPaired;
    const secondClient = await secondClientCompleted;
    if (first.authToken === second.authToken) throw new Error('Controller credential was not rotated');
    if (firstClient.authToken !== secondClient.authToken) throw new Error('Re-pairing unexpectedly rotated the Client credential');

    const staleSocket = await openSocket('ws://127.0.0.1:8102');
    sockets.push(staleSocket);
    const staleRejected = nextMessage(staleSocket, (m) => m.type === 'error' && m.code === 'unauthorized', 'rejection of rotated token');
    staleSocket.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'revocation-client',
      authToken: first.authToken
    }));
    await staleRejected;

    const replacement = await openSocket('ws://127.0.0.1:8102');
    sockets.push(replacement);
    const replacementAuthenticated = nextMessage(replacement, (m) => m.type === 'auth.connected', 'new Controller token authentication');
    replacement.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'revocation-client',
      authToken: second.authToken
    }));
    await replacementAuthenticated;

    // A second Controller can be paired to the same Client without replacing
    // the first Controller's grant or the Client's own credential.
    const otherController = await openSocket('ws://127.0.0.1:8102');
    sockets.push(otherController);
    const thirdOffer = nextMessage(client, (m) => m.type === 'client.pairing_offer_registered', 'second Controller pairing offer');
    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'revocation-client',
      pairingCode: '161803',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));
    await thirdOffer;
    const thirdRequest = nextMessage(client, (m) => m.type === 'client.pairing_request', 'second Controller pairing request');
    otherController.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'second-controller',
      pairingCode: '161803'
    }));
    const request3 = await thirdRequest;
    const thirdPaired = nextMessage(otherController, (m) => m.type === 'controller.pairing_result' && m.status === 'paired', 'second Controller pairing result');
    const thirdClientCompleted = nextMessage(client, (m) => m.type === 'client.pairing_completed', 'Client credential remains stable');
    client.send(JSON.stringify({
      type: 'client.pairing_decision',
      requestId: request3.requestId,
      approved: true
    }));
    const third = await thirdPaired;
    const thirdClient = await thirdClientCompleted;
    if (thirdClient.authToken !== firstClient.authToken) throw new Error('Adding another Controller changed the Client credential');
    const otherAuthenticated = nextMessage(otherController, (m) => m.type === 'auth.connected', 'second Controller authentication');
    otherController.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'revocation-client',
      authToken: third.authToken
    }));
    await otherAuthenticated;

    const controllerRevoked = nextMessage(replacement, (m) => m.type === 'device.revoked', 'first Controller credential revocation');
    replacement.send(JSON.stringify({
      type: 'controller.revoke_device',
      clientId: 'revocation-client'
    }));
    await controllerRevoked;
    const clientStillPaired = nextMessage(client, (m) => m.type === 'heartbeat.ack', 'Client remains paired to second Controller');
    client.send(JSON.stringify({ type: 'heartbeat' }));
    await clientStillPaired;

    const secondControllerRevoked = nextMessage(otherController, (m) => m.type === 'device.revoked', 'second Controller revocation');
    const clientRevoked = nextMessage(client, (m) => m.type === 'device.revoked', 'Client revocation notice after last Controller');
    otherController.send(JSON.stringify({
      type: 'controller.revoke_device',
      clientId: 'revocation-client'
    }));
    await Promise.all([secondControllerRevoked, clientRevoked]);

    const revokedRejected = nextMessage(staleSocket, (m) => m.type === 'error' && m.code === 'unauthorized', 'rejection after device revocation');
    staleSocket.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'revocation-client',
      authToken: second.authToken
    }));
    await revokedRejected;

    console.log('revocation.test: OK');
  } finally {
    sockets.forEach((socket) => socket.close());
    server.kill('SIGTERM');
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
