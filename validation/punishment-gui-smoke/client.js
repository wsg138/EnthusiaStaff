'use strict'

/**
 * An automated Java *protocol* client, not an interactive human Java client.
 * Only two safe UI clicks (Refresh and Close); no target selection,
 * chat commands, sanctions, approvals or production network access.
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

// Only picker slots 49 (CLOCK / Refresh) and 51 (BARRIER / Close) are allowed.
// Never select a player, reason, approval, or sanction.
const REFRESH_SLOT = 49
const CLOSE_SLOT = 51
let completed = false
let ended = false
let pickerOpens = 0
let closeRequested = false

function fail (reason) {
  if (ended) return
  ended = true
  console.log('GUI_PACKET_FAIL=' + String(reason))
  process.exitCode = 2
  bot.quit()
}

function clickSafeControl (window, slot, itemName, marker) {
  if (ended) return
  const item = window.slots[slot]
  if (!item || item.name !== itemName) {
    fail('unexpected item at ' + slot + ' expected ' + itemName +
      ' got ' + String(item?.name))
    return
  }
  console.log(marker)
  // The click can resolve after the server replaces/closes its inventory.
  // The actual proof is a subsequent window event, not clickWindow's return.
  Promise.resolve(bot.clickWindow(slot, 0, 0)).catch(error => {
    console.log('GUI_CLICK_RESULT=' + String(error))
  })
}

bot.on('spawn', () => console.log('GUI_CLIENT_SPAWN=true'))
bot.on('messagestr', message => {
  console.log('GUI_CLIENT_MESSAGE=' + String(message).replace(/[\r\n]/g, ' ').slice(0, 350))
})
bot.on('windowOpen', window => {
  if (ended) return
  const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
  console.log('GUI_PACKET_OPEN|inventoryStart=' + String(window.inventoryStart) +
    '|slotCount=' + String(window.slots.length) + '|title=' + title)
  if (!/punish|select player/i.test(title)) return
  if (window.inventoryStart !== 54) {
    fail('punishment picker top inventory slot count not 54')
    return
  }
  pickerOpens++
  console.log('GUI_PACKET_54_SLOTS=true')
  if (pickerOpens === 1) {
    setTimeout(() => clickSafeControl(window, REFRESH_SLOT, 'clock',
      'GUI_CLIENT_REFRESH_CLICK=true'), 120)
  } else if (pickerOpens === 2) {
    console.log('GUI_CLIENT_REFRESHED_PICKER=true')
    setTimeout(() => {
      closeRequested = true
      clickSafeControl(window, CLOSE_SLOT, 'barrier', 'GUI_CLIENT_CLOSE_CLICK=true')
    }, 120)
  } else {
    fail('unexpected third picker opening')
  }
})
bot.on('windowClose', () => {
  if (!ended && closeRequested && pickerOpens === 2) {
    completed = true
    ended = true
    console.log('GUI_CLIENT_REFRESH_AND_CLOSE=true')
    bot.quit()
  }
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
