# 7.1 achievement category reward response

This correction covers `TakeAchievementGoalRewardRsp`, the achievement category/goal reward response, not the individual achievement response. It maps response command **22054** through the canonical compatibility override with this functional wire contract:

- `retcode`: int32, field **7**, ordinary signed varint encoding (not ZigZag).
- `id_list`: repeated uint32, field **8**, accepting packed and unpacked encodings.
- `item_list`: repeated ItemParam messages, field **9**.
- ItemParam: `item_id:uint32` at field **1**, `count:uint32` at field **2**, both ordinary varints.

## Provenance

The inspected build is `GenshinImpact.exe`, 444260776 bytes, SHA-256 `08a3086d5f3fe695f01dab61efa42e442006b18e5e475b2520df356f6a073b7d`. Static inspection links the response command to category reward handling and verifies the field tags, nested item schema, and unchanged value encoding on the normal native path. This is an interoperability correction based on static inspection, not a clean-room implementation or evidence of successful runtime client acceptance.

## Regression and limits

`PacketTakeAchievementGoalRewardRspTest` checks the generated descriptor, actual success/error packet bytes, nested items, packed/unpacked IDs, signed int32 encoding, response opcode, and unchanged unmapped request opcode. The synthetic success fixture `42 01 01 4a 05 08 c9 01 10 05` represents goal ID 1 and item 201 count 5; the existing already-taken error is `38 f5 04` (retcode 629). These are schema-derived fixtures, not captured successful category claims.

The goal request is mapped too: `TakeAchievementGoalRewardReq` is command **28295** with `id_list` on repeated uint32 field **14**, from kitkat-multiverse/genshin-protocol 7.1.0. Reward eligibility, delivery and persistence are unchanged.
