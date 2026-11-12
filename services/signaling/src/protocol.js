'use strict';

const PROTOCOL_VERSION = 2;

const SERVER_CAPABILITIES = Object.freeze([
  'pairing',
  'token-authentication',
  'presence',
  'telemetry',
  'device-commands',
  'multi-device-routing',
  'session-authorization',
  'media-signaling',
  'turn-credentials',
  'remote-interaction'
]);

module.exports = { PROTOCOL_VERSION, SERVER_CAPABILITIES };
