'use strict';

const { spawn } = require('child_process');
const WebSocket = require('ws');

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function openSocket(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    socket.once('open', () => resolve(socket));
    socket.once('error', reject);
  });
}

async function run() {
  const server = spawn(process.execPath, ['src/server.js'], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: '8100' },
    stdio: ['ignore', 'pipe', 'pipe']
  });

  try {
    await wait(300);
    const client = await openSocket('ws://127.0.0.1:8100');
    const controller = await openSocket('ws://127.0.0.1:8100');

    let paired = false;
    let denied = false;
    let authenticated = false;

    client.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      if (msg.type === 'client.pairing_request') {
        client.send(JSON.stringify({
          type: 'client.pairing_decision',
          requestId: msg.requestId,
          approved: true
        }));
      }
    });

    controller.on('message', (raw) => {
      const msg = JSON.parse(raw.toString());
      if (msg.type === 'controller.pairing_result' && msg.status === 'paired') {
        paired = true;
        controller.send(JSON.stringify({
          type: 'auth.connect',
          role: 'controller',
          clientId: msg.clientId,
          authToken: msg.authToken
        }));
      }
      if (msg.type === 'auth.connected') authenticated = true;
      if (msg.type === 'controller.pairing_result' && msg.status === 'failed') {
        denied = true;
      }
    });

    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'client-test-1',
      pairingCode: '654321',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));

    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'controller-test-1',
      pairingCode: '654321'
    }));

    await wait(800);

    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'controller-test-1',
      pairingCode: '654321'
    }));

    await wait(500);

    client.close();
    controller.close();

    if (!paired) {
      throw new Error('Expected pairing success flow to complete');
    }

    if (!denied) {
      throw new Error('Expected one-time code reuse attempt to fail');
    }

    if (!authenticated) {
      throw new Error('Expected issued controller token to authenticate');
    }

    console.log('pairing-smoke.test: OK');
  } finally {
    server.kill('SIGTERM');
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
