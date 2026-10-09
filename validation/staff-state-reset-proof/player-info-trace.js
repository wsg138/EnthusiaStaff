'use strict'

const { firstPresent, profileId } = require('./packet-fields')

function normalizeUuid (value) {
  return String(value || '').replace(/-/g, '').toLowerCase()
}

function ownUuid (bot) {
  return normalizeUuid(bot.player?.uuid || bot.entity?.uuid || bot._client?.uuid)
}

function traceOwnPlayerInfo (bot, packet, log) {
  const entries = firstPresent(packet, ['data', 'entries']) || []
  const self = ownUuid(bot)
  if (!self || !Array.isArray(entries)) return
  const entry = entries.find(candidate => normalizeUuid(profileId(candidate)) === self)
  if (!entry) return

  const gameMode = firstPresent(entry, ['gamemode', 'gameMode', 'game_mode'])
  const listed = firstPresent(entry, ['listed', 'isListed'])
  log(
    'SELF_PLAYER_INFO|packet=player_info|gameMode=' +
    String(gameMode === undefined ? 'unknown' : gameMode) +
    '|listed=' +
    String(listed === undefined ? 'unknown' : listed)
  )
}

module.exports = { traceOwnPlayerInfo }
