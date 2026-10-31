package emu.grasscutter.command.commands;

import emu.grasscutter.data.GameData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import picocli.CommandLine;

/**
 * Human-readable OpenState names. Only names established by the existing
 * OpenStateData documentation are mapped here; newer resource IDs remain usable
 * numerically until their names have been verified.
 *
 * <p>Every list and completion result is intersected with the actually loaded
 * OpenStateConfigData entries, so obsolete names are never advertised.
 */
public final class OpenStateNames {
    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final Map<Integer, String> BY_ID = new HashMap<>();

    static {
        for (Known known : Known.values()) {
            String name = known.name().toLowerCase(Locale.ROOT);
            if (BY_NAME.put(name, known.id) != null || BY_ID.put(known.id, name) != null) {
                throw new IllegalStateException("Duplicate OpenState name or ID: " + name);
            }
        }
    }

    private OpenStateNames() {}

    static int parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        Integer known = BY_NAME.get(normalized);
        if (known != null) return known;
        if (normalized.matches("[0-9]+")) {
            try {
                return Integer.parseInt(normalized);
            } catch (NumberFormatException exception) {
                throw new CommandLine.TypeConversionException("OpenState ID is out of range: " + input);
            }
        }
        throw new CommandLine.TypeConversionException(
                "Unknown open state: " + input + ". Use 'openstate list' to find a name.");
    }

    static String display(int id) {
        String name = BY_ID.get(id);
        return name == null ? id + " (unknown)" : name + " (" + id + ")";
    }

    record Entry(int id, String name) {}

    static List<Entry> entries(Set<Integer> availableIds, String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (int id : availableIds) {
            String name = BY_ID.getOrDefault(id, Integer.toString(id));
            if (name.contains(needle) || Integer.toString(id).contains(needle)) {
                result.add(new Entry(id, name));
            }
        }
        result.sort(Comparator.comparing(Entry::name).thenComparingInt(Entry::id));
        return result;
    }

    static List<String> suggestions(Set<Integer> availableIds) {
        return entries(availableIds, null).stream()
                .filter(entry -> BY_ID.containsKey(entry.id()))
                .map(Entry::name)
                .toList();
    }

    /** Lazily consult the resources that are loaded when completion is requested. */
    public static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return suggestions(GameData.getOpenStateDataMap().keySet()).iterator();
        }
    }

    // Verified legacy names; do not infer names for 7.1-only IDs from their numbers.
    private enum Known {
        PAIMON(1),
        PAIMON_NAVIGATION(2),
        AVATAR_PROMOTE(3),
        AVATAR_TALENT(4),
        WEAPON_PROMOTE(5),
        WEAPON_AWAKEN(6),
        QUEST_REMIND(7),
        GAME_GUIDE(8),
        COOK(9),
        WEAPON_UPGRADE(10),
        RELIQUARY_UPGRADE(11),
        RELIQUARY_PROMOTE(12),
        WEAPON_PROMOTE_GUIDE(13),
        WEAPON_CHANGE_GUIDE(14),
        PLAYER_LVUP_GUIDE(15),
        FRESHMAN_GUIDE(16),
        SKIP_FRESHMAN_GUIDE(17),
        GUIDE_MOVE_CAMERA(18),
        GUIDE_SCALE_CAMERA(19),
        GUIDE_KEYBOARD(20),
        GUIDE_MOVE(21),
        GUIDE_JUMP(22),
        GUIDE_SPRINT(23),
        GUIDE_MAP(24),
        GUIDE_ATTACK(25),
        GUIDE_FLY(26),
        GUIDE_TALENT(27),
        GUIDE_RELIC(28),
        GUIDE_RELIC_PROM(29),
        COMBINE(30),
        GACHA(31),
        GUIDE_GACHA(32),
        GUIDE_TEAM(33),
        GUIDE_PROUD(34),
        GUIDE_AVATAR_PROMOTE(35),
        GUIDE_ADVENTURE_CARD(36),
        FORGE(37),
        GUIDE_BAG(38),
        EXPEDITION(39),
        GUIDE_ADVENTURE_DAILYTASK(40),
        GUIDE_ADVENTURE_DUNGEON(41),
        TOWER(42),
        WORLD_STAMINA(43),
        TOWER_FIRST_ENTER(44),
        RESIN(45),
        LIMIT_REGION_FRESHMEAT(47),
        LIMIT_REGION_GLOBAL(48),
        MULTIPLAYER(49),
        GUIDE_MOUSEPC(50),
        GUIDE_MULTIPLAYER(51),
        GUIDE_DUNGEONREWARD(52),
        GUIDE_BLOSSOM(53),
        AVATAR_FASHION(54),
        PHOTOGRAPH(55),
        GUIDE_KSLQUEST(56),
        PERSONAL_LINE(57),
        GUIDE_PERSONAL_LINE(58),
        GUIDE_APPEARANCE(59),
        GUIDE_PROCESS(60),
        GUIDE_PERSONAL_LINE_KEY(61),
        GUIDE_WIDGET(62),
        GUIDE_ACTIVITY_SKILL_ASTER(63),
        GUIDE_COLDCLIMATE(64),
        DERIVATIVE_MALL(65),
        GUIDE_EXITMULTIPLAYER(66),
        GUIDE_THEATREMACHANICUS_BUILD(67),
        GUIDE_THEATREMACHANICUS_REBUILD(68),
        GUIDE_THEATREMACHANICUS_CARD(69),
        GUIDE_THEATREMACHANICUS_MONSTER(70),
        GUIDE_THEATREMACHANICUS_MISSION_CHECK(71),
        GUIDE_THEATREMACHANICUS_BUILD_SELECT(72),
        GUIDE_THEATREMACHANICUS_CHALLENGE_START(73),
        GUIDE_CONVERT(74),
        GUIDE_THEATREMACHANICUS_MULTIPLAYER(75),
        GUIDE_COOP_TASK(76),
        GUIDE_HOMEWORLD_ADEPTIABODE(77),
        GUIDE_HOMEWORLD_DEPLOY(78),
        GUIDE_CHANNELLERSLAB_EQUIP(79),
        GUIDE_CHANNELLERSLAB_MP_SOLUTION(80),
        GUIDE_CHANNELLERSLAB_POWER(81),
        GUIDE_HIDEANDSEEK_SKILL(82),
        GUIDE_HOMEWORLD_MAPLIST(83),
        GUIDE_RELICRESOLVE(84),
        GUIDE_GGUIDE(85),
        GUIDE_GGUIDE_HINT(86),
        GUIDE_CHANNELLERSLAB_EQUIP_V2(87),
        GUIDE_CHANNELLERSLAB_MP_SOLUTION_V2(88),
        GUIDE_CHANNELLERSLAB_POWER_V2(89),
        GUIDE_QUICK_TEAMMEMBERCHANGE(90),
        GGUIDE_FIRSTSHOW(91),
        GGUIDE_MAINPAGE_ENTRY_DISAPPEAR(92),
        CITY_REPUATION_MENGDE(800),
        CITY_REPUATION_LIYUE(801),
        CITY_REPUATION_UI_HINT(802),
        CITY_REPUATION_INAZUMA(803),
        SHOP_TYPE_MALL(900),
        SHOP_TYPE_RECOMMANDED(901),
        SHOP_TYPE_GENESISCRYSTAL(902),
        SHOP_TYPE_GIFTPACKAGE(903),
        SHOP_TYPE_PAIMON(1001),
        SHOP_TYPE_CITY(1002),
        SHOP_TYPE_BLACKSMITH(1003),
        SHOP_TYPE_GROCERY(1004),
        SHOP_TYPE_FOOD(1005),
        SHOP_TYPE_SEA_LAMP(1006),
        SHOP_TYPE_VIRTUAL_SHOP(1007),
        SHOP_TYPE_LIYUE_GROCERY(1008),
        SHOP_TYPE_LIYUE_SOUVENIR(1009),
        SHOP_TYPE_LIYUE_RESTAURANT(1010),
        SHOP_TYPE_INAZUMA_SOUVENIR(1011),
        SHOP_TYPE_NPC_TOMOKI(1012),
        SHOP_TYPE_INAZUMA_SOUVENIR_BLACK_BAR(1013),
        ADVENTURE_MANUAL(1100),
        ADVENTURE_MANUAL_CITY_MENGDE(1101),
        ADVENTURE_MANUAL_CITY_LIYUE(1102),
        ADVENTURE_MANUAL_MONSTER(1103),
        ADVENTURE_MANUAL_BOSS_DUNGEON(1104),
        ACTIVITY_SEALAMP(1200),
        ACTIVITY_SEALAMP_TAB2(1201),
        ACTIVITY_SEALAMP_TAB3(1202),
        BATTLE_PASS(1300),
        BATTLE_PASS_ENTRY(1301),
        ACTIVITY_CRUCIBLE(1400),
        ACTIVITY_NEWBEEBOUNS_OPEN(1401),
        ACTIVITY_NEWBEEBOUNS_CLOSE(1402),
        ACTIVITY_ENTRY_OPEN(1403),
        MENGDE_INFUSEDCRYSTAL(1404),
        LIYUE_INFUSEDCRYSTAL(1405),
        SNOW_MOUNTAIN_ELDER_TREE(1406),
        MIRACLE_RING(1407),
        COOP_LINE(1408),
        INAZUMA_INFUSEDCRYSTAL(1409),
        FISH(1410),
        GUIDE_SUMO_TEAM_SKILL(1411),
        GUIDE_FISH_RECIPE(1412),
        HOME(1500),
        ACTIVITY_HOMEWORLD(1501),
        ADEPTIABODE(1502),
        HOME_AVATAR(1503),
        HOME_EDIT(1504),
        HOME_EDIT_TIPS(1505),
        RELIQUARY_DECOMPOSE(1600),
        ACTIVITY_H5(1700),
        ORAIONOKAMI(2000),
        GUIDE_CHESS_MISSION_CHECK(2001),
        GUIDE_CHESS_BUILD(2002),
        GUIDE_CHESS_WIND_TOWER_CIRCLE(2003),
        GUIDE_CHESS_CARD_SELECT(2004),
        INAZUMA_MAINQUEST_FINISHED(2005),
        PAIMON_LVINFO(2100),
        TELEPORT_HUD(2101),
        GUIDE_MAP_UNLOCK(2102),
        GUIDE_PAIMON_LVINFO(2103),
        GUIDE_AMBORTRANSPORT(2104),
        GUIDE_FLY_SECOND(2105),
        GUIDE_KAEYA_CLUE(2106),
        CAPTURE_CODEX(2107),
        ACTIVITY_FISH_OPEN(2200),
        ACTIVITY_FISH_CLOSE(2201),
        GUIDE_ROGUE_MAP(2205),
        GUIDE_ROGUE_RUNE(2206),
        GUIDE_BARTENDER_FORMULA(2210),
        GUIDE_BARTENDER_MIX(2211),
        GUIDE_BARTENDER_CUP(2212),
        GUIDE_MAIL_FAVORITES(2400),
        GUIDE_POTION_CONFIGURE(2401),
        GUIDE_LANV2_FIREWORK(2402),
        LOADINGTIPS_ENKANOMIYA(2403),
        MICHIAE_CASKET(2500),
        MAIL_COLLECT_UNLOCK_RED_POINT(2501),
        LUMEN_STONE(2600),
        GUIDE_CRYSTALLINK_BUFF(2601),
        GUIDE_MUSIC_GAME_V3(2700),
        GUIDE_MUSIC_GAME_V3_REAL_TIME_EDIT(2701),
        GUIDE_MUSIC_GAME_V3_TIMELINE_EDIT(2702),
        GUIDE_MUSIC_GAME_V3_SETTING(2703),
        GUIDE_ROBOTGACHA(2704),
        GUIDE_FRAGILE_RESIN(2800),
        ADVENTURE_MANUAL_EDUCATION(2801);
        private final int id;

        Known(int id) {
            this.id = id;
        }
    }
}
