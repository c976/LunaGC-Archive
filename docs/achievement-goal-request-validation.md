# Validate complete achievement-category claim requests

The command `28295` mapping and repeated `uint32` field `14` already exist upstream. This change validates the whole request before calling the existing synchronized category-reward boundary; it does not change mapping, eligibility, delivery or persistence.

Packed and unpacked IDs and valid unknown fields remain supported. Empty/missing IDs, duplicates, invalid wire types, truncated fields, unmatched groups, out-of-range/overlong varints, requests above 4,096 bytes and batches above 64 IDs fail with the existing error response before rewards are attempted. The parser never accepts an ID or packed length produced by truncating an oversized varint.

`AchievementGoalRequestProtocolTest` checks real dispatch, the existing generated descriptor, synthetic serialization, malformed framing, bounds and missing-player behavior with a recording reward boundary. No client binaries, native instruction excerpts, account exports or live credentials are included. Local client category claiming/ownership persistence was checked separately on the preserved deployment; refusal/duplicate controls are offline tests.
