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
      for (const [sessionId, session] of sessions) {
        if (session.clientId !== clientId || !['pending', 'approved'].includes(session.state)) continue;
        session.state = 'failed';
        session.endedAtMs = now;
        session.terminationReason = 'client_heartbeat_timeout';
        notifySession(session);
        sessions.delete(sessionId);
      }
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

function terminateSessionsForConnection(connection, role, identity, reason) {
  if (!connection) return;
  for (const [sessionId, session] of sessions) {
    const matches = role === 'client'
      ? session.clientConnection === connection && session.clientId === identity
      : session.controllerConnection === connection && session.controllerId === identity;
    if (!matches || !['pending', 'approved'].includes(session.state)) continue;
    session.state = 'failed';
    session.endedAtMs = nowMs();
    session.terminationReason = reason;
    notifySession(session);
    sessions.delete(sessionId);
  }
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
    // A token can reconnect on a new socket while the previous socket remains
    // open. Never broadcast device metadata to a socket that no longer owns
    // an active credential for this device.
    if (!controllerHasDeviceAccess(controller, device)) {
      device.controllerConnections.delete(controller);
      continue;
    }
    sendJson(controller, {
      type: 'device.status',
      clientId,
      status: device.status,
      lastSeen: device.lastSeen,
      metadata: device.metadata
    });
  }
}

function controllerHasDeviceAccess(ws, device) {
  if (!device || ws.auth?.role !== 'controller') return false;
  return [...device.controllerTokens].some((value) =>
    ws.authenticatedControllerTokens?.has(value) &&
    controllerSessions.get(value)?.controllerId === ws.auth.controllerId &&
    controllerSessions.get(value)?.connection === ws
  );
}

function authenticate(ws, message) {
  if (!isNonEmptyString(message.authToken)) return null;
  if (message.role === 'controller' && !isNonEmptyString(message.clientId)) {
    const session = controllerSessions.get(message.authToken);
    if (!session) return null;
    if (session.connection && session.connection !== ws) {
      terminateSessionsForConnection(
        session.connection, 'controller', session.controllerId, 'controller_reconnected'
      );
    }
    session.connection = ws;
    ws.authenticatedControllerIds = ws.authenticatedControllerIds || new Set();
    ws.authenticatedControllerIds.add(session.controllerId);
    ws.authenticatedControllerTokens = ws.authenticatedControllerTokens || new Set();
    ws.authenticatedControllerTokens.add(message.authToken);
    ws.auth = { role: 'controller', controllerId: session.controllerId };
    for (const device of pairedDevices.values()) {
      if (device.controllerTokens.has(message.authToken)) device.controllerConnections.add(ws);
    }
    return session;
  }
  if (!isNonEmptyString(message.clientId)) return null;
  const device = pairedDevices.get(message.clientId);
  if (!device) return null;
  if (message.role === 'client' && message.authToken === device.clientToken) {
    if (device.clientConnection && device.clientConnection !== ws) {
      terminateSessionsForConnection(
        device.clientConnection, 'client', message.clientId, 'client_reconnected'
      );
    }
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
    if (session.connection && session.connection !== ws) {
      terminateSessionsForConnection(
        session.connection, 'controller', session.controllerId, 'controller_reconnected'
      );
    }
    session.connection = ws;
    ws.authenticatedControllerIds = ws.authenticatedControllerIds || new Set();
    ws.authenticatedControllerIds.add(session.controllerId);
    ws.authenticatedControllerTokens = ws.authenticatedControllerTokens || new Set();
    ws.authenticatedControllerTokens.add(message.authToken);
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
  const pairedDevice = pairedDevices.get(message.clientId);
  if (pairedDevice && pairedDevice.clientConnection !== ws) {
    sendError(ws, 'client_id_in_use', 'This Client ID is already owned by another authenticated connection');
    return;
  }
  if (pairedDevice && (ws.auth?.role !== 'client' || ws.auth.clientId !== message.clientId)) {
    sendError(ws, 'unauthorized', 'Authenticate as this Client before refreshing its pairing offer');
    return;
  }
  const existingOffer = pairingOffersByCode.get(message.pairingCode);
  if (existingOffer && existingOffer.connection !== ws &&
      existingOffer.expiresAtMs > nowMs() &&
      existingOffer.connection.readyState === existingOffer.connection.OPEN) {
    sendError(ws, 'pairing_code_conflict', 'Pairing code is already in use');
    return;
  }

  // A Client may have only one active pairing code. Remove its previous code
  // before registering the replacement so rotated/disabled codes cannot linger.
  if (ws.currentPairingCode) {
    const previousOffer = pairingOffersByCode.get(ws.currentPairingCode);
    if (previousOffer?.connection === ws) pairingOffersByCode.delete(ws.currentPairingCode);
  }
  if (existingOffer && existingOffer.connection !== ws) {
    pairingOffersByCode.delete(message.pairingCode);
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
  const existingDevice = pairedDevices.get(pending.clientId);
  if (existingDevice && existingDevice.clientConnection !== ws) {
    pendingPairRequests.delete(message.requestId);
    sendJson(pending.controllerConnection, {
      type: 'controller.pairing_result',
      requestId: message.requestId,
      status: 'failed',
      reason: 'Client connection ownership changed; restart pairing'
    });
    return;
  }
  pendingPairRequests.delete(message.requestId);
  if (!message.approved) {
    sendJson(pending.controllerConnection, { type: 'controller.pairing_result', requestId: message.requestId, status: 'rejected' });
    return;
  }
  pairingOffersByCode.delete(pending.code);
  const device = existingDevice || null;
  const clientToken = device?.clientToken || token();
  const controllerToken = token();
  if (device) {
    // Re-pairing the same Controller ID rotates its credential rather than
    // leaving multiple valid tokens behind.
    for (const oldToken of [...device.controllerTokens]) {
      const oldSession = controllerSessions.get(oldToken);
      if (oldSession?.controllerId !== pending.controllerId) continue;
      controllerSessions.delete(oldToken);
      device.controllerTokens.delete(oldToken);
      const oldConnection = oldSession.connection;
      if (oldConnection && oldConnection !== pending.controllerConnection) {
        oldConnection.authenticatedControllerTokens?.delete(oldToken);
        device.controllerConnections.delete(oldConnection);
        sendJson(oldConnection, { type: 'device.revoked', clientId: device.clientId, reason: 'credential_rotated' });
      } else if (oldConnection === pending.controllerConnection) {
        oldConnection.authenticatedControllerTokens?.delete(oldToken);
      }
    }
    device.metadata = offer.metadata;
    device.controllerTokens.add(controllerToken);
    device.controllerIds.add(pending.controllerId);
    device.controllerConnections.add(pending.controllerConnection);
    device.clientHeartbeat = nowMs();
    device.lastSeen = nowMs();
    device.status = 'online';
  } else {
    pairedDevices.set(pending.clientId, {
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
    });
  }
  const pairedDevice = pairedDevices.get(pending.clientId);
  const controllerSession = {
    controllerId: pending.controllerId,
    token: controllerToken,
    connection: pending.controllerConnection
  };
  controllerSessions.set(controllerToken, controllerSession);
  ws.auth = { role: 'client', clientId: pairedDevice.clientId };
  pending.controllerConnection.authenticatedControllerIds =
    pending.controllerConnection.authenticatedControllerIds || new Set();
  pending.controllerConnection.authenticatedControllerIds.add(pending.controllerId);
  pending.controllerConnection.authenticatedControllerTokens =
    pending.controllerConnection.authenticatedControllerTokens || new Set();
  pending.controllerConnection.authenticatedControllerTokens.add(controllerToken);
  broadcastDeviceStatus(pairedDevice.clientId);
  sendJson(pending.controllerConnection, {
    type: 'controller.pairing_result',
    requestId: message.requestId,
    status: 'paired',
    clientId: pairedDevice.clientId,
    authToken: controllerToken,
    pairedAt: nowMs(),
    metadata: pairedDevice.metadata
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
  if (!controllerHasDeviceAccess(ws, device)) return null;
  return device;
}

function onSessionStart(ws, message) {
  const device = authorizedDeviceForController(ws, message.clientId);
  if (!device) return sendError(ws, 'unauthorized', 'Not authorized for this device');
  if (!SESSION_TYPES.has(message.sessionType)) {
    return sendError(ws, 'invalid_session_type', 'Unsupported session type');
  }
  if (!device.clientConnection) return sendError(ws, 'offline', 'Client is offline');
  const activeDuplicate = [...sessions.values()].some((session) =>
    session.clientId === device.clientId &&
    ['pending', 'approved'].includes(session.state) &&
    (message.sessionType === 'interaction'
      ? session.sessionType === 'interaction'
      : session.controllerId === ws.auth.controllerId && session.sessionType === message.sessionType)
  );
  if (activeDuplicate) {
    return sendError(ws, 'session_already_active', 'An equivalent session is already active for this Client');
  }
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
    const authorized = controllerHasDeviceAccess(ws, device);
    if (authorized) {
      devices.push({ clientId: device.clientId, status: device.status, lastSeen: device.lastSeen, metadata: device.metadata });
    }
  }
  sendJson(ws, { type: 'controller.device_list', devices });
}

function onControllerSnapshot(ws, message) {
  const device = pairedDevices.get(message.clientId);
  if (!controllerHasDeviceAccess(ws, device)) {
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
  if (!controllerHasDeviceAccess(ws, device)) {
    return sendError(ws, 'unauthorized', 'Not authorized for this device');
  }
  if (!device.clientConnection) return sendError(ws, 'offline', 'Client is offline');
  if (!isNonEmptyString(message.command)) return sendError(ws, 'invalid_command', 'Command is required');
  const hasApprovedInteractionSession = [...sessions.values()].some((session) =>
    session.clientId === device.clientId &&
    session.controllerId === ws.auth.controllerId &&
    session.controllerConnection === ws &&
    session.clientConnection === device.clientConnection &&
    session.state === 'approved' &&
    session.sessionType === 'interaction'
  );
  if (!hasApprovedInteractionSession) {
    return sendError(ws, 'session_required', 'An approved interaction session is required');
  }
  const requestId = crypto.randomUUID();
  device.clientConnection.send(JSON.stringify({
    type: 'client.command',
    requestId,
    command: message.command,
    parameters: message.parameters && typeof message.parameters === 'object' &&
      !Array.isArray(message.parameters) ? message.parameters : {}
  }));
  device.pendingRequests = device.pendingRequests || new Map();
  device.pendingRequests.set(requestId, ws);
}

function onRenameDevice(ws, message) {
  const device = pairedDevices.get(message.clientId);
  if (!controllerHasDeviceAccess(ws, device)) {
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
  if (!controllerHasDeviceAccess(target, device)) {
    return sendError(ws, 'request_unauthorized', 'The requesting Controller no longer has device access');
  }
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
  if (!controllerHasDeviceAccess(ws, device)) {
    return sendError(ws, 'unauthorized', 'Not authorized to revoke this device');
  }
  device.controllerIds.delete(ws.auth.controllerId);
  const revokedTokens = new Set();
  for (const value of [...device.controllerTokens]) {
    if (controllerSessions.get(value)?.controllerId === ws.auth.controllerId) {
      controllerSessions.delete(value);
      device.controllerTokens.delete(value);
      revokedTokens.add(value);
    }
  }
  for (const controller of [...device.controllerConnections]) {
    const hadRevokedToken = [...revokedTokens].some((value) =>
      controller.authenticatedControllerTokens?.has(value)
    );
    if (!hadRevokedToken) continue;
    for (const value of revokedTokens) controller.authenticatedControllerTokens.delete(value);
    device.controllerConnections.delete(controller);
    sendJson(controller, { type: 'device.revoked', clientId: device.clientId });
  }
  for (const [requestId, target] of device.pendingRequests || []) {
    if (target.auth?.role === 'controller' && target.auth.controllerId === ws.auth.controllerId) {
      device.pendingRequests.delete(requestId);
    }
  }
  for (const session of sessions.values()) {
    if (session.clientId !== device.clientId || session.controllerId !== ws.auth.controllerId ||
        !['pending', 'approved'].includes(session.state)) continue;
    session.state = 'failed';
    session.endedAtMs = nowMs();
    session.terminationReason = 'controller_revoked';
    notifySession(session);
    sessions.delete(session.sessionId);
  }
  if (device.controllerIds.size > 0) return;
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
const wss = new WebSocketServer({ server: healthServer, maxPayload: 64 * 1024 });
setInterval(prune, 15 * 1000).unref();

wss.on('connection', (ws) => {
  ws.on('message', (payload) => routeMessage(ws, payload));
  ws.on('close', () => {
    if (ws.currentPairingCode) {
      const currentOffer = pairingOffersByCode.get(ws.currentPairingCode);
      if (currentOffer?.connection === ws) pairingOffersByCode.delete(ws.currentPairingCode);
    }
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
    for (const current of pairedDevices.values()) {
      if (ws.auth?.role === 'controller') {
        for (const [requestId, target] of current.pendingRequests || []) {
          if (target === ws) current.pendingRequests.delete(requestId);
        }
      }
      if (ws.auth?.role === 'client' && current.clientConnection === ws) {
        for (const [requestId, target] of current.pendingRequests || []) {
          if (controllerHasDeviceAccess(target, current)) {
            sendJson(target, {
              type: 'device.result',
              clientId: current.clientId,
              requestId,
              result: {},
              error: 'Client disconnected before responding'
            });
          }
          current.pendingRequests.delete(requestId);
        }
      }
    }
    if (device && ws.auth?.role === 'client' && device.clientConnection === ws) {
      // A WebSocket close is not proof that the device is offline. Mobile OSes can
      // suspend or terminate an app process while the device remains reachable.
      // Keep the last authenticated connection/heartbeat until prune() applies
      // HEARTBEAT_TIMEOUT_MS. A reconnect replaces this connection in authenticate().
      logEvent('presence.socket_closed', { clientId: device.clientId });
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
