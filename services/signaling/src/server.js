'use strict';

const crypto = require('crypto');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT || 8080);
const PAIRING_MAX_TTL_MS = 10 * 60 * 1000;
const PAIRING_WINDOW_MS = 60 * 1000;
const PAIRING_MAX_ATTEMPTS_PER_WINDOW = 10;

/** @type {Map<string, { clientId: string, expiresAtMs: number, pairingAcceptanceEnabled: boolean, connection: import('ws'), metadata?: object }>} */
const pairingOffersByCode = new Map();
/** @type {Map<string, { attempts: number, windowStartMs: number }>} */
const pairAttemptState = new Map();
/** @type {Map<string, { controllerConnection: import('ws'), clientId: string, controllerId: string, code: string, createdAtMs: number }>} */
const pendingPairRequests = new Map();

function sendJson(ws, payload) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function nowMs() {
  return Date.now();
}

function isSixDigitCode(code) {
  return /^\d{6}$/.test(code);
}

function registerAttempt(controllerId) {
  const key = controllerId || 'anonymous';
  const current = pairAttemptState.get(key);
  const now = nowMs();

  if (!current || now - current.windowStartMs > PAIRING_WINDOW_MS) {
    pairAttemptState.set(key, { attempts: 1, windowStartMs: now });
    return true;
  }

  if (current.attempts >= PAIRING_MAX_ATTEMPTS_PER_WINDOW) {
    return false;
  }

  current.attempts += 1;
  pairAttemptState.set(key, current);
  return true;
}

function pruneExpiredOffers() {
  const now = nowMs();
  for (const [code, offer] of pairingOffersByCode.entries()) {
    if (offer.expiresAtMs <= now || offer.connection.readyState !== offer.connection.OPEN) {
      pairingOffersByCode.delete(code);
    }
  }

  for (const [requestId, pending] of pendingPairRequests.entries()) {
    if (now - pending.createdAtMs > PAIRING_MAX_TTL_MS) {
      pendingPairRequests.delete(requestId);
      sendJson(pending.controllerConnection, {
        type: 'controller.pairing_result',
        requestId,
        status: 'expired',
        reason: 'Pairing approval timeout'
      });
    }
  }
}

function validateExpiry(expiresAtMs) {
  if (typeof expiresAtMs !== 'number') {
    return false;
  }

  const now = nowMs();
  const ttl = expiresAtMs - now;
  return ttl > 0 && ttl <= PAIRING_MAX_TTL_MS;
}

function onClientRegisterPairingOffer(ws, message) {
  const { clientId, pairingCode, expiresAtMs, pairingAcceptanceEnabled, metadata } = message;

  if (typeof clientId !== 'string' || !isSixDigitCode(pairingCode) || !validateExpiry(expiresAtMs)) {
    sendJson(ws, {
      type: 'error',
      code: 'invalid_offer',
      message: 'Invalid pairing offer payload'
    });
    return;
  }

  pairingOffersByCode.set(pairingCode, {
    clientId,
    expiresAtMs,
    pairingAcceptanceEnabled: Boolean(pairingAcceptanceEnabled),
    connection: ws,
    metadata: typeof metadata === 'object' && metadata !== null ? metadata : undefined
  });

  ws.clientId = clientId;
  ws.currentPairingCode = pairingCode;

  sendJson(ws, {
    type: 'client.pairing_offer_registered',
    clientId,
    pairingCode,
    expiresAtMs
  });
}

function onControllerRequestPairing(ws, message) {
  const { controllerId, pairingCode } = message;

  if (typeof controllerId !== 'string' || !isSixDigitCode(pairingCode)) {
    sendJson(ws, {
      type: 'error',
      code: 'invalid_pairing_request',
      message: 'Invalid controller pairing payload'
    });
    return;
  }

  if (!registerAttempt(controllerId)) {
    sendJson(ws, {
      type: 'controller.pairing_result',
      status: 'rate_limited',
      reason: 'Too many attempts, retry later'
    });
    return;
  }

  pruneExpiredOffers();

  const offer = pairingOffersByCode.get(pairingCode);
  if (!offer) {
    sendJson(ws, {
      type: 'controller.pairing_result',
      status: 'failed',
      reason: 'Pairing code invalid or expired'
    });
    return;
  }

  if (!offer.pairingAcceptanceEnabled) {
    sendJson(ws, {
      type: 'controller.pairing_result',
      status: 'failed',
      reason: 'Client has not enabled pairing acceptance'
    });
    return;
  }

  const requestId = crypto.randomUUID();
  pendingPairRequests.set(requestId, {
    controllerConnection: ws,
    clientId: offer.clientId,
    controllerId,
    code: pairingCode,
    createdAtMs: nowMs()
  });

  sendJson(offer.connection, {
    type: 'client.pairing_request',
    requestId,
    controllerId
  });

  sendJson(ws, {
    type: 'controller.pairing_pending',
    requestId,
    clientId: offer.clientId
  });
}

function onClientPairingDecision(ws, message) {
  const { requestId, approved } = message;
  if (typeof requestId !== 'string' || typeof approved !== 'boolean') {
    sendJson(ws, {
      type: 'error',
      code: 'invalid_pairing_decision',
      message: 'Invalid pairing decision payload'
    });
    return;
  }

  const pending = pendingPairRequests.get(requestId);
  if (!pending) {
    sendJson(ws, {
      type: 'error',
      code: 'pairing_request_not_found',
      message: 'Pairing request is not active'
    });
    return;
  }

  pendingPairRequests.delete(requestId);

  if (!approved) {
    sendJson(pending.controllerConnection, {
      type: 'controller.pairing_result',
      requestId,
      status: 'rejected'
    });
    return;
  }

  // One-time code consumption to prevent replay.
  pairingOffersByCode.delete(pending.code);

  const pairedAt = nowMs();
  sendJson(pending.controllerConnection, {
    type: 'controller.pairing_result',
    requestId,
    status: 'paired',
    clientId: pending.clientId,
    pairedAt
  });

  sendJson(ws, {
    type: 'client.pairing_completed',
    requestId,
    controllerId: pending.controllerId,
    pairedAt
  });
}

function routeMessage(ws, rawPayload) {
  let message;
  try {
    message = JSON.parse(rawPayload.toString());
  } catch {
    sendJson(ws, {
      type: 'error',
      code: 'invalid_json',
      message: 'Payload must be JSON'
    });
    return;
  }

  if (!message || typeof message.type !== 'string') {
    sendJson(ws, {
      type: 'error',
      code: 'invalid_message',
      message: 'Message type is required'
    });
    return;
  }

  switch (message.type) {
    case 'client.register_pairing_offer':
      onClientRegisterPairingOffer(ws, message);
      break;
    case 'controller.request_pairing':
      onControllerRequestPairing(ws, message);
      break;
    case 'client.pairing_decision':
      onClientPairingDecision(ws, message);
      break;
    default:
      sendJson(ws, {
        type: 'error',
        code: 'unknown_message_type',
        message: 'Unsupported message type'
      });
      break;
  }
}

const wss = new WebSocketServer({ port: PORT });

setInterval(pruneExpiredOffers, 15 * 1000).unref();

wss.on('connection', (ws) => {
  ws.on('message', (payload) => routeMessage(ws, payload));

  ws.on('close', () => {
    if (ws.currentPairingCode && pairingOffersByCode.has(ws.currentPairingCode)) {
      pairingOffersByCode.delete(ws.currentPairingCode);
    }
  });

  sendJson(ws, {
    type: 'system.hello',
    message: 'Meizzo signaling ready'
  });
});

console.log(`Meizzo signaling listening on :${PORT}`);
