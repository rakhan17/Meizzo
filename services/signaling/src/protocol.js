'use strict';

const PROTOCOL_VERSION = 2;

const SERVER_CAPABILITIES = Object.freeze([
  'pairing',
  'token-authentication',
  'presence',
  'telemetry',
  'device-commands',
  'multi-device-routing'
]);

module.exports = { PROTOCOL_VERSION, SERVER_CAPABILITIES };
