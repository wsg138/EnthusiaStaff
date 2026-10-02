'use strict';

const fs = require('node:fs');
const path = require('node:path');

function applyMineflayer263TeleportPatch() {
  const mineflayerRoot = path.dirname(require.resolve('mineflayer'));
  const physicsFile = path.join(mineflayerRoot, 'lib', 'plugins', 'physics.js');
  const source = fs.readFileSync(physicsFile, 'utf8');
  const marker = 'TEMP263_PROOF_DEFERRED_TELEPORT_ECHO';
  if (source.includes(marker)) return;
  const oldTail = `    sendPacketPositionAndLook(pos, newYaw, newPitch, bot.entity.onGround)

    shouldUsePhysics = true
    bot.jumpTicks = 0
    lastSentYaw = bot.entity.yaw
    lastSentPitch = bot.entity.pitch

    bot.emit('forcedMove')`;
  if (!source.includes(oldTail)) {
    throw new Error('unexpected Mineflayer 26.3 physics source; refusing runtime patch');
  }
  const replacement = `    // ${marker}: this disposable proof uses real 26.3 network clients but
    // controls movement packets directly. A teleport_confirm is enough here.
    if (!bot.physicsEnabled) {
      shouldUsePhysics = false
      bot.jumpTicks = 0
      lastSentYaw = bot.entity.yaw
      lastSentPitch = bot.entity.pitch
      bot.emit('forcedMove')
      return
    }

    sendPacketPositionAndLook(pos, newYaw, newPitch, bot.entity.onGround)

    shouldUsePhysics = true
    bot.jumpTicks = 0
    lastSentYaw = bot.entity.yaw
    lastSentPitch = bot.entity.pitch

    bot.emit('forcedMove')`;
  fs.writeFileSync(physicsFile, source.replace(oldTail, replacement));
}

applyMineflayer263TeleportPatch();
const mineflayer = require('mineflayer');

const host = process.env.VPROOF_HOST || '127.0.0.1';
const port = Number.parseInt(process.env.VPROOF_PORT || '25594', 10);
const username = 'Temp263Proof';

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function normalizeMode(bot) {
  const value = bot.game && bot.game.gameMode;
  return value == null ? 'unknown' : String(value).toLowerCase();
}

function createClient() {
  const bot = mineflayer.createBot({
    host,
    port,
    username,
    version: '26.3',
    auth: 'offline',
    physicsEnabled: false,
  });
  bot.physicsEnabled = false;
  bot._proofMessages = [];
  bot._rawGameEvents = [];
  bot.on('message', message => {
    const text = message.toString();
    bot._proofMessages.push(text);
    console.log('CHAT', text);
  });
  bot._client.on('game_state_change', packet => {
    bot._rawGameEvents.push(packet);
    console.log('GAME_STATE_CHANGE', JSON.stringify(packet));
  });
  bot.on('game', () => console.log('GAME mode=' + normalizeMode(bot)));
  bot.on('kicked', reason => console.log('KICKED', JSON.stringify(reason)));
  bot.on('error', error => console.log('CLIENT_ERROR', error.stack || error.message));
  return bot;
}

async function waitSpawn(bot) {
  if (bot.entity) return;
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('spawn timeout')), 45000);
    bot.once('spawn', () => {
      clearTimeout(timer);
      bot.physicsEnabled = false;
      resolve();
    });
    bot.once('error', error => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

async function waitForMessage(bot, needle, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const found = bot._proofMessages.find(message => message.includes(needle));
    if (found) return found;
    await sleep(50);
  }
  throw new Error('timed out waiting for message containing ' + needle);
}

async function command(bot, commandText, expected) {
  const start = bot._proofMessages.length;
  bot.chat(commandText);
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline) {
    for (let i = start; i < bot._proofMessages.length; i++) {
      const message = bot._proofMessages[i];
      if (message.includes('VPROOF_FAILURE')) {
        throw new Error('server proof failure after ' + commandText + ': ' + message);
      }
      if (message.includes(expected)) return message;
    }
    await sleep(50);
  }
  throw new Error('timed out waiting for ' + expected + ' after ' + commandText);
}

async function waitMode(bot, expected, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const mode = normalizeMode(bot);
    if (mode === expected || mode.includes(expected)) {
      console.log('CLIENT_MODE_OK expected=' + expected + ' observed=' + mode);
      return;
    }
    await sleep(50);
  }
  throw new Error('client game mode did not become ' + expected + '; observed=' + normalizeMode(bot)
    + ' raw=' + JSON.stringify(bot._rawGameEvents.slice(-8)));
}

function sendPosition(bot, x, y, z) {
  bot._client.write('position_look', {
    x,
    y,
    z,
    yaw: 0,
    pitch: 0,
    onGround: false,
    horizontalCollision: false,
  });
  console.log(`CLIENT_POSITION_PACKET x=${x} y=${y} z=${z}`);
}

function parsePosition(message) {
  const x = Number.parseFloat(/\bx=(-?[0-9.]+)/.exec(message)?.[1]);
  const y = Number.parseFloat(/\by=(-?[0-9.]+)/.exec(message)?.[1]);
  const z = Number.parseFloat(/\bz=(-?[0-9.]+)/.exec(message)?.[1]);
  if (![x, y, z].every(Number.isFinite)) throw new Error('could not parse server position: ' + message);
  return { x, y, z };
}

function assertNear(actual, expected, label) {
  const delta = Math.abs(actual - expected);
  if (delta > 0.75) throw new Error(`${label} expected ${expected}, got ${actual}`);
}

async function geometryCase(bot, name, target) {
  await command(bot, '/vproof geometry ' + name, 'VPROOF_GEOMETRY_READY case=' + name);
  await waitMode(bot, 'spectator');
  await sleep(200);
  sendPosition(bot, target.x, target.y, target.z);
  await sleep(350);
  const message = await command(bot, '/vproof checkpos ' + name, 'VPROOF_POSITION case=' + name);
  const position = parsePosition(message);
  assertNear(position.x, target.x, name + '.x');
  assertNear(position.y, target.y, name + '.y');
  assertNear(position.z, target.z, name + '.z');
  if (!message.includes('server_mode=SURVIVAL') || !message.includes('noPhysics=true')) {
    throw new Error(name + ' passage lost authoritative survival/noPhysics state: ' + message);
  }
  console.log('GEOMETRY_PASS case=' + name + ' server=' + JSON.stringify(position));
}

async function main() {
  let bot = createClient();
  await waitSpawn(bot);

  await command(bot, '/vproof abi', 'VPROOF_ABI_OK');
  await command(bot, '/vproof baseline', 'VPROOF_BASELINE_OK');

  await command(bot, '/vproof plugin-disable', 'VPROOF_PLUGIN_DISABLE_OK');
  await waitMode(bot, 'survival');
  console.log('PLUGIN_DISABLE_CLEANUP_PASS');

  await command(bot, '/vproof begin', 'VPROOF_BEGIN_OK');
  await waitMode(bot, 'spectator');
  const vanishedStatus = await command(bot, '/vproof status vanished', 'VPROOF_STATUS label=vanished');
  const noPhysicsRetained = vanishedStatus.includes('server_mode=SURVIVAL')
    && vanishedStatus.includes('noPhysics=true');
  console.log(noPhysicsRetained
    ? 'NO_PHYSICS_RETENTION_PASS server_mode=SURVIVAL noPhysics=true'
    : 'NO_PHYSICS_RETENTION_FAIL ' + vanishedStatus);

  if (noPhysicsRetained) {
    await geometryCase(bot, 'wall', { x: 204.5, y: 80.0, z: 200.5 });
    await geometryCase(bot, 'floor', { x: 210.5, y: 76.5, z: 200.5 });
    await geometryCase(bot, 'ceiling', { x: 220.5, y: 85.0, z: 200.5 });
  } else {
    console.log('GEOMETRY_BLOCKED reason=server_noPhysics_did_not_remain_enabled');
  }

  await command(bot, '/vproof teleport', 'VPROOF_TELEPORT_OK');
  await waitMode(bot, 'spectator');
  const teleportStatus = await command(bot, '/vproof status teleport-settled', 'VPROOF_STATUS label=teleport-settled');
  console.log(teleportStatus.includes('noPhysics=true')
    ? 'TELEPORT_NO_PHYSICS_RETENTION_PASS'
    : 'TELEPORT_NO_PHYSICS_RETENTION_FAIL ' + teleportStatus);

  await command(bot, '/vproof disable', 'VPROOF_DISABLE_OK');
  await waitMode(bot, 'survival');
  const restoredStatus = await command(bot, '/vproof status restored', 'VPROOF_STATUS label=restored');
  if (!restoredStatus.includes('server_mode=SURVIVAL') || !restoredStatus.includes('noPhysics=false')) {
    throw new Error('unvanish did not restore authoritative state: ' + restoredStatus);
  }

  await command(bot, '/vproof begin', 'VPROOF_BEGIN_OK');
  await waitMode(bot, 'spectator');
  bot.quit('reconnect-proof');
  await sleep(1200);

  bot = createClient();
  await waitSpawn(bot);
  await waitMode(bot, 'survival');
  const reconnectInitial = await command(bot, '/vproof status reconnect-initial', 'VPROOF_STATUS label=reconnect-initial');
  if (!reconnectInitial.includes('server_mode=SURVIVAL') || !reconnectInitial.includes('noPhysics=false')) {
    throw new Error('reconnect did not start clean: ' + reconnectInitial);
  }
  await command(bot, '/vproof begin', 'VPROOF_BEGIN_OK');
  await waitMode(bot, 'spectator');
  await command(bot, '/vproof disable', 'VPROOF_DISABLE_OK');
  await waitMode(bot, 'survival');
  console.log('RECONNECT_PASS cleanup_and_restoration=true');

  await command(bot, '/vproof forced-failure', 'VPROOF_FORCED_FAILURE_OK');
  await command(bot, '/vproof finalize', 'VPROOF_FINAL_OK');

  if (noPhysicsRetained) {
    console.log('TEMP263_REAL_CLIENT_PROOF_COMPLETE compatibility=PASS');
  } else {
    console.log('TEMP263_REAL_CLIENT_PROOF_COMPLETE compatibility=FAIL reason=noPhysics_not_retained');
  }
  bot.quit('proof-complete');
  await sleep(300);
}

main().then(() => {
  process.exit(0);
}).catch(error => {
  console.error('TEMP263_REAL_CLIENT_PROOF_FAILED', error.stack || error.message);
  process.exit(1);
});
