'use strict';

const crypto = require('crypto');
const http = require('http');
const { WebSocketServer } = require('ws');
const { PROTOCOL_VERSION, SERVER_CAPABILITIES } = require('./protocol');

const PORT = Number(process.env.PORT || 8080);
const PAIRING_MAX_TTL_MS = 10 * 60 * 1000;
const PAIRING_WINDOW_MS = 60 * 1000;
const PAIRING_MAX_ATTEMPTS_PER_WINDOW = 10;
const HEARTBEAT_TIMEOUT_MS = 45 * 1000;

const pairingOffersByCode = new Map();
const pairAttemptState = new Map();
const pairIpAttemptState = new Map();
const pendingPairRequests = new Map();
const pairedDevices = new Map();
const controllerSessions = new Map();
const sessions = new Map();
const SESSION_TYPES = new Set(['monitoring', 'screen', 'camera', 'interaction', 'files']);
const SESSION_PENDING_TTL_MS = 10 * 60 * 1000;

function nowMs() {
  return Date.now();
}

function logEvent(event, details = {}) {
  console.log(JSON.stringify({ event, at: nowMs(), ...details }));
}

function sendJson(ws, payload) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(payload));
}

function isSixDigitCode(code) {
  return typeof code === 'string' && /^\d{6}$/.test(code);
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.length > 0 && value.length <= 128;
}

function token() {
  return crypto.randomBytes(32).toString('base64url');
}

function consumeAttempt(map, key, limit, now) {
  const current = map.get(key);
  if (!current || now - current.windowStartMs > PAIRING_WINDOW_MS) {
    map.set(key, { attempts: 1, windowStartMs: now });
    return true;
  }
  if (current.attempts >= limit) return false;
  current.attempts += 1;
  return true;
}

function registerAttempt(controllerId, ws) {
  const now = nowMs();
  const controllerKey = controllerId || 'anonymous';
  const addressKey = ws._socket?.remoteAddress || 'unknown';
  const controllerAllowed = consumeAttempt(
    pairAttemptState, controllerKey, PAIRING_MAX_ATTEMPTS_PER_WINDOW, now
  );
  const addressAllowed = consumeAttempt(pairIpAttemptState, addressKey, 30, now);
  return controllerAllowed && addressAllowed;
}

function validateExpiry(expiresAtMs) {
  return Number.isFinite(expiresAtMs) &&
    expiresAtMs > nowMs() &&
    expiresAtMs - nowMs() <= PAIRING_MAX_TTL_MS;
}

function sendError(ws, code, message) {
  sendJson(ws, { type: 'error', code, message });
}

function prune() {
  const now = nowMs();
  for (const map of [pairAttemptState, pairIpAttemptState]) {
    for (const [key, state] of map) {
      if (now - state.windowStartMs > PAIRING_WINDOW_MS) map.delete(key);
    }
  }
  for (const [code, offer] of pairingOffersByCode) {
    if (offer.expiresAtMs <= now || offer.connection.readyState !== offer.connection.OPEN) {
      pairingOffersByCode.delete(code);
    }
  }
  for (const [requestId, pending] of pendingPairRequests) {
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
  for (const [clientId, device] of pairedDevices) {
    if (device.clientConnection && now - device.clientHeartbeat > HEARTBEAT_TIMEOUT_MS) {
      device.clientConnection = null;
      device.status = 'offline';
      device.lastSeen = now;
      logEvent('presence.offline', { clientId });
      broadcastDeviceStatus(clientId);
    }
  }
  for (const [sessionId, session] of sessions) {
    if (session.state === 'pending' && now - session.createdAtMs > SESSION_PENDING_TTL_MS) {
      session.state = 'failed';
      session.endedAtMs = now;
      session.terminationReason = 'approval_timeout';
      notifySession(session);
      sessions.delete(sessionId);
    }
  }
}

function notifySession(session) {
  const payload = {
    type: 'session.status',
    sessionId: session.sessionId,
    clientId: session.clientId,
    controllerId: session.controllerId,
    sessionType: session.sessionType,
    state: session.state,
    createdAtMs: session.createdAtMs,
    endedAtMs: session.endedAtMs || null,
    terminationReason: session.terminationReason || null
  };
  sendJson(session.controllerConnection, payload);
  sendJson(session.clientConnection, {
    ...payload,
    type: 'client.session_status'
  });
}

function deviceForConnection(ws) {
  for (const device of pairedDevices.values()) {
    if (device.clientConnection === ws || device.controllerConnections.has(ws)) return device;
  }
  return null;
}

function broadcastDeviceStatus(clientId) {
  const device = pairedDevices.get(clientId);
  if (!device) return;
  for (const controller of device.controllerConnections) {
    sendJson(controller, {
      type: 'device.status',
      clientId,
      status: device.status,
      lastSeen: device.lastSeen,
      metadata: device.metadata
    });
  }
}

function controllerAuthorized(ws, controllerId) {
  return Boolean(controllerId && ws.authenticatedControllerIds?.has(controllerId));
}

function authenticate(ws, message) {
  if (!isNonEmptyString(message.authToken)) return null;
  if (message.role === 'controller' && !isNonEmptyString(message.clientId)) {
    const session = controllerSessions.get(message.authToken);
    if (!session) return null;
    session.connection = ws;
    ws.authenticatedControllerIds = ws.authenticatedControllerIds || new Set();
    ws.authenticatedControllerIds.add(session.controllerId);
    ws.auth = { role: 'controller', controllerId: session.controllerId };
    for (const device of pairedDevices.values()) {
      if (device.controllerIds.has(session.controllerId)) device.controllerConnections.add(ws);
    }
    return session;
  }
  if (!isNonEmptyString(message.clientId)) return null;
  const device = pairedDevices.get(message.clientId);
  if (!device) {
    const session = controllerSessions.get(message.authToken);
    if (message.role === 'controller' && session) {
      session.connection = ws;
      ws.auth = { role: 'controller', controllerId: session.controllerId };
      return session;
    }
    return null;
  }
  if (message.role === 'client' && message.authToken === device.clientToken) {
    device.clientConnection = ws;
    device.clientHeartbeat = nowMs();
    device.lastSeen = nowMs();
    device.status = 'online';
    ws.auth = { role: 'client', clientId: message.clientId };
    broadcastDeviceStatus(device.clientId);
    logEvent('presence.heartbeat', { clientId: device.clientId });
    return device;
  }
  if (message.role === 'controller' && device.controllerTokens.has(message.authToken)) {
    const session = controllerSessions.get(message.authToken);
    if (!session) return null;
    session.connection = ws;
    ws.authenticatedControllerIds = ws.authenticatedControllerIds || new Set();
    ws.authenticatedControllerIds.add(session.controllerId);
    device.controllerConnections.add(ws);
    ws.auth = { role: 'controller', controllerId: session.controllerId };
    return device;
  }
  return null;
}

function onOffer(ws, message) {
  if (!isNonEmptyString(message.clientId) || !isSixDigitCode(message.pairingCode) ||
      !validateExpiry(message.expiresAtMs)) {
    sendError(ws, 'invalid_offer', 'Invalid pairing offer payload');
    return;
  }
  pairingOffersByCode.set(message.pairingCode, {
    clientId: message.clientId,
    expiresAtMs: message.expiresAtMs,
    pairingAcceptanceEnabled: message.pairingAcceptanceEnabled === true,
    connection: ws,
    metadata: message.metadata && typeof message.metadata === 'object' ? message.metadata : {}
  });
  ws.currentPairingCode = message.pairingCode;
  sendJson(ws, {
    type: 'client.pairing_offer_registered',
    clientId: message.clientId,
    pairingCode: message.pairingCode,
    expiresAtMs: message.expiresAtMs
  });
}

function onPairingRequest(ws, message) {
  if (!isNonEmptyString(message.controllerId) || !isSixDigitCode(message.pairingCode)) {
    sendError(ws, 'invalid_pairing_request', 'Invalid controller pairing payload');
    return;
  }
  if (!registerAttempt(message.controllerId, ws)) {
    sendJson(ws, { type: 'controller.pairing_result', status: 'rate_limited', reason: 'Too many attempts, retry later' });
    return;
  }
  prune();
  const offer = pairingOffersByCode.get(message.pairingCode);
  if (!offer || !offer.pairingAcceptanceEnabled) {
    sendJson(ws, { type: 'controller.pairing_result', status: 'failed', reason: 'Pairing code invalid, expired, or disabled' });
    return;
  }
  const requestId = crypto.randomUUID();
  pendingPairRequests.set(requestId, {
    controllerConnection: ws,
    clientId: offer.clientId,
    controllerId: message.controllerId,
    code: message.pairingCode,
    createdAtMs: nowMs()
  });
  sendJson(offer.connection, { type: 'client.pairing_request', requestId, controllerId: message.controllerId });
  sendJson(ws, { type: 'controller.pairing_pending', requestId, clientId: offer.clientId });
}

function onPairingDecision(ws, message) {
  if (!isNonEmptyString(message.requestId) || typeof message.approved !== 'boolean') {
    sendError(ws, 'invalid_pairing_decision', 'Invalid pairing decision payload');
    return;
  }
  const pending = pendingPairRequests.get(message.requestId);
  if (!pending) {
    sendError(ws, 'pairing_request_not_found', 'Pairing request is not active');
    return;
  }
  const offer = pairingOffersByCode.get(pending.code);
  if (!offer || offer.connection !== ws) {
    sendError(ws, 'pairing_offer_not_found', 'Pairing offer is no longer active');
    pendingPairRequests.delete(message.requestId);
    return;
  }
  pendingPairRequests.delete(message.requestId);
  if (!message.approved) {
    sendJson(pending.controllerConnection, { type: 'controller.pairing_result', requestId: message.requestId, status: 'rejected' });
    return;
  }
  pairingOffersByCode.delete(pending.code);
  const clientToken = token();
  const controllerToken = token();
  const controllerSession = {
    controllerId: pending.controllerId,
    token: controllerToken,
    connection: pending.controllerConnection
  };
  controllerSessions.set(controllerToken, controllerSession);
  const device = {
    clientId: pending.clientId,
    metadata: offer.metadata,
    clientToken,
    controllerTokens: new Set([controllerToken]),
    controllerIds: new Set([pending.controllerId]),
    clientConnection: ws,
    controllerConnections: new Set([pending.controllerConnection]),
    clientHeartbeat: nowMs(),
    lastSeen: nowMs(),
    status: 'online'
  };
  ws.auth = { role: 'client', clientId: device.clientId };
  pending.controllerConnection.authenticatedControllerIds =
    pending.controllerConnection.authenticatedControllerIds || new Set();
  pending.controllerConnection.authenticatedControllerIds.add(pending.controllerId);
  pairedDevices.set(device.clientId, device);
  sendJson(pending.controllerConnection, {
    type: 'controller.pairing_result',
    requestId: message.requestId,
    status: 'paired',
    clientId: device.clientId,
    authToken: controllerToken,
    pairedAt: nowMs(),
    metadata: device.metadata
  });
  sendJson(ws, {
    type: 'client.pairing_completed',
    requestId: message.requestId,
    controllerId: pending.controllerId,
    authToken: clientToken,
    pairedAt: nowMs()
  });
}

function onHeartbeat(ws, message) {
  const device = deviceForConnection(ws);
  if (!device) return sendError(ws, 'unauthorized', 'Authenticate before sending heartbeats');
  if (ws.auth.role === 'client') {
    device.clientHeartbeat = nowMs();
    device.lastSeen = nowMs();
    device.status = 'online';
    if (message.telemetry && typeof message.telemetry === 'object') {
      device.metadata = { ...device.metadata, telemetry: message.telemetry };
    }

    broadcastDeviceStatus(device.clientId);
  }
  sendJson(ws, { type: 'heartbeat.ack', timestamp: nowMs() });
}

function authorizedDeviceForController(ws, clientId) {
  const device = pairedDevices.get(clientId);
  if (!device || ws.auth?.role !== 'controller' ||
      !controllerAuthorized(ws, ws.auth.controllerId) ||
      !device.controllerIds.has(ws.auth.controllerId)) return null;
  return device;
}

function onSessionStart(ws, message) {
  const device = authorizedDeviceForController(ws, message.clientId);
  if (!device) return sendError(ws, 'unauthorized', 'Not authorized for this device');
  if (!SESSION_TYPES.has(message.sessionType)) {
    return sendError(ws, 'invalid_session_type', 'Unsupported session type');
  }
  if (!device.clientConnection) return sendError(ws, 'offline', 'Client is offline');
  const session = {
    sessionId: crypto.randomUUID(),
    clientId: device.clientId,
    controllerId: ws.auth.controllerId,
    sessionType: message.sessionType,
    state: 'pending',
    createdAtMs: nowMs(),
    controllerConnection: ws,
    clientConnection: device.clientConnection
  };
  sessions.set(session.sessionId, session);
  notifySession(session);
}

function onClientSessionDecision(ws, message) {
  const session = sessions.get(message.sessionId);
  if (!session || ws.auth?.role !== 'client' ||
      session.clientId !== ws.auth.clientId || session.clientConnection !== ws ||
      typeof message.approved !== 'boolean') {
    return sendError(ws, 'unauthorized', 'Invalid session decision');
  }
  if (session.state !== 'pending') {
    return sendError(ws, 'session_not_pending', 'Session is no longer awaiting approval');
  }
  session.state = message.approved ? 'approved' : 'rejected';
  session.endedAtMs = message.approved ? undefined : nowMs();
  session.terminationReason = message.approved ? undefined : 'client_rejected';
  notifySession(session);
  if (!message.approved) sessions.delete(session.sessionId);
}

function onSessionEnd(ws, message) {
  const session = sessions.get(message.sessionId);
  if (!session || ws.auth?.role !== 'controller' ||
      session.controllerId !== ws.auth.controllerId ||
      session.controllerConnection !== ws) {
    return sendError(ws, 'unauthorized', 'Invalid session termination');
  }
  session.state = 'ended';
  session.endedAtMs = nowMs();
  session.terminationReason = isNonEmptyString(message.reason) ? message.reason : 'controller_ended';
  notifySession(session);
  sessions.delete(session.sessionId);
}

function onDeviceList(ws) {
  const devices = [];
  for (const device of pairedDevices.values()) {
    const authorized = [...(ws.authenticatedControllerIds || [])]
      .some((controllerId) => device.controllerIds.has(controllerId));
    if (authorized) {
      devices.push({ clientId: device.clientId, status: device.status, lastSeen: device.lastSeen, metadata: device.metadata });
    }
  }
  sendJson(ws, { type: 'controller.device_list', devices });
}

function onControllerSnapshot(ws, message) {
  const device = pairedDevices.get(message.clientId);
  if (!device || !controllerAuthorized(ws, ws.auth?.controllerId) ||
      !device.controllerIds.has(ws.auth.controllerId)) {
    return sendError(ws, 'unauthorized', 'Not authorized for this device');
  }
  if (!device.clientConnection) return sendError(ws, 'offline', 'Client is offline');
  const requestId = crypto.randomUUID();
  device.clientConnection.send(JSON.stringify({
    type: 'client.snapshot_request',
    requestId,
    requestedAt: nowMs()
  }));
  device.pendingRequests = device.pendingRequests || new Map();
  device.pendingRequests.set(requestId, ws);
}

function onControllerCommand(ws, message) {
  const device = pairedDevices.get(message.clientId);
  if (!device || !controllerAuthorized(ws, ws.auth?.controllerId) ||
      !device.controllerIds.has(ws.auth.controllerId)) {
    return sendError(ws, 'unauthorized', 'Not authorized for this device');
  }
  if (!device.clientConnection) return sendError(ws, 'offline', 'Client is offline');
  if (!isNonEmptyString(message.command)) return sendError(ws, 'invalid_command', 'Command is required');
  const requestId = crypto.randomUUID();
  device.clientConnection.send(JSON.stringify({
    type: 'client.command',
    requestId,
    command: message.command,
    parameters: message.parameters && typeof message.parameters === 'object' ? message.parameters : {}
  }));
  device.pendingRequests = device.pendingRequests || new Map();
  device.pendingRequests.set(requestId, ws);
}

function onRenameDevice(ws, message) {
  const device = pairedDevices.get(message.clientId);
  if (!device || ws.auth?.role !== 'controller' ||
      !controllerAuthorized(ws, ws.auth.controllerId) ||
      !device.controllerIds.has(ws.auth.controllerId)) {
    return sendError(ws, 'unauthorized', 'Not authorized for this device');
  }
  if (!isNonEmptyString(message.alias) || message.alias.length > 64) {
    return sendError(ws, 'invalid_alias', 'Alias must be between 1 and 64 characters');
  }
  device.metadata = { ...device.metadata, alias: message.alias };
  broadcastDeviceStatus(device.clientId);
  sendJson(ws, { type: 'device.renamed', clientId: device.clientId, alias: message.alias });
}

function onClientResult(ws, message) {
  const device = deviceForConnection(ws);
  if (!device || ws.auth?.role !== 'client' || !isNonEmptyString(message.requestId)) {
    return sendError(ws, 'unauthorized', 'Invalid client response');
  }
  const target = device.pendingRequests?.get(message.requestId);
  if (!target) return sendError(ws, 'request_not_found', 'Request is no longer active');
  device.pendingRequests.delete(message.requestId);
  sendJson(target, {
    type: 'device.result',
    clientId: device.clientId,
    requestId: message.requestId,
    result: message.result && typeof message.result === 'object' ? message.result : {},
    error: message.error || null
  });
}

function onRevoke(ws, message) {
  const device = pairedDevices.get(message.clientId);
  if (!device || ws.auth?.role !== 'controller' ||
      !controllerAuthorized(ws, ws.auth.controllerId) ||
      !device.controllerIds.has(ws.auth.controllerId)) {
    return sendError(ws, 'unauthorized', 'Not authorized to revoke this device');
  }
  device.controllerIds.delete(ws.auth.controllerId);
  device.controllerConnections.delete(ws);
  const tokenToDelete = [...device.controllerTokens].find((value) => controllerSessions.get(value)?.controllerId === ws.auth.controllerId);
  if (tokenToDelete) controllerSessions.delete(tokenToDelete);
  if (device.controllerIds.size > 0) return sendJson(ws, { type: 'device.revoked', clientId: device.clientId });
  pairedDevices.delete(device.clientId);
  if (device.clientConnection) sendJson(device.clientConnection, { type: 'device.revoked', clientId: device.clientId });
  for (const controller of device.controllerConnections) {
    sendJson(controller, { type: 'device.revoked', clientId: device.clientId });
  }
}

function routeMessage(ws, rawPayload) {
  let message;
  try { message = JSON.parse(rawPayload.toString()); } catch { return sendError(ws, 'invalid_json', 'Payload must be JSON'); }
  if (!message || typeof message.type !== 'string') return sendError(ws, 'invalid_message', 'Message type is required');
  switch (message.type) {
    case 'client.register_pairing_offer': return onOffer(ws, message);
    case 'controller.request_pairing': return onPairingRequest(ws, message);
    case 'client.pairing_decision': return onPairingDecision(ws, message);
    case 'auth.connect': {
      const device = authenticate(ws, message);
      if (!device) return sendError(ws, 'unauthorized', 'Invalid authentication token');
      if (ws.auth.role === 'controller') {
        logEvent('auth.success', { role: ws.auth.role, controllerId: ws.auth.controllerId });
        sendJson(ws, { type: 'controller.status', status: 'connected', timestamp: nowMs() });
      } else {
        logEvent('auth.success', { role: ws.auth.role, clientId: ws.auth.clientId });
      }
      sendJson(ws, {
        type: 'auth.connected',
        protocolVersion: PROTOCOL_VERSION,
        capabilities: SERVER_CAPABILITIES,
        clientId: device.clientId,
        role: ws.auth.role,
        status: device.status
      });
      return;
    }
    case 'heartbeat': return onHeartbeat(ws, message);
    case 'controller.start_session': return onSessionStart(ws, message);
    case 'client.session_decision': return onClientSessionDecision(ws, message);
    case 'controller.end_session': return onSessionEnd(ws, message);
    case 'controller.list_devices': return onDeviceList(ws);
    case 'controller.request_snapshot': return onControllerSnapshot(ws, message);
    case 'controller.command': return onControllerCommand(ws, message);
    case 'controller.rename_device': return onRenameDevice(ws, message);
    case 'client.result': return onClientResult(ws, message);
    case 'controller.revoke_device': return onRevoke(ws, message);
    default: return sendError(ws, 'unknown_message_type', 'Unsupported message type');
  }
}

const healthServer = http.createServer((req, res) => {
  if (req.url === '/healthz') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ status: 'ok', uptimeSeconds: process.uptime() }));
  }
  res.writeHead(404);
  res.end();
});
healthServer.listen(PORT, '0.0.0.0');
const wss = new WebSocketServer({ server: healthServer });
setInterval(prune, 15 * 1000).unref();

wss.on('connection', (ws) => {
  ws.on('message', (payload) => routeMessage(ws, payload));
  ws.on('close', () => {
    if (ws.currentPairingCode) pairingOffersByCode.delete(ws.currentPairingCode);
    const device = deviceForConnection(ws);
    if (ws.authenticatedControllerIds) {
      for (const current of pairedDevices.values()) current.controllerConnections.delete(ws);
    }
    for (const session of controllerSessions.values()) {
      if (session.connection === ws) session.connection = null;
    }
    for (const [sessionId, session] of sessions) {
      if (session.controllerConnection !== ws && session.clientConnection !== ws) continue;
      session.state = 'failed';
      session.endedAtMs = nowMs();
      session.terminationReason = session.clientConnection === ws
        ? 'client_disconnected'
        : 'controller_disconnected';
      notifySession(session);
      sessions.delete(sessionId);
    }
    if (device && ws.auth?.role === 'client' && device.clientConnection === ws) {
      device.clientConnection = null;
      device.status = 'offline';
      device.lastSeen = nowMs();
      logEvent('presence.disconnect', { clientId: device.clientId });
      broadcastDeviceStatus(device.clientId);
    }
  });
  sendJson(ws, {
    type: 'system.hello',
    protocolVersion: PROTOCOL_VERSION,
    capabilities: SERVER_CAPABILITIES,
    message: 'Meizzo signaling ready'
  });
});

console.log(`Meizzo signaling listening on :${PORT}`);
