# TPS weapons (7.1)

The 7.x third-person shooter mode: guns and grenades an avatar wears next to its normal weapon.
This folder has the 7.1 protocol recovered for it (`tps_7.1.proto`) and the tools that recovered it.

## What the server does

| | |
|---|---|
| Items | `TpsWeaponExcelConfigData` items (224001-224008) load as `ITEM_TPS_WEAPON`, one of each per player, sent as `Item.tps_weapon` with their accessories and affixes. |
| Gun models | An avatar's worn TPS weapons get weapon entities (gadgets 50016001-50016008) and are listed in `SceneAvatarInfo.tps_weapon_list` (31) and `AvatarInfo.tps_weapon_list` (37). |
| Abilities | Each worn weapon's affix (and unlocked accessory affixes) opens its `EquipAffixExcelConfigData.openConfig`, e.g. `TPS_Weapon_IceGun`, which adds `Avatar_TPS_IceGun_PressAim`, `Avatar_TPS_Ammo_Manager`, `Avatar_TPS_Ammo_Reload` and the rest to the avatar. `recalcStats` sends them with `AbilityChangeNotify`. |
| Switching | `WearTpsEquipReq` (20756) replaces the avatar's whole TPS list, at most 2 guns and 1 grenade (`CONST_VALUE_TPS_SLOT_WEAR_NUM_LIMIT`). A weapon worn by another avatar moves. Replies `WearTpsEquipRsp` (25902) and broadcasts `TpsEquipChangeNotify` (21312). |
| TPS traveler | The TPS dungeons (51334, 51336 shooting range, 51341-51345) allow only 10000134/10000135. Entering one swaps the team for the TPS traveler matching the player's traveler, as the level 20 trial avatar 10064/10065, wearing the player's TPS loadout (224001 the first time, `CONST_VALUE_INIT_TPS_WEAPON_ID`). Leaving the dungeon restores the team. Weapons chosen there are kept as the loadout. |
| TPS traveler abilities | 10000134/10000135 share the travelers' icons, so the icon name alone gave them the normal traveler's abilities. They now load `ConfigAvatar_AetherShadow`/`LumineShadow` (`Avatar_TPS_Innate_Ability` and the rest; a stale `AbilityEmbryos.json` is backfilled). Their depot 50001 has no regular skills, only the attack mode skill 20000 (`Main_AimActive`), which is now in the skill maps. |
| Ammunition | A reserve per `TpsAmmunitionExcelConfigData` id, shared by the slots it fills (1001 feeds both rifles' slot 101/201), full by default, capped at `tpsAmmoLimit`. `ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION` (invoke argument 31) applies the client's `ammunition_list` changes. Each weapon's `ammunition_list` reports the reserve per slot. On entering a scene the server sends an empty `TpsRegionalPlaySupplyInfoNotify` (6579) and then `TpsAmmunitionChangeNotify` (24371) with each worn ammunition's reserve, before `EnterSceneDoneRsp`, as a capture shows (`{1001: 1000, 1003: 12, 1005: 3}`). Without 24371 the client's reserves are 0. |

The openConfig loader also had to learn the obfuscated 7.x talent entries (`PHNIGFHBFMD` = AddAbility,
`GMOELNAHCOH` = ModifyAbility, `CMGFNDNMFFO` = UnlockTalentParam, key `NCCKLDFFDOH` = abilityName).
Without them every TPS openConfig was empty. The same entries are used by the Cryo Traveler and
LittleIdol talents, which now load too.

### Trying it

```
/tps give            all eight TPS weapons (or /tps give 224001)
/tps accessory       unlock every accessory of the weapons you own
/tps wear 224001 224004   the field avatar wears the Glacia rifle and a fire grenade
/tps refill          fill all ammunition and resend the weapons
/tps ammo ...        ammunition experiments, see below
/dungeon 10955       the shooting range
/dungeon 10953       an Emerged Grey Field stage (also 10960-10964)
```

The commands need the `player.tps` and `player.enterdungeon` permissions.

### Ammunition experiments

How the client reads `SceneWeaponInfo.ammunition_list` is not settled, so `/tps ammo` switches the
fill at runtime (until restart) and resends the weapons:

```
/tps ammo type slot|one              ammunition_type = the ammo slot (101...) or 1
/tps ammo current reserve|limit|45   current_ammunition = the reserve, the pool limit, or a number
/tps ammo notify                     send 24371 with each worn ammunition's reserve
/tps ammo supply                     send a SUPPLY ammunition invoke to Avatar_TPS_Ammo_Manager
```

Every `ABILITY_META_UPDATE_TPS_WEAPON_AMMUNITION` the client sends is logged at info, decoded and
in hex (`TPS ammo invoke: ...`). The ammunition state lives in the client's TPS module: the
weapon-side `UNIQUE_TPS_*_Combatbase` modifier's mixin (`AGKHIHKEJCM`) names the slot (101), the
pool (1001) and the magazine size (45, or 75 with the magazine accessory), and the action
`EBDEAFMADGN` changes a slot by an amount or fills it.

Ammunition traffic is logged at debug level (`TPS ammunition ...`).

## How the protocol was recovered

Obfuscated names change every version, but structure does not, and inside one version an
obfuscated field name always stands for the same real name. The inputs were the 7.0 dump with
partial names (`7.0deof.proto`), its TPS name list, the 7.1 dump (`7.1Obfuscated.proto`) and the 7.1
name list (`7.1nt.txt`).

`tools/match.py OLD.proto NEW.proto NEW_NAMES.json NAME...` does it as constraint propagation:

1. A name the 7.1 list already knows is taken as is (`AbilityMetaUpdateTpsWeaponAmmunition`,
   `_SetWidgetQuickSlotListReq`). Otherwise the candidates are the 7.1 messages of the same shape:
   the multiset of field labels and kinds, where a kind is the scalar type, the real name of a type
   both versions know (`SceneWeaponInfo`), or the shape of the referenced type.
2. A resolved message resolves the types of its fields. Leaf structs are found this way: three
   `uint32`s look like 135 other messages, but `SceneWeaponInfo` field 12 has only one type.
3. Each resolved pair teaches old->new field names (`avatar_guid` -> `ECMNNFKNAIK`,
   `equip_guid_list` -> `OIMEKIFBCPB`). Every candidate must then use the mapped names and carry the
   resolved child types, and no 7.1 message may match two 7.0 ones.
4. Repeat until nothing changes.

```
python3 tools/match.py 7.0deof.proto 7.1Obfuscated.proto 7.1nt.txt \
    AbilityMetaUpdateTpsWeaponAmmunition PJFEBILHHJJ DHOKPFPMBAL JAPMAEHMHPG \
    TpsWeapon HGKMLCKCHBD JCGFEBNFLJP JPHBCDGGFOF HJOPNGJBLBN
```

resolves all of them; the CmdIds it gives for the packets (20756, 25902, 21312) are the ones this
repository already used. `tools/show.py` prints a 7.1 message with the known names filled in.

The field names in the 7.1 meta message line up with `SceneWeaponInfo.12`: `ammunition_type` and
the ammunition config id carry the same obfuscated names in both.

### Checking the 7.0 TPS name list

Inside one version a real field name always gets the same obfuscated name: in `7.1nt.txt` 2193
of 2194 field names have exactly one spelling, and e.g. `ECMNNFKNAIK` is `avatar_guid` in dozens
of messages. So a label from the 7.0 list can be checked: find the 7.1 field it became by
structure, and see whether 7.1 spells that real name the same way. `match.py --fields
7.0nameTranslation_TPS.txt ...` prints the result:

Two references are used: the 7.1 name list (its recovered names carry a leading underscore,
`_current_slot_num`, which the check ignores), and the 7.0 dump itself, which already spells many
fields by their real name.

| 7.0 label | 7.0 obf | 7.1 by structure | Verdict |
|---|---|---|---|
| `item_id` | BMNOGGNIIFO | PPFJCENMNEO | wrong: 7.1 `item_id` is EIGJMOABCGH. The values are ammunition ids, so it is named `ammunition_config_id`, the alias the list itself gives |
| `change_count` | FBMKHDNKCAP | CEAMPEAIPPJ | wrong: 7.1 `change_count` is DOCFIDJNIFO. Name kept as a description |
| `weapon_list` | KPJFLNKEFBC | MJGFMDADPJE | wrong: 7.1 `weapon_list` is OCBIBFHOCAG. Same name as `SceneAvatarInfo.tps_weapon_list`, so it is named that |
| `level` | MMAHICOGIKB | (field 12 or 14) | wrong: the matched message has no `level` (MLADOODEJDP) |
| `progress` | FHKAJDHNIJN | DFEMHODHAAP | wrong: the matched message has no `progress` (GLEDHAJBGBN) |
| `avatar_guid`, `equip_guid_list`, `retcode`, `update_type`, `affix_map`, `current_slot_num`, `material_id_list`, `is_force_ignore_cd` | | | confirmed: the 7.0 dump names the field so and 7.1 spells it the same |
| `entity_id` | FPKIFOPCGNE | | only "confirmed" through `SceneWeaponInfo.entity_id`; its own message is missing (below) |
| `accessory_list`, `ammunition_list`, `accessory_id_list`, `supply_progress_list`, `client_sequence`, `ammunition_type`, `current_ammunition`, `ammunition_config_id_list`, `slot_index` | | | unverifiable: neither reference knows the name. The first five are placed by structure |

`TpsEntityFightPropUpdateNotify` (KHAAHLBEIAC) is in neither dump, under its obfuscated name or any
real one. Both versions have the same six messages carrying `fight_prop_map` (the entity and avatar
fight-prop notifies, AvatarInfo, ShowAvatarInfo), so if it exists in 7.1 it is one of the two
unnamed entity notifies, 9736 or 27270, which this server uses as EntityFightPropUpdateNotify /
EntityFightPropNotify. The list's scope note says it covers proto files added after some commit,
so it likely comes from a later build.

### What is not settled

- `TpsWeaponAccessoryInfo` fields 12 and 14: the 7.0 list calls them `slot_index` and `level`, but
  neither is 7.1's `level`. The server only logs them.
- `AbilityMetaUpdateTpsWeaponAmmunition` field 7, a bool.
- `TpsAmmunitionChangeNotify` (24371) is a guessed name. At scene entry its counts are the reserves;
  whether later ones are deltas or totals is unknown, so it is only sent on entry and on `/tps refill`.
- `GetWidgetQuickSlotListRsp` / `SetWidgetQuickSlotListRsp` are 5047 and 6316 in some order.
- Whether `TpsWeaponAmmunitionInfo.current_ammunition` is the reserve or the loaded magazine. The
  ability config initialises the magazine on the client (`Avatar_TPS_Ammo_Manager`), so the
  server sends the reserve.
- The TPS traveler is a trial avatar here. Whether the official server grants it as an owned
  avatar instead is unknown. Outside TPS dungeons any avatar can still wear the weapons, which is
  handy with `/tps wear` for testing.

A packet capture from the official client would settle all of these.

## Regenerating the Java

The repository ships generated Java but not the `.proto` sources. `tools/extract.py` recovers the
descriptors embedded in the Java, and `tools/desc2proto.py` turns them back into `.proto` files that
`protoc` 3.18.1 compiles to byte-identical Java. Edit or add `.proto` files there, then compile only
those:

```
python3 tools/extract.py src/generated/main/java/emu/grasscutter/net/proto repo.desc
python3 tools/desc2proto.py repo.desc proto_src
# edit / add files in proto_src
protoc -Iproto_src --java_out=src/generated/main/java File1.proto File2.proto
```

Do not put `.proto` files under `proto/` in the repository: the build would then regenerate, and
`clean` delete, every generated class.
