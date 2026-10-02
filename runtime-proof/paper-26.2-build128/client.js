'use strict'

const fs = require('fs')
const mineflayer = require('mineflayer')

const port = Number(process.argv[2])
const output = process.argv[3]
let session = 0
let reconnectExpected = false
let finished = false
const seen = new Set()

function log (line) {
  fs.appendFileSync(output, line + '\n')
  console.log(line)
}

function sleep (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function connect () {
  session++
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port,
    username: 'RuntimeProof',
    auth: 'offline',
    version: '26.2',
    physicsEnabled: false
  })

  bot._client.on('game_state_change', packet => {
    log(`GAME_STATE|session=${session}|reason=${String(packet.reason)}|gameMode=${String(packet.gameMode)}`)
  })

  bot.once('spawn', () => {
    log(`SPAWN|session=${session}|mode=${bot.game.gameMode}|x=${bot.entity.position.x}|y=${bot.entity.position.y}|z=${bot.entity.position.z}`)
  })

  async function move (x, y, z, label) {
    if (!bot.entity || bot._client.state !== 'play') return
    bot.entity.position.set(x, y, z)
    bot._client.write('position', {
      x,
      y,
      z,
      onGround: false,
      flags: { onGround: false, hasHorizontalCollision: false }
    })
    log(`CLIENT_MOVE|${label}|x=${x}|y=${y}|z=${z}|mode=${bot.game.gameMode}`)
  }

  async function handleMarker (marker, coordinates) {
    if (seen.has(`${session}:${marker}`)) return
    seen.add(`${session}:${marker}`)
    log(`MARKER|${marker}|session=${session}|mode=${bot.game.gameMode}`)

    if (marker === 'ACTIVE') {
      const [x, y, z] = coordinates
      await sleep(150)
      await move(x + 2.0, y, z, 'WALL')
      await sleep(1000)
      await move(x + 2.0, y - 2.5, z, 'FLOOR')
      await sleep(1000)
      await move(x + 2.0, y + 3.5, z, 'CEILING')
    }
    if (marker === 'RECONNECT_ACTIVE') {
      reconnectExpected = true
    }
    if (marker === 'DISABLE_CLEANED') {
      finished = true
    }
  }

  function inspectMessage (text) {
    const match = String(text).match(/RTPROOF:([A-Z_]+)(?::(-?\d+(?:\.\d+)?):(-?\d+(?:\.\d+)?):(-?\d+(?:\.\d+)?))?/)
    if (match) {
      const coordinates = match.slice(2, 5).map(value => value == null ? NaN : Number(value))
      void handleMarker(match[1], coordinates)
    }
  }

  bot.on('messagestr', inspectMessage)
  bot.on('message', message => inspectMessage(message.toString()))
  bot.on('kicked', reason => log(`KICKED|session=${session}|reason=${String(reason)}`))
  bot.on('error', error => log(`ERROR|session=${session}|error=${error.stack || error}`))
  bot.on('end', reason => {
    log(`END|session=${session}|reason=${String(reason)}|reconnectExpected=${reconnectExpected}|finished=${finished}`)
    if (reconnectExpected && session === 1 && !finished) {
      reconnectExpected = false
      setTimeout(connect, 1000)
    }
  })
}

connect()

setTimeout(() => {
  if (!finished) {
    log('CLIENT_TIMEOUT=true')
    process.exitCode = 2
  }
}, 90000)
