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

function nextMessage(socket, predicate, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`Timed out waiting for ${label}`)), 2500);
    const listener = (raw) => {
      const message = JSON.parse(raw.toString());
      if (!predicate(message)) return;
      clearTimeout(timer);
      socket.off('message', listener);
      resolve(message);
    };
    socket.on('message', listener);
  });
}

async function run() {
  const server = spawn(process.execPath, ['src/server.js'], {
    cwd: process.cwd(),
    env: { ...process.env, PORT: '8103' },
    stdio: ['ignore', 'pipe', 'pipe']
  });
  const sockets = [];
  try {
    await wait(300);
    const client = await openSocket('ws://127.0.0.1:8103');
    const controller = await openSocket('ws://127.0.0.1:8103');
    sockets.push(client, controller);

    const offer = nextMessage(client, m => m.type === 'client.pairing_offer_registered', 'pairing offer');
    client.send(JSON.stringify({
      type: 'client.register_pairing_offer',
      clientId: 'media-client',
      pairingCode: '808080',
      expiresAtMs: Date.now() + 60_000,
      pairingAcceptanceEnabled: true
    }));
    await offer;

    const request = nextMessage(client, m => m.type === 'client.pairing_request', 'pairing request');
    controller.send(JSON.stringify({
      type: 'controller.request_pairing',
      controllerId: 'media-controller',
      pairingCode: '808080'
    }));
    const pendingPairing = await request;
    const paired = nextMessage(controller, m => m.type === 'controller.pairing_result' && m.status === 'paired', 'paired Controller');
    client.send(JSON.stringify({
      type: 'client.pairing_decision',
      requestId: pendingPairing.requestId,
      approved: true
    }));
    const credentials = await paired;
    const authenticated = nextMessage(controller, m => m.type === 'auth.connected', 'Controller authentication');
    controller.send(JSON.stringify({
      type: 'auth.connect',
      role: 'controller',
      clientId: 'media-client',
      authToken: credentials.authToken
    }));
    await authenticated;

    const clientPending = nextMessage(client, m => m.type === 'client.session_status' && m.sessionType === 'screen' && m.state === 'pending', 'Client screen consent');
    const controllerPending = nextMessage(controller, m => m.type === 'session.status' && m.sessionType === 'screen' && m.state === 'pending', 'Controller screen session');
    controller.send(JSON.stringify({
      type: 'controller.start_session',
      clientId: 'media-client',
      sessionType: 'screen'
    }));
    const session = await controllerPending;
    await clientPending;

    const approved = nextMessage(controller, m => m.type === 'session.status' && m.sessionId === session.sessionId && m.state === 'approved', 'screen approval');
    client.send(JSON.stringify({
      type: 'client.session_decision',
      sessionId: session.sessionId,
      approved: true
    }));
    await approved;

    const offerSignal = { type: 'offer', sdp: 'v=0\\r\\no=meizzo-test' };
    const deliveredOffer = nextMessage(client, m => m.type === 'media.signal' && m.sessionId === session.sessionId, 'WebRTC offer at Client');
    controller.send(JSON.stringify({
      type: 'media.signal',
      sessionId: session.sessionId,
      signal: offerSignal
    }));
    const receivedOffer = await deliveredOffer;
    if (receivedOffer.fromRole !== 'controller' || receivedOffer.signal.sdp !== offerSignal.sdp) {
      throw new Error('Controller media offer was not relayed intact to the Client');
    }

    const answerSignal = { type: 'answer', sdp: 'v=0\\r\\na=meizzo-answer' };
    const deliveredAnswer = nextMessage(controller, m => m.type === 'media.signal' && m.sessionId === session.sessionId, 'WebRTC answer at Controller');
    client.send(JSON.stringify({
      type: 'media.signal',
      sessionId: session.sessionId,
      signal: answerSignal
    }));
    const receivedAnswer = await deliveredAnswer;
    if (receivedAnswer.fromRole !== 'client' || receivedAnswer.signal.sdp !== answerSignal.sdp) {
      throw new Error('Client media answer was not relayed intact to the Controller');
    }

    const ended = nextMessage(controller, m => m.type === 'session.status' &&
      m.sessionId === session.sessionId && m.state === 'ended' &&
      m.terminationReason === 'screen_capture_consent_denied', 'Client-initiated screen session end');
    client.send(JSON.stringify({
      type: 'client.end_session',
      sessionId: session.sessionId,
      reason: 'screen_capture_consent_denied'
    }));
    await ended;

    const rejected = nextMessage(controller, m => m.type === 'error' && m.code === 'session_required', 'signal rejected after session end');
    controller.send(JSON.stringify({
      type: 'media.signal',
      sessionId: session.sessionId,
      signal: { type: 'candidate', candidate: 'stale' }
    }));
    await rejected;

    console.log('media-signaling.test: OK');
  } finally {
    sockets.forEach(socket => socket.close());
    server.kill('SIGTERM');
  }
}

run().catch(error => {
  console.error(error);
  process.exit(1);
});
