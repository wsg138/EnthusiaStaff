'use strict'

/**
 * An automated Java *protocol* client, not an interactive human Java client.
 * No clicks, chat commands, sanctions, approvals or production network access.
 */
const mineflayer = require('mineflayer')
const port = Number(process.argv[2])
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw Error('Invalid loopback port')

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port,
  username: 'GuiSmoke',
  auth: 'offline',
  version: '26.2',
  physicsEnabled: false
})

let completed = false
function fail (reason) {
  console.log('GUI_PACKET_FAIL=' + String(reason))
  process.exitCode = 2
  bot.quit()
}
bot.on('spawn', () => console.log('GUI_CLIENT_SPAWN=true'))
bot.on('windowOpen', window => {
  console.log('GUI_PACKET_OPEN|inventoryStart=' + String(window.inventoryStart) +
    '|slotCount=' + String(window.slots.length) + '|title=' + String(window.title))
  if (window.inventoryStart !== 54) {
    fail('top inventory slot count not 54')
    return
  }
  completed = true
  console.log('GUI_PACKET_54_SLOTS=true')
  bot.quit()
})
bot.on('kicked', reason => console.log('GUI_CLIENT_KICKED=' + String(reason)))
bot.on('error', error => {
  console.log('GUI_CLIENT_ERROR=' + String(error))
  process.exitCode = 2
})
bot.on('end', () => {
  if (!completed) process.exitCode = 2
  console.log('GUI_CLIENT_END|completed=' + completed)
})
setTimeout(() => {
  if (!completed) fail('timeout')
}, 60000).unref()
