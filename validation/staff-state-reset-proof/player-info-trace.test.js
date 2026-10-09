'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const { traceOwnPlayerInfo } = require('./player-info-trace')
const self = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
function trace (bot, packet) {
  const logs = []
  traceOwnPlayerInfo(bot, packet, line => logs.push(line))
  return logs
}
test('Spectator update resolves normalized own UUID through entry profile', () => {
  assert.deepEqual(trace({_client:{uuid:self}}, {entries:[
    {profile:{id:self.replace(/-/g, '').toUpperCase()}, game_mode:3, isListed:true}
  ]}), ['SELF_PLAYER_INFO|packet=player_info|gameMode=3|listed=true'])
})
test('other player spectator update does not establish own spectator state', () => {
  assert.deepEqual(trace({_client:{uuid:self}}, {data:[{uuid:'other', gamemode:3}]}), [])
})
test('zero game mode and false listing remain observed values', () => {
  assert.deepEqual(trace({player:{uuid:self}}, {data:[{uuid:self, gamemode:0, listed:false}]}),
    ['SELF_PLAYER_INFO|packet=player_info|gameMode=0|listed=false'])
})
test('missing identity never treats unknown packet profiles as self', () => {
  assert.deepEqual(trace({_client:{}}, {data:[{gameMode:3}]}), [])
})
test('malformed entry collection fails closed without false protocol evidence', () => {
  assert.deepEqual(trace({_client:{uuid:self}}, {data:{uuid:self, gameMode:3}}), [])
})
test('partial own update reports missing fields instead of inventing spectator state', () => {
  assert.deepEqual(trace({entity:{uuid:self}}, {entries:[{profileId:self}]}),
    ['SELF_PLAYER_INFO|packet=player_info|gameMode=unknown|listed=unknown'])
})

test('partial player identity falls back to the entity UUID before the client', () => {
  assert.deepEqual(trace({player:{}, entity:{uuid:self}, _client:{uuid:'other'}},
    {entries:[{uuid:self, gamemode:3}]}),
    ['SELF_PLAYER_INFO|packet=player_info|gameMode=3|listed=unknown'])
})
