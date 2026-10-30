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

// Strict allowlist: 49 Refresh, 51 Close, then 9 for the single disposable
// offline target after its identity and material are verified on both sides.
// Never click a category, reason, review or sanction action.
const REFRESH_SLOT = 49
const CLOSE_SLOT = 51
const TARGET_HEAD_SLOT = 9
const TEST_TARGET = 'GuiTarget'
let completed = false
let ended = false
let pickerOpens = 0
let closeRequested = false
let targetBot
let targetConnected = false

function fail (reason) {
  if (ended) return
  ended = true
  console.log('GUI_PACKET_FAIL=' + String(reason))
  process.exitCode = 2
  bot.quit()
  targetBot?.quit()
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
  Promise.resolve(bot.clickWindow(slot, 0, 0)).catch(error => {
    console.log('GUI_CLICK_RESULT=' + String(error))
  })
}

bot.on('spawn', () => {
  console.log('GUI_CLIENT_SPAWN=true')
  // A separate throwaway online player is required to populate exactly one
  // target head in the picker. Never impersonate real accounts or connect remote.
  targetBot = mineflayer.createBot({
    host: '127.0.0.1', port, username: TEST_TARGET, auth: 'offline',
    version: '26.2', physicsEnabled: false
  })
  targetBot.on('spawn', () => {
    targetConnected = true
    console.log('GUI_DISPOSABLE_TARGET_SPAWN=true')
  })
  targetBot.on('kicked', reason => fail('disposable target kicked: ' + reason))
  targetBot.on('error', error => fail('disposable target error: ' + error))
  targetBot.on('end', () => {
    if (!completed && !ended) fail('disposable target disconnected')
  })
})

bot.on('messagestr', message => {
  console.log('GUI_CLIENT_MESSAGE=' + String(message).replace(/[\r\n]/g, ' ').slice(0, 350))
})
bot.on('windowOpen', window => {
  if (ended) return
  const title = typeof window.title === 'string' ? window.title : JSON.stringify(window.title)
  console.log('GUI_PACKET_OPEN|inventoryStart=' + String(window.inventoryStart) +
    '|slotCount=' + String(window.slots.length) + '|title=' + title)
  if (title.includes('Punish') && title.includes(TEST_TARGET)) {
    if (pickerOpens !== 3 || window.inventoryStart !== 54 ||
        window.slots[13]?.name !== 'oak_sign') {
      fail('categories window missing guide sign or wrong state')
      return
    }
    completed = true
    ended = true
    console.log('GUI_CLIENT_TARGET_CATEGORIES_OPEN=true')
    bot.quit()
    targetBot?.quit()
    return
  }
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
  } else if (pickerOpens === 3) {
    if (!targetConnected) {
      fail('disposable target not connected before target selection')
      return
    }
    console.log('GUI_CLIENT_TARGET_PICKER_REOPEN=true')
    setTimeout(() => clickSafeControl(window, TARGET_HEAD_SLOT, 'player_head',
      'GUI_CLIENT_TARGET_HEAD_CLICK=true'), 120)
  } else {
    fail('unexpected fourth picker opening')
  }
})
bot.on('windowClose', () => {
  if (!ended && closeRequested && pickerOpens === 2) {
    closeRequested = false
    console.log('GUI_CLIENT_REFRESH_AND_CLOSE=true')
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
