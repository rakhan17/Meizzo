const PROTOCOL_VERSION = 2;
const SERVER_CAPABILITIES = ["pairing", "token-authentication", "presence", "telemetry", "device-commands", "multi-device-routing", "session-authorization", "media-signaling", "turn-credentials", "remote-interaction"];
const PAIRING_MAX_TTL_MS = 10 * 60 * 1000;
const HEARTBEAT_TIMEOUT_MS = 45 * 1000;
const SESSION_PENDING_TTL_MS = 10 * 60 * 1000;
const SESSION_TYPES = new Set(["monitoring", "screen", "camera", "interaction", "files"]);
const now = () => Date.now();
const id = () => crypto.randomUUID();
const randomToken = () => {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/g, "");
};
const validString = (v, max = 128) => typeof v === "string" && v.length > 0 && v.length <= max;
const validCode = v => typeof v === "string" && /^\d{6}$/.test(v);
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" } });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/healthz") return json({ status: "ok", service: "meizzo-signaling", runtime: "cloudflare-workers", protocolVersion: PROTOCOL_VERSION });
    if (url.pathname !== "/" && url.pathname !== "/ws") return json({ error: "not_found" }, 404);
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return json({ service: "meizzo-signaling", websocket: "available", health: "/healthz" });
    const stub = env.MEIZZO_SIGNALING.get(env.MEIZZO_SIGNALING.idFromName("global"));
    return stub.fetch(request);
  }
};

export class MeizzoSignaling {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") return json({ error: "websocket_upgrade_required" }, 426);
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ role: "guest", ip: request.headers.get("CF-Connecting-IP") || "unknown", connId: id() });
    this.send(server, { type: "system.hello", protocolVersion: PROTOCOL_VERSION, capabilities: SERVER_CAPABILITIES, message: "Meizzo signaling ready" });
    await this.ensureAlarm();
    return new Response(null, { status: 101, webSocket: client });
  }

  async state() {
    return (await this.ctx.storage.get("state")) || {
      offers: {}, attempts: {}, ipAttempts: {}, pending: {}, devices: {}, controllers: {}, sessions: {}
    };
  }

  async save(s) {
    await this.ctx.storage.put("state", s);
    await this.ensureAlarm();
  }

  async getIceServers() {
    const fallback = [{ urls: ["stun:stun.cloudflare.com:3478"] }];
    const keyId = this.env.REALTIME_TURN_TOKEN_ID;
    const apiToken = this.env.REALTIME_TURN_TOKEN_SECRET;
    if (!keyId || !apiToken) return fallback;
    try {
      const response = await fetch(
        `https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(keyId)}/credentials/generate-ice-servers`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${apiToken}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({ ttl: 86400 })
        }
      );
      if (!response.ok) return fallback;
      const data = await response.json();
      return Array.isArray(data.iceServers) && data.iceServers.length
        ? data.iceServers
        : fallback;
    } catch {
      return fallback;
    }
  }

  async ensureAlarm() {
    const alarm = await this.ctx.storage.getAlarm();
    if (alarm === null) await this.ctx.storage.setAlarm(now() + 15000);
  }

  send(ws, payload) {
    try { if (ws.readyState === 1) ws.send(JSON.stringify(payload)); } catch {}
  }

  sendTag(tag, payload) {
    const [kind, identity] = tag.split(":");
    for (const ws of this.ctx.getWebSockets()) {
      const a = this.attachment(ws);
      if (kind === "client" && a.clientId === identity) this.send(ws, payload);
      if (kind === "controller" && a.controllerId === identity) this.send(ws, payload);
    }
  }

  sendClient(clientId, payload) {
    this.sendTag("client:" + clientId, payload);
  }

  sendController(controllerId, payload) {
    this.sendTag("controller:" + controllerId, payload);
  }

  sendError(ws, code, message) {
    this.send(ws, { type: "error", code, message });
  }

  attachment(ws) {
    try { return ws.deserializeAttachment() || { role: "guest" }; } catch { return { role: "guest" }; }
  }

  setAuth(ws, auth) {
    const old = this.attachment(ws);
    ws.serializeAttachment({ ...old, ...auth });
  }

  async webSocketMessage(ws, raw) {
    if (typeof raw !== "string" || raw.length > 65536) return this.sendError(ws, "payload_too_large", "Payload exceeds 64 KB");
    let m;
    try { m = JSON.parse(raw); } catch { return this.sendError(ws, "invalid_json", "Payload must be JSON"); }
    if (!m || typeof m.type !== "string") return this.sendError(ws, "invalid_message", "Message type is required");
    const s = await this.state();
    const a = this.attachment(ws);
    const t = now();

    switch (m.type) {
      case "client.register_pairing_offer": {
        if (!validString(m.clientId) || !validCode(m.pairingCode) ||
            !Number.isFinite(m.expiresAtMs) || m.expiresAtMs <= t ||
            m.expiresAtMs - t > PAIRING_MAX_TTL_MS) {
          return this.sendError(ws, "invalid_offer", "Invalid pairing offer payload");
        }
        const device = s.devices[m.clientId];
        if (device && (a.role !== "client" || a.clientId !== m.clientId)) {
          return this.sendError(ws, "unauthorized", "Authenticate as this Client before refreshing its pairing offer");
        }
        const existing = s.offers[m.pairingCode];
        if (existing && existing.clientId !== m.clientId && existing.expiresAtMs > t) {
          return this.sendError(ws, "pairing_code_conflict", "Pairing code is already in use");
        }
        for (const [code, offer] of Object.entries(s.offers)) if (offer.clientId === m.clientId) delete s.offers[code];
        s.offers[m.pairingCode] = {
          clientId: m.clientId, expiresAtMs: m.expiresAtMs,
          pairingAcceptanceEnabled: m.pairingAcceptanceEnabled === true,
          metadata: m.metadata && typeof m.metadata === "object" && !Array.isArray(m.metadata) ? m.metadata : {}
        };
        this.setAuth(ws, { role: "client", clientId: m.clientId, currentPairingCode: m.pairingCode });
        await this.save(s);
        return this.send(ws, { type: "client.pairing_offer_registered", clientId: m.clientId, pairingCode: m.pairingCode, expiresAtMs: m.expiresAtMs });
      }
      case "controller.request_pairing": {
        if (!validString(m.controllerId) || !validCode(m.pairingCode)) return this.sendError(ws, "invalid_pairing_request", "Invalid controller pairing payload");
        const key = m.controllerId;
        const attempt = s.attempts[key];
        if (!attempt || t - attempt.start > 60000) s.attempts[key] = { count: 1, start: t };
        else if (attempt.count >= 10) return this.send(ws, { type: "controller.pairing_result", status: "rate_limited", reason: "Too many attempts, retry later" });
        else attempt.count++;
        const ip = a.ip || "unknown";
        const ipAttempt = s.ipAttempts[ip];
        if (!ipAttempt || t - ipAttempt.start > 60000) s.ipAttempts[ip] = { count: 1, start: t };
        else if (ipAttempt.count >= 30) return this.send(ws, { type: "controller.pairing_result", status: "rate_limited", reason: "Too many attempts, retry later" });
        else ipAttempt.count++;
        const offer = s.offers[m.pairingCode];
        if (!offer || offer.expiresAtMs <= t || !offer.pairingAcceptanceEnabled) {
          await this.save(s);
          return this.send(ws, { type: "controller.pairing_result", status: "failed", reason: "Pairing code invalid, expired, or disabled" });
        }
        const requestId = id();
        s.pending[requestId] = { clientId: offer.clientId, controllerId: m.controllerId, code: m.pairingCode, createdAtMs: t };
        this.sendClient(offer.clientId, { type: "client.pairing_request", requestId, controllerId: m.controllerId });
        this.setAuth(ws, { role: "pending-controller", controllerId: m.controllerId });
        await this.save(s);
        return this.send(ws, { type: "controller.pairing_pending", requestId, clientId: offer.clientId });
      }
      case "client.pairing_decision": {
        if (a.role !== "client" || !validString(m.requestId) || typeof m.approved !== "boolean") return this.sendError(ws, "invalid_pairing_decision", "Invalid pairing decision payload");
        const pending = s.pending[m.requestId];
        if (!pending || pending.clientId !== a.clientId) return this.sendError(ws, "pairing_request_not_found", "Pairing request is not active");
        const offer = s.offers[pending.code];
        if (!offer || offer.clientId !== a.clientId) {
          delete s.pending[m.requestId]; await this.save(s);
          return this.sendError(ws, "pairing_offer_not_found", "Pairing offer is no longer active");
        }
        delete s.pending[m.requestId];
        if (!m.approved) {
          await this.save(s);
          return this.sendController(pending.controllerId, { type: "controller.pairing_result", requestId: m.requestId, status: "rejected" });
        }
        delete s.offers[pending.code];
        const device = s.devices[pending.clientId] || {
          clientId: pending.clientId, clientToken: randomToken(), controllerTokens: {},
          controllerIds: [], metadata: offer.metadata, status: "online", lastSeen: t,
          clientHeartbeat: t, pendingRequests: {}, clientConnId: a.connId
        };
        const controllerToken = randomToken();
        device.metadata = offer.metadata;
        device.clientConnId = a.connId;
        device.controllerTokens[controllerToken] = pending.controllerId;
        if (!device.controllerIds.includes(pending.controllerId)) device.controllerIds.push(pending.controllerId);
        device.status = "online"; device.lastSeen = t; device.clientHeartbeat = t;
        s.devices[pending.clientId] = device;
        s.controllers[controllerToken] = { controllerId: pending.controllerId };
        this.setAuth(ws, { role: "client", clientId: pending.clientId });
        this.sendController(pending.controllerId, {
          type: "controller.pairing_result", requestId: m.requestId, status: "paired",
          clientId: pending.clientId, authToken: controllerToken, pairedAt: t, metadata: device.metadata
        });
        this.sendClient(pending.clientId, {
          type: "client.pairing_completed", requestId: m.requestId,
          controllerId: pending.controllerId, authToken: device.clientToken, pairedAt: t
        });
        await this.save(s);
        return;
      }
      case "auth.connect": {
        if (!validString(m.authToken)) return this.sendError(ws, "unauthorized", "Invalid authentication token");
        if (m.role === "client" && validString(m.clientId)) {
          const d = s.devices[m.clientId];
          if (!d || d.clientToken !== m.authToken) return this.sendError(ws, "unauthorized", "Invalid authentication token");
          if (d.clientConnId && d.clientConnId !== a.connId) {
            for (const [sessionId, session] of Object.entries(s.sessions)) {
              if (session.clientId !== m.clientId || !["pending", "approved"].includes(session.state)) continue;
              session.state = "failed";
              session.endedAtMs = t;
              session.terminationReason = "client_reconnected";
              const payload = {
                type: "session.status",
                sessionId,
                clientId: session.clientId,
                controllerId: session.controllerId,
                sessionType: session.sessionType,
                state: "failed",
                createdAtMs: session.createdAtMs,
                endedAtMs: t,
                terminationReason: "client_reconnected"
              };
              this.sendController(session.controllerId, payload);
              this.sendClient(session.clientId, { ...payload, type: "client.session_status" });
              delete s.sessions[sessionId];
            }
          }
          this.setAuth(ws, { role: "client", clientId: m.clientId });
          d.status = "online"; d.lastSeen = t; d.clientHeartbeat = t; d.clientConnId = a.connId;
          await this.save(s);
          this.broadcastDevice(s, d);
          this.send(ws, { type: "auth.connected", protocolVersion: PROTOCOL_VERSION, capabilities: SERVER_CAPABILITIES, clientId: d.clientId, role: "client", status: d.status });
          return;
        }
        if (m.role === "controller") {
          const controllerId = s.controllers[m.authToken]?.controllerId;
          if (!controllerId) return this.sendError(ws, "unauthorized", "Invalid authentication token");
          if (a.role === "controller" && a.controllerId !== controllerId) return this.sendError(ws, "unauthorized", "Controller identity mismatch");
          if (a.connId) {
            for (const [sessionId, session] of Object.entries(s.sessions)) {
              if (session.controllerId !== controllerId || session.controllerConnId === a.connId ||
                  !["pending", "approved"].includes(session.state)) continue;
              session.state = "failed";
              session.endedAtMs = t;
              session.terminationReason = "controller_reconnected";
              const payload = {
                type: "session.status", sessionId, clientId: session.clientId,
                controllerId: session.controllerId, sessionType: session.sessionType,
                state: "failed", createdAtMs: session.createdAtMs, endedAtMs: t,
                terminationReason: "controller_reconnected"
              };
              this.sendController(session.controllerId, payload);
              this.sendClient(session.clientId, { ...payload, type: "client.session_status" });
              delete s.sessions[sessionId];
            }
          }
          const tokens = [...new Set([...(a.tokens || []), m.authToken])];
          this.setAuth(ws, { role: "controller", controllerId, token: m.authToken, tokens });
          this.send(ws, { type: "controller.status", status: "connected", timestamp: t });
          const d = Object.values(s.devices).find(x => x.controllerTokens[m.authToken]);
          this.send(ws, { type: "auth.connected", protocolVersion: PROTOCOL_VERSION, capabilities: SERVER_CAPABILITIES, clientId: d?.clientId || "", role: "controller", status: "connected" });
          return;
        }
        return this.sendError(ws, "unauthorized", "Invalid authentication token");
      }
      case "heartbeat": {
        if (a.role !== "client" && a.role !== "controller") return this.sendError(ws, "unauthorized", "Authenticate before sending heartbeats");
        if (a.role === "client") {
          const d = s.devices[a.clientId];
          if (!d) return this.sendError(ws, "unauthorized", "Unknown Client");
          d.clientHeartbeat = t; d.lastSeen = t; d.status = "online";
          if (m.telemetry && typeof m.telemetry === "object" && !Array.isArray(m.telemetry)) d.metadata = { ...d.metadata, telemetry: m.telemetry };
          this.broadcastDevice(s, d);
          await this.save(s);
        }
        return this.send(ws, { type: "heartbeat.ack", timestamp: t });
      }
      case "controller.list_devices": {
        if (a.role !== "controller") return this.sendError(ws, "unauthorized", "Controller authentication required");
        const devices = Object.values(s.devices).filter(d =>
          Object.entries(d.controllerTokens).some(([token, owner]) =>
            owner === a.controllerId && (a.tokens || [a.token]).includes(token)
          )
        ).map(d => ({ clientId: d.clientId, status: d.status, lastSeen: d.lastSeen, metadata: d.metadata }));
        return this.send(ws, { type: "controller.device_list", devices });
      }
      case "controller.rename_device": {
        const d = this.authorizedDevice(s, a, m.clientId);
        if (!d) return this.sendError(ws, "unauthorized", "Not authorized for this device");
        if (!validString(m.alias, 64)) return this.sendError(ws, "invalid_alias", "Alias must be between 1 and 64 characters");
        d.metadata = { ...d.metadata, alias: m.alias }; await this.save(s);
        this.broadcastDevice(s, d);
        return this.send(ws, { type: "device.renamed", clientId: d.clientId, alias: m.alias });
      }
      case "controller.revoke_device": {
        const d = this.authorizedDevice(s, a, m.clientId);
        if (!d) return this.sendError(ws, "unauthorized", "Not authorized to revoke this device");
        for (const [tok, owner] of Object.entries(d.controllerTokens)) if (owner === a.controllerId) { delete d.controllerTokens[tok]; delete s.controllers[tok]; }
        d.controllerIds = d.controllerIds.filter(x => x !== a.controllerId);
        this.sendController(a.controllerId, { type: "device.revoked", clientId: d.clientId });
        if (!d.controllerIds.length) delete s.devices[d.clientId];
        await this.save(s);
        return;
      }
      case "controller.start_session": {
        const d = this.authorizedDevice(s, a, m.clientId);
        if (!d) return this.sendError(ws, "unauthorized", "Not authorized for this device");
        if (!SESSION_TYPES.has(m.sessionType)) return this.sendError(ws, "invalid_session_type", "Unsupported session type");
        if (d.status !== "online") return this.sendError(ws, "offline", "Client is offline");
        const activeDuplicate = Object.values(s.sessions).some(session =>
          session.clientId === d.clientId &&
          ["pending", "approved"].includes(session.state) &&
          (m.sessionType === "interaction"
            ? session.sessionType === "interaction"
            : session.controllerId === a.controllerId && session.sessionType === m.sessionType)
        );
        if (activeDuplicate) {
          return this.sendError(ws, "session_already_active", "An equivalent session is already active for this Client");
        }
        const controllerToken = Object.entries(d.controllerTokens).find(([token, owner]) =>
          owner === a.controllerId && (a.tokens || [a.token]).includes(token)
        )?.[0];
        if (!controllerToken) return this.sendError(ws, "unauthorized", "No active Controller credential for this device");
        const sessionId = id();
        const session = {
          sessionId, clientId: d.clientId, controllerId: a.controllerId,
          controllerToken, controllerConnId: a.connId,
          clientConnId: d.clientConnId, sessionType: m.sessionType,
          state: "pending", createdAtMs: t
        };
        s.sessions[sessionId] = session; await this.save(s);
        const payload = { type: "session.status", sessionId, clientId: d.clientId, controllerId: a.controllerId, sessionType: m.sessionType, state: "pending", createdAtMs: t, endedAtMs: null, terminationReason: null };
        this.sendController(a.controllerId, payload); this.sendClient(d.clientId, { ...payload, type: "client.session_status" });
        return;
      }
      case "client.session_decision": {
        const session = s.sessions[m.sessionId];
        if (!session || a.role !== "client" || session.clientId !== a.clientId || session.clientConnId !== a.connId || typeof m.approved !== "boolean") return this.sendError(ws, "unauthorized", "Invalid session decision");
        if (session.state !== "pending") return this.sendError(ws, "session_not_pending", "Session is no longer awaiting approval");
        session.state = m.approved ? "approved" : "rejected"; session.endedAtMs = m.approved ? null : t;
        session.terminationReason = m.approved ? null : "client_rejected";
        await this.save(s);
        if (m.approved && ["screen", "camera", "files"].includes(session.sessionType)) {
          const iceServers = await this.getIceServers();
          const icePayload = { type: "media.ice_servers", sessionId: session.sessionId, iceServers };
          this.sendController(session.controllerId, icePayload);
          this.sendClient(session.clientId, icePayload);
        }
        const payload = { type: "session.status", sessionId: session.sessionId, clientId: session.clientId, controllerId: session.controllerId, sessionType: session.sessionType, state: session.state, createdAtMs: session.createdAtMs, endedAtMs: session.endedAtMs, terminationReason: session.terminationReason };
        this.sendController(session.controllerId, payload); this.sendClient(session.clientId, { ...payload, type: "client.session_status" });
        if (!m.approved) { delete s.sessions[m.sessionId]; await this.save(s); }
        return;
      }
      case "client.end_session": {
        const session = s.sessions[m.sessionId];
        if (!session || a.role !== "client" || session.clientId !== a.clientId ||
            session.clientConnId !== a.connId || session.state !== "approved") {
          return this.sendError(ws, "unauthorized", "Invalid Client session termination");
        }
        session.state = "ended";
        session.endedAtMs = t;
        session.terminationReason = validString(m.reason) ? m.reason : "client_ended";
        await this.save(s);
        const payload = {
          type: "session.status", sessionId: session.sessionId, clientId: session.clientId,
          controllerId: session.controllerId, sessionType: session.sessionType,
          state: "ended", createdAtMs: session.createdAtMs, endedAtMs: t,
          terminationReason: session.terminationReason
        };
        this.sendController(session.controllerId, payload);
        this.sendClient(session.clientId, { ...payload, type: "client.session_status" });
        delete s.sessions[m.sessionId];
        await this.save(s);
        return;
      }
      case "controller.end_session": {
        const session = s.sessions[m.sessionId];
        if (!session || a.role !== "controller" || session.controllerId !== a.controllerId || !(a.tokens || [a.token]).includes(session.controllerToken) || session.controllerConnId !== a.connId) return this.sendError(ws, "unauthorized", "Invalid session termination");
        session.state = "ended"; session.endedAtMs = t; session.terminationReason = validString(m.reason) ? m.reason : "controller_ended";
        await this.save(s);
        const payload = { type: "session.status", sessionId: session.sessionId, clientId: session.clientId, controllerId: session.controllerId, sessionType: session.sessionType, state: "ended", createdAtMs: session.createdAtMs, endedAtMs: t, terminationReason: session.terminationReason };
        this.sendController(session.controllerId, payload); this.sendClient(session.clientId, { ...payload, type: "client.session_status" });
        delete s.sessions[m.sessionId]; await this.save(s); return;
      }
      case "media.signal": {
        const session = s.sessions[m.sessionId];
        if (!session || session.state !== "approved" || !["screen", "camera", "files"].includes(session.sessionType)) {
          return this.sendError(ws, "session_required", "An approved screen, camera, or file session is required");
        }
        if (!m.signal || typeof m.signal !== "object" || Array.isArray(m.signal) || JSON.stringify(m.signal).length > 48000) {
          return this.sendError(ws, "invalid_signal", "Invalid or oversized media signaling payload");
        }
        const payload = { type: "media.signal", sessionId: session.sessionId, fromRole: a.role, signal: m.signal };
        if (a.role === "client" && a.clientId === session.clientId && a.connId === session.clientConnId) {
          for (const peer of this.ctx.getWebSockets()) {
            const peerAuth = this.attachment(peer);
            if (peerAuth.role === "controller" && peerAuth.controllerId === session.controllerId &&
                (peerAuth.tokens || [peerAuth.token]).includes(session.controllerToken)) this.send(peer, payload);
          }
          return;
        }
        if (a.role === "controller" && a.controllerId === session.controllerId &&
            session.controllerConnId === a.connId && (a.tokens || [a.token]).includes(session.controllerToken)) {
          this.sendClient(session.clientId, payload);
          return;
        }
        return this.sendError(ws, "unauthorized", "Not a participant in this media session");
      }
      case "controller.request_snapshot":
      case "controller.command": {
        const d = this.authorizedDevice(s, a, m.clientId);
        if (!d) return this.sendError(ws, "unauthorized", "Not authorized for this device");
        if (d.status !== "online") return this.sendError(ws, "offline", "Client is offline");
        if (m.type === "controller.command") {
          const allowed = Object.values(s.sessions).some(x => x.clientId === d.clientId && x.controllerId === a.controllerId && (a.tokens || [a.token]).includes(x.controllerToken) && x.sessionType === "interaction" && x.state === "approved");
          if (!allowed) return this.sendError(ws, "session_required", "An approved interaction session is required");
          if (!validString(m.command)) return this.sendError(ws, "invalid_command", "Command is required");
        }
        const requestId = id();
        d.pendingRequests = d.pendingRequests || {};
        d.pendingRequests[requestId] = { controllerId: a.controllerId, controllerToken: a.token };
        await this.save(s);
        if (m.type === "controller.command") this.sendClient(d.clientId, { type: "client.command", requestId, command: m.command, parameters: m.parameters && typeof m.parameters === "object" && !Array.isArray(m.parameters) ? m.parameters : {} });
        else this.sendClient(d.clientId, { type: "client.snapshot_request", requestId, requestedAt: t });
        return;
      }
      case "client.result": {
        if (a.role !== "client" || !validString(m.requestId)) return this.sendError(ws, "unauthorized", "Invalid client response");
        const d = s.devices[a.clientId]; const target = d?.pendingRequests?.[m.requestId];
        if (!target || !s.controllers[target.controllerToken]) return this.sendError(ws, "request_not_found", "Request is no longer active");
        delete d.pendingRequests[m.requestId]; await this.save(s);
        this.sendController(target.controllerId, { type: "device.result", clientId: a.clientId, requestId: m.requestId, result: m.result && typeof m.result === "object" ? m.result : {}, error: m.error || null });
        return;
      }
      default:
        return this.sendError(ws, "unknown_message_type", "Unsupported message type");
    }
  }

  authorizedDevice(s, a, clientId) {
    if (a.role !== "controller" || !validString(clientId)) return null;
    const d = s.devices[clientId];
    const tokens = a.tokens || [a.token];
    return d && Object.entries(d.controllerTokens).some(([token, owner]) =>
      owner === a.controllerId && tokens.includes(token)
    ) ? d : null;
  }

  broadcastDevice(s, d) {
    const payload = { type: "device.status", clientId: d.clientId, status: d.status, lastSeen: d.lastSeen, metadata: d.metadata };
    for (const ws of this.ctx.getWebSockets()) {
      const a = this.attachment(ws);
      if (a.role === "controller" && Object.entries(d.controllerTokens).some(([token, owner]) =>
        owner === a.controllerId && (a.tokens || [a.token]).includes(token)
      )) this.send(ws, payload);
    }
  }

  async webSocketClose(ws) {
    const a = this.attachment(ws);
    const s = await this.state();

    // A Controller disconnect ends sessions owned by that live connection.
    // This mirrors the local signaling server and prevents an orphaned
    // approved session from blocking a fresh Client consent request.
    if (a.role === "controller" && a.controllerId) {
      const tokens = new Set(a.tokens || [a.token].filter(Boolean));
      let changed = false;
      for (const [sessionId, session] of Object.entries(s.sessions)) {
        if (session.controllerId !== a.controllerId || session.controllerConnId !== a.connId ||
            (tokens.size && !tokens.has(session.controllerToken)) ||
            !["pending", "approved"].includes(session.state)) continue;
        session.state = "ended";
        session.endedAtMs = now();
        session.terminationReason = "controller_disconnected";
        const payload = {
          type: "session.status",
          sessionId,
          clientId: session.clientId,
          controllerId: session.controllerId,
          sessionType: session.sessionType,
          state: "ended",
          createdAtMs: session.createdAtMs,
          endedAtMs: session.endedAtMs,
          terminationReason: session.terminationReason
        };
        this.sendController(session.controllerId, payload);
        this.sendClient(session.clientId, { ...payload, type: "client.session_status" });
        delete s.sessions[sessionId];
        changed = true;
      }
      if (changed) await this.save(s);
      return;
    }

    if (a.role !== "client" || !a.clientId) return;

    // A Client socket close may be a transient Android/network handoff.
    // Presence is changed only after the heartbeat timeout in alarm().
    const d = s.devices[a.clientId];
    if (d && d.clientConnId === a.connId) {
      console.log(JSON.stringify({
        event: "presence.socket_closed",
        clientId: a.clientId,
        at: now()
      }));
    }
  }

  async webSocketError(ws) {
    // Errors follow the same heartbeat-timeout policy as normal socket closes.
    await this.webSocketClose(ws);
  }

  async alarm() {
    const s = await this.state(); const t = now();
    for (const [code, offer] of Object.entries(s.offers)) if (offer.expiresAtMs <= t) delete s.offers[code];
    for (const [requestId, pending] of Object.entries(s.pending)) {
      if (t - pending.createdAtMs > PAIRING_MAX_TTL_MS) {
        delete s.pending[requestId];
        this.sendController(pending.controllerId, { type: "controller.pairing_result", requestId, status: "expired", reason: "Pairing approval timeout" });
      }
    }
    for (const d of Object.values(s.devices)) {
      if (d.status === "online" && t - d.clientHeartbeat > HEARTBEAT_TIMEOUT_MS) {
        d.status = "offline"; d.lastSeen = t; this.broadcastDevice(s, d);
        for (const [sid, session] of Object.entries(s.sessions)) if (session.clientId === d.clientId && ["pending", "approved"].includes(session.state)) {
          session.state = "failed"; session.endedAtMs = t; session.terminationReason = "client_heartbeat_timeout";
          const payload = { type: "session.status", sessionId: sid, clientId: session.clientId, controllerId: session.controllerId, sessionType: session.sessionType, state: "failed", createdAtMs: session.createdAtMs, endedAtMs: t, terminationReason: session.terminationReason };
          this.sendController(session.controllerId, payload); this.sendClient(session.clientId, { ...payload, type: "client.session_status" }); delete s.sessions[sid];
        }
      }
    }
    for (const [sid, session] of Object.entries(s.sessions)) if (session.state === "pending" && t - session.createdAtMs > SESSION_PENDING_TTL_MS) {
      session.state = "failed"; session.endedAtMs = t; session.terminationReason = "approval_timeout";
      const payload = { type: "session.status", sessionId: sid, clientId: session.clientId, controllerId: session.controllerId, sessionType: session.sessionType, state: "failed", createdAtMs: session.createdAtMs, endedAtMs: t, terminationReason: "approval_timeout" };
      this.sendController(session.controllerId, payload); this.sendClient(session.clientId, { ...payload, type: "client.session_status" }); delete s.sessions[sid];
    }
    for (const [key, attempt] of Object.entries(s.attempts)) if (t - attempt.start > 60000) delete s.attempts[key];
    for (const [key, attempt] of Object.entries(s.ipAttempts)) if (t - attempt.start > 60000) delete s.ipAttempts[key];
    await this.save(s);
    await this.ctx.storage.setAlarm(t + 15000);
  }
}

