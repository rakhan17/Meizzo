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
    let deniedCount = 0;
    let authenticated = false;
    let authProtocol = false;

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
      if (msg.type === 'auth.connected') {
        authenticated = true;
        authProtocol = msg.protocolVersion === 2 &&
          msg.capabilities.includes('telemetry');
      }
      if (msg.type === 'controller.pairing_result' && msg.status === 'failed') {
        deniedCount += 1;
      }
    });

    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'client-test-1',
      pairingCode: '654321',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));
    // Rotating a code must immediately invalidate the previous offer.
    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'client-test-1',
      pairingCode: '123456',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));

    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'controller-test-1',
      pairingCode: '654321'
    }));
    await wait(300);
    if (deniedCount < 1) {
      throw new Error('Expected previous pairing code to be invalidated after rotation');
    }

    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'controller-test-1',
      pairingCode: '123456'
    }));

    await wait(800);

    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'controller-test-1',
      pairingCode: '654321'
    }));

    await wait(500);

    if (!paired) {
      throw new Error('Expected pairing success flow to complete');
    }

    if (deniedCount < 2) {
      throw new Error('Expected one-time code reuse attempt to fail');
    }

    if (!authenticated) {
      throw new Error('Expected issued controller token to authenticate');
    }

    if (!authProtocol) {
      throw new Error('Expected protocol version and capabilities negotiation');
    }

    const rateLimited = await new Promise((resolve, reject) => {
      let received = 0;
      let sawRateLimit = false;
      const timeout = setTimeout(() => reject(new Error('Pairing IP rate limit was not enforced')), 2000);
      const listener = (raw) => {
        const message = JSON.parse(raw.toString());
        if (message.type !== 'controller.pairing_result') return;
        received += 1;
        if (message.status === 'rate_limited') sawRateLimit = true;
        if (received === 28) {
          clearTimeout(timeout);
          controller.off('message', listener);
          resolve(sawRateLimit);
        }
      };
      controller.on('message', listener);
      for (let index = 0; index < 28; index += 1) {
        controller.send(JSON.stringify({
          type: 'controller.request_pairing',
          controllerId: `rate-test-${index}`,
          pairingCode: '000000'
        }));
      }
    });
    if (!rateLimited) throw new Error('Pairing attempts were not limited per network address');

    client.close();
    controller.close();
    console.log('pairing-smoke.test: OK');
  } finally {
    server.kill('SIGTERM');
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
