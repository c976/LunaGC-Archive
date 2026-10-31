package emu.grasscutter.game.dps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.Utils;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Identifies the specific reaction name from an AttackResult.
 *
 * <p>The generic marker for lunar/astral "counts as reaction damage" is the {@code attackTag} in the ability
 * config (for example {@code
 * MoonOvergrowDamage}), independent of the character. Ordinary Dendro/Geo/Electro skills carry no such tag
 * and are still counted as elemental damage.
 */
public final class DPSReactionHelper {

    private static final Map<Integer, String> HASH_TO_REACTION = new HashMap<>();
    /** abilityName to the lunar/astral attackTag seen on that ability's Damage action. */
    private static final Map<String, EnumSet<AttackTagKind>> ABILITY_ATTACK_TAGS =
            new ConcurrentHashMap<>();

    private static final AtomicBoolean ATTACK_TAG_INDEX_LOADED = new AtomicBoolean();
    private static final AtomicInteger PROBE_LEFT = new AtomicInteger(40);

    private enum AttackTagKind {
        MOON_BLOOM,
        MOON_SHOCK,
        MOON_CRYSTAL,
        STAR_SUPER,
        STAR_SWIRL
    }

    static {
        // Astral
        mapHash("TeamAbility_StarSuperconductor", "Astral Superconduct");
        mapHash("TeamAbility_StarSwirl_Ice", "Astral Swirl");
        mapHash("Avatar_StarSuperconductor_Field_Checker", "Astral Superconduct");
        mapHash("ElementalReaction_StarSuperconductor", "Astral Superconduct");
        mapHash("ElementalReaction_StarSwirl_Ice", "Astral Swirl");
        mapHash("Avatar_NewElementReaction_Preload_StarSuperconductor", "Astral Superconduct");
        mapHash("Avatar_NewElementReaction_Preload_StarSwirl_Ice", "Astral Swirl");
        for (int i = 1; i <= 12; i++) {
            mapHash(String.format("StarSuperconducted_Attack%02d", i), "Astral Superconduct");
            mapHash(String.format("StarSuperconducted_Attack%d", i), "Astral Superconduct");
            mapHash(String.format("Player_Ice_StarSuperconducted_Attack%02d", i), "Astral Superconduct");
        }
        mapHash("StarSuperconducted_ExtraAttack", "Astral Superconduct");
        mapHash("StarSuperconducted_PlungeAttack", "Astral Superconduct");

        // Lunar
        mapHash("TeamAbility_MoonShock", "Lunar Charged");
        mapHash("TeamAbility_Reset_MoonOvergrow", "Lunar Bloom");
        mapHash("TeamAbility_MoonCrystal_Water", "Lunar Crystallize");
        mapHash("TeamAbility_MoonPhase", "Lunar Charged");
        mapHash("ElementalReaction_MoonShock", "Lunar Charged");
        mapHash("ElementalReaction_MoonOvergrow", "Lunar Bloom");
        mapHash("ElementalReaction_MoonCrystallize_Water", "Lunar Crystallize");
        mapHash("Avatar_NewElementReaction_Preload_MoonShock", "Lunar Charged");
        mapHash("Avatar_NewElementReaction_Preload_MoonOvergrow", "Lunar Bloom");
        mapHash("Avatar_NewElementReaction_Preload_MoonCrystallize_Water", "Lunar Crystallize");

        // Classic transformative/amplifying reactions, matched by ability name.
        mapHash("ElementalReaction_Explode", "Overload");
        mapHash("ElementalReaction_Superconductor", "Superconduct");
        mapHash("ElementalReaction_Electric", "Electro-Charged");
        mapHash("ElementalReaction_Stream", "Electro-Charged");
        mapHash("ElementalReaction_Burning", "Burning");
        mapHash("ElementalReaction_Swirl_Fire", "Swirl");
        mapHash("ElementalReaction_Swirl_Water", "Swirl");
        mapHash("ElementalReaction_Swirl_Electric", "Swirl");
        mapHash("ElementalReaction_Swirl_Ice", "Swirl");
        mapHash("ElementalReaction_FrozenBroken", "Shattered");
        mapHash("ElementalReaction_Overgrow", "Bloom");
        mapHash("ElementalReaction_Overgrow_Mushroom_Electric", "Hyperbloom");
        mapHash("ElementalReaction_Overgrow_Mushroom_Fire", "Burgeon");
        mapHash("ElementalReaction_Melt", "Melt");
        mapHash("ElementalReaction_Steam", "Vaporize");
        mapHash("ElementalReaction_Overdose_Electric", "Aggravate");
        mapHash("ElementalReaction_Overdose_Grass", "Spread");
    }

    private DPSReactionHelper() {}

    private static void mapHash(String abilityName, String reaction) {
        int h = Utils.abilityHash(abilityName);
        HASH_TO_REACTION.put(h, reaction);
        HASH_TO_REACTION.putIfAbsent((int) (h & 0xffffffffL), reaction);
    }

    public static String detect(AttackResult result, GameEntity attacker, ElementType element) {
        // Astral Superconduct: Field_Checker plus Cryo damage still classifies.
        // Lunar family: match only on ability attackTag or reaction ability name. Never reclassify every
        // Dendro/Geo/Electro hit in the party as a lunar reaction.
        if (result == null) {
            String named = fromStarField(attacker, element);
            if (named == null) probeOnce(null, attacker, element, null);
            return named;
        }

        String abilityName = resolveAbilityName(attacker, result);

        String named = fromAbilityAttackTag(abilityName, element);
        if (named != null) return named;

        named = fromText(result.getAnimEventId());
        if (named != null) return upgradeBloomIfMoonTeam(named, attacker);

        named = fromText(abilityName);
        if (named != null) return upgradeBloomIfMoonTeam(named, attacker);

        named = fromHashes(collectAllInts(result));
        if (named != null) return upgradeBloomIfMoonTeam(named, attacker);

        if (result.hasAbilityIdentifier()) {
            named = fromHashes(collectAllInts(result.getAbilityIdentifier()));
            if (named != null) return upgradeBloomIfMoonTeam(named, attacker);
        }

        GameEntity owner = attacker;
        if (attacker instanceof EntityClientGadget gadget) {
            try {
                if (gadget.getScene() != null) {
                    owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());
                }
                String ownerAbility = resolveAbilityName(owner, result);
                named = fromAbilityAttackTag(ownerAbility, element);
                if (named != null) return named;
                named = fromText(ownerAbility);
                if (named != null) return upgradeBloomIfMoonTeam(named, attacker);
            } catch (Throwable ignored) {
            }
        }

        named = fromStarField(attacker, element);
        if (named != null) return named;
        if (owner != attacker) {
            named = fromStarField(owner, element);
            if (named != null) return named;
        }

        for (int candidate : collectAllInts(result)) {
            named = nameOfReactionId(candidate);
            if (named != null) return upgradeBloomIfMoonTeam(named, attacker);
        }

        Float amplifyRate = findAmplifyRate(result);
        if (amplifyRate != null && amplifyRate > 0.05f && amplifyRate < 5f) {
            named = amplifyByElement(element, amplifyRate);
            if (named != null) return named;
        }

        probeOnce(result, attacker, element, null);
        return null;
    }

    private static String fromHashes(int[] values) {
        for (int v : values) {
            String named = HASH_TO_REACTION.get(v);
            if (named != null) return named;
        }
        return null;
    }

    /** Classifies by the attackTag in the ability config - works for every character, no hardcoded names. */
    private static String fromAbilityAttackTag(String abilityName, ElementType element) {
        if (abilityName == null || abilityName.isEmpty()) return null;
        ensureAttackTagIndex();
        EnumSet<AttackTagKind> tags = ABILITY_ATTACK_TAGS.get(abilityName);
        if (tags == null || tags.isEmpty()) return null;
        return pickReaction(tags, element);
    }

    private static String pickReaction(EnumSet<AttackTagKind> tags, ElementType element) {
        if (element == ElementType.Grass && tags.contains(AttackTagKind.MOON_BLOOM)) return "Lunar Bloom";
        if (element == ElementType.Electric && tags.contains(AttackTagKind.MOON_SHOCK)) return "Lunar Charged";
        if (element == ElementType.Rock && tags.contains(AttackTagKind.MOON_CRYSTAL)) return "Lunar Crystallize";
        if (element == ElementType.Ice && tags.contains(AttackTagKind.STAR_SUPER)) return "Astral Superconduct";
        if ((element == ElementType.Ice || element == ElementType.Wind)
                && tags.contains(AttackTagKind.STAR_SWIRL)) {
            return "Astral Swirl";
        }

        if (tags.contains(AttackTagKind.MOON_BLOOM)) return "Lunar Bloom";
        if (tags.contains(AttackTagKind.MOON_SHOCK)) return "Lunar Charged";
        if (tags.contains(AttackTagKind.MOON_CRYSTAL)) return "Lunar Crystallize";
        if (tags.contains(AttackTagKind.STAR_SUPER)) return "Astral Superconduct";
        if (tags.contains(AttackTagKind.STAR_SWIRL)) return "Astral Swirl";
        return null;
    }

    private static void ensureAttackTagIndex() {
        if (!ATTACK_TAG_INDEX_LOADED.compareAndSet(false, true)) return;
        try {
            Path root = FileUtils.getResourcePath("BinOutput/Ability");
            if (!Files.isDirectory(root)) {
                Grasscutter.getLogger().warn("[DPS-REACT] Ability dir missing: {}", root);
                return;
            }
            int files = 0;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                    files++;
                    indexAbilityFile(path);
                }
            }
            propagateMoonTagsToDamageAliases();
            Grasscutter.getLogger()
                    .debug(
                            "[DPS-REACT] attackTag index: {} abilities from {} files",
                            ABILITY_ATTACK_TAGS.size(),
                            files);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("[DPS-REACT] failed to build attackTag index", t);
        }
    }

    /**
     * Common for Lunar Bloom: the thing actually carrying {@code MoonOvergrowDamage} is the MoonLight gadget,
     * while the hit is recorded on
     * {@code *_Damage_Handler} or {@code *_ExtraAttack}. Backfilled by character prefix, not by hardcoded name.
     */
    private static void propagateMoonTagsToDamageAliases() {
        var snapshot = Map.copyOf(ABILITY_ATTACK_TAGS);
        for (var entry : snapshot.entrySet()) {
            String name = entry.getKey();
            EnumSet<AttackTagKind> tags = entry.getValue();
            if (tags == null || tags.isEmpty()) continue;

            String prefix = characterPrefix(name);
            if (prefix == null) continue;

            EnumSet<AttackTagKind> moonOnly = EnumSet.noneOf(AttackTagKind.class);
            if (tags.contains(AttackTagKind.MOON_BLOOM)) moonOnly.add(AttackTagKind.MOON_BLOOM);
            if (tags.contains(AttackTagKind.MOON_SHOCK)) moonOnly.add(AttackTagKind.MOON_SHOCK);
            if (tags.contains(AttackTagKind.MOON_CRYSTAL)) moonOnly.add(AttackTagKind.MOON_CRYSTAL);
            if (moonOnly.isEmpty()) continue;

            // Damage_Handler commonly forwards the lunar reaction damage segment.
            mergeAbilityTags(prefix + "_Damage_Handler", moonOnly);

            // Moonlight charged attacks: the source ability name carries ExtraAttack+MoonLight or MoonExtraAttack.
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("extraattack_moonlight")
                    || lower.contains("moonlight_gadget")
                    || lower.contains("moonextraattack")
                    || (lower.contains("extraattack")
                            && lower.contains("moon")
                            && (lower.contains("overgrow") || lower.contains("light")))) {
                mergeAbilityTags(prefix + "_ExtraAttack", EnumSet.of(AttackTagKind.MOON_BLOOM));
            }
        }
    }

    private static String characterPrefix(String abilityName) {
        if (abilityName == null || abilityName.isEmpty()) return null;
        if (abilityName.startsWith("Avatar_")) {
            int first = abilityName.indexOf('_');
            int second = abilityName.indexOf('_', first + 1);
            if (second > first) return abilityName.substring(0, second);
            return null;
        }
        int u = abilityName.indexOf('_');
        return u > 0 ? abilityName.substring(0, u) : null;
    }

    private static void mergeAbilityTags(String abilityName, EnumSet<AttackTagKind> add) {
        if (abilityName == null || add == null || add.isEmpty()) return;
        ABILITY_ATTACK_TAGS.merge(
                abilityName,
                EnumSet.copyOf(add),
                (a, b) -> {
                    EnumSet<AttackTagKind> m = EnumSet.copyOf(a);
                    m.addAll(b);
                    return m;
                });
    }

    private static void indexAbilityFile(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (!text.contains("attackTag")) return;
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray()) {
                    indexAbilityNode(el);
                }
            } else {
                indexAbilityNode(root);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void indexAbilityNode(JsonElement el) {
        if (el == null || !el.isJsonObject()) return;
        JsonObject obj = el.getAsJsonObject();
        JsonObject body =
                obj.has("Default") && obj.get("Default").isJsonObject()
                        ? obj.getAsJsonObject("Default")
                        : obj;
        if (!body.has("abilityName") || !body.get("abilityName").isJsonPrimitive()) return;
        String name = body.get("abilityName").getAsString();
        if (name == null || name.isEmpty()) return;

        EnumSet<AttackTagKind> found = EnumSet.noneOf(AttackTagKind.class);
        collectAttackTags(body, found);
        if (!found.isEmpty()) {
            ABILITY_ATTACK_TAGS.merge(
                    name,
                    found,
                    (a, b) -> {
                        EnumSet<AttackTagKind> m = EnumSet.copyOf(a);
                        m.addAll(b);
                        return m;
                    });
        }
    }

    private static void collectAttackTags(JsonElement el, EnumSet<AttackTagKind> out) {
        if (el == null || out == null) return;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("attackTag") && obj.get("attackTag").isJsonPrimitive()) {
                AttackTagKind kind = kindOfAttackTag(obj.get("attackTag").getAsString());
                if (kind != null) out.add(kind);
            }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                collectAttackTags(e.getValue(), out);
            }
        } else if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement child : arr) {
                collectAttackTags(child, out);
            }
        }
    }

    private static AttackTagKind kindOfAttackTag(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        return switch (tag) {
            case "MoonOvergrowDamage" -> AttackTagKind.MOON_BLOOM;
            case "MoonShockDamage" -> AttackTagKind.MOON_SHOCK;
            case "MoonCrystallizeWaterDamage" -> AttackTagKind.MOON_CRYSTAL;
            case "StarSuperconductorIceDamage", "StarSuperconductorElectricDamage" ->
                    AttackTagKind.STAR_SUPER;
            case "StarSwirlIceDamage", "StarSwirlWindDamage" -> AttackTagKind.STAR_SWIRL;
            default -> null;
        };
    }

    /** Astral reaction fields: Cryo damage can classify as Astral Superconduct/Swirl. Lunar no longer
     * reclassifies by element wholesale. */
    private static String fromStarField(GameEntity entity, ElementType element) {
        if (element == null) return null;
        FieldFlags flags = scanTeamFields(entity);
        if (flags.starSuper && element == ElementType.Ice) return "Astral Superconduct";
        if (flags.starSwirl && (element == ElementType.Ice || element == ElementType.Wind))
            return "Astral Swirl";
        return null;
    }

    /** While the party has Lunar Bloom active, plain Bloom cores count as Lunar Bloom. Hyperbloom and
     * Burgeon are unaffected. */
    private static String upgradeBloomIfMoonTeam(String named, GameEntity entity) {
        if (!"Bloom".equals(named)) return named;
        return scanTeamFields(entity).moonBloom ? "Lunar Bloom" : named;
    }

    private static FieldFlags scanTeamFields(GameEntity entity) {
        FieldFlags flags = new FieldFlags();
        scanEntityFields(entity, flags);
        Player player = resolvePlayer(entity);
        if (player != null) {
            try {
                for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
                    scanEntityFields(avatar, flags);
                }
            } catch (Throwable ignored) {
            }
        }
        return flags;
    }

    private static final class FieldFlags {
        boolean starSuper;
        boolean starSwirl;
        boolean moonBloom;
    }

    private static void scanEntityFields(GameEntity entity, FieldFlags flags) {
        if (entity == null || flags == null) return;
        try {
            var abilities = entity.getInstancedAbilities();
            if (abilities == null) return;
            for (var ability : abilities) {
                if (ability == null || ability.getData() == null) continue;
                String n = ability.getData().abilityName;
                if (n == null) continue;
                String lower = n.toLowerCase(Locale.ROOT);
                if (lower.contains("starsuperconductor")
                        || lower.contains("starsuperconducted")
                        || lower.contains("starsupport")
                        || (lower.contains("relic")
                                && lower.contains("star")
                                && lower.contains("super"))) {
                    flags.starSuper = true;
                }
                if (lower.contains("starswirl")) {
                    flags.starSwirl = true;
                }
                if (lower.contains("moonovergrow")
                        || lower.contains("moonbloom")
                        || (lower.contains("moon") && lower.contains("overgrow"))
                        || lower.contains("preload_moonovergrow")
                        || lower.contains("reset_moonovergrow")) {
                    flags.moonBloom = true;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Player resolvePlayer(GameEntity entity) {
        if (entity == null) return null;
        try {
            if (entity instanceof EntityAvatar avatar) {
                return avatar.getPlayer();
            }
            if (entity instanceof EntityClientGadget gadget) {
                if (gadget.getOwner() != null) return gadget.getOwner();
                if (gadget.getScene() != null) {
                    GameEntity owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());
                    if (owner instanceof EntityAvatar av) return av.getPlayer();
                    if (owner instanceof EntityClientGadget og && og.getOwner() != null) {
                        return og.getOwner();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String amplifyByElement(ElementType element, float rate) {
        if (element == null) return null;
        return switch (element) {
            case Ice -> "Melt";
            case Water -> "Vaporize";
            case Fire -> rate >= 1.75f ? "Melt" : "Vaporize";
            default -> null;
        };
    }

    private static Float findAmplifyRate(AttackResult result) {
        try {
            for (Method method : result.getClass().getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != float.class) continue;
                String name = method.getName();
                if (!name.startsWith("get") || name.equals("getDamage")) continue;
                float v = (Float) method.invoke(result);
                if (!Float.isFinite(v)) continue;
                if (v >= 0.4f && v <= 2.5f && Math.abs(v - result.getDamage()) > 1f) return v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[] collectAllInts(Object message) {
        if (message == null) return new int[0];
        try {
            Method[] methods = message.getClass().getMethods();
            int[] buf = new int[methods.length];
            int n = 0;
            for (Method method : methods) {
                if (method.getParameterCount() != 0 || method.getReturnType() != int.class) continue;
                String name = method.getName();
                if (!name.startsWith("get")) continue;
                if (name.equals("getSerializedSize")
                        || name.equals("hashCode")
                        || name.equals("getElementType")
                        || name.equals("getAttackerId")
                        || name.equals("getDefenseId")
                        || name.equals("getHitRetreatAngleCompat")) {
                    continue;
                }
                Object value = method.invoke(message);
                if (value instanceof Integer i && i != 0) {
                    buf[n++] = i;
                }
            }
            int[] out = new int[n];
            System.arraycopy(buf, 0, out, 0, n);
            return out;
        } catch (Throwable ignored) {
            return new int[0];
        }
    }

    private static String resolveAbilityName(GameEntity attacker, AttackResult result) {
        if (attacker == null || result == null || !result.hasAbilityIdentifier()) return null;
        try {
            int instanced = result.getAbilityIdentifier().getInstancedAbilityId();
            if (instanced <= 0) return null;
            var abilities = attacker.getInstancedAbilities();
            if (abilities == null || instanced > abilities.size()) return null;
            var ability = abilities.get(instanced - 1);
            if (ability == null || ability.getData() == null) return null;
            return ability.getData().abilityName;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String fromText(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String s = raw.toLowerCase(Locale.ROOT);

        if (s.contains("starsuperconduct")
                || s.contains("star_superconduct")
                || s.contains("starsuperconductor")
                || (s.contains("star") && s.contains("superconduct"))) {
            return "Astral Superconduct";
        }
        if (s.contains("starswirl") || (s.contains("star") && s.contains("swirl"))) {
            return "Astral Swirl";
        }
        if (s.contains("moonshock")
                || (s.contains("moon") && (s.contains("shock") || s.contains("\u611f\u7535")))) {
            return "Lunar Charged";
        }
        if (s.contains("moonovergrow")
                || s.contains("moonbloom")
                || (s.contains("moon") && (s.contains("overgrow") || s.contains("bloom")))) {
            return "Lunar Bloom";
        }
        if (s.contains("mooncrystal")
                || s.contains("mooncrystall")
                || (s.contains("moon") && (s.contains("crystal") || s.contains("\u7ed3\u6676")))) {
            return "Lunar Crystallize";
        }

        // The escaped literals below are NOT display text and must stay as they are: they are matched
        // against ability and reaction identifiers coming from the game's own data, which is Chinese on
        // a CN client. They are written as escapes to keep this source pure ASCII. Translating or deleting
        // them would make these branches silently stop matching.
        if (s.contains("vaporize")
                || s.contains("\u84b8\u53d1")
                || (s.contains("steam") && s.contains("reaction"))) return "Vaporize";
        if (s.contains("melt") || s.contains("\u878d\u5316")) return "Melt";
        if (s.contains("overload") || s.contains("explode") || s.contains("\u8d85\u8f7d")) return "Overload";
        if (s.contains("superconduct") || s.contains("\u8d85\u5bfc")) return "Superconduct";
        if (s.contains("electrocharged")
                || s.contains("electro_charged")
                || (s.contains("stream") && s.contains("reaction"))
                || s.contains("\u611f\u7535")) return "Electro-Charged";
        if (s.contains("burning") || s.contains("\u71c3\u70e7")) return "Burning";
        if (s.contains("shatter") || s.contains("frozenbroken") || s.contains("\u788e\u51b0")) return "Shattered";
        if (s.contains("swirl") || s.contains("\u6269\u6563")) return "Swirl";
        if (s.contains("hyperbloom") || s.contains("\u8d85\u7efd")) return "Hyperbloom";
        if (s.contains("burgeon") || s.contains("\u70c8\u7efd")) return "Burgeon";
        if (s.contains("bloom") || s.contains("overgrow") || s.contains("\u7efd\u653e")) return "Bloom";
        if (s.contains("aggravate") || s.contains("\u8d85\u6fc0")) return "Aggravate";
        if (s.contains("spread") || s.contains("\u8513\u6fc0")) return "Spread";
        return null;
    }

    private static String nameOfReactionId(int type) {
        boolean beyond = type >= 4700 && type <= 4800;
        if (beyond) type -= 4700;
        else if (type >= 1 && type <= 5) return null;
        return switch (type) {
            case 1 -> "Overload";
            case 2 -> "Electro-Charged";
            case 3, 4 -> "Burning";
            case 6 -> "Bloom";
            case 7 -> "Melt";
            case 12 -> "Electro-Charged";
            case 16 -> "Superconduct";
            case 17, 18, 19, 20, 21, 22, 23, 24 -> "Swirl";
            case 31 -> "Shattered";
            case 34 -> "Aggravate";
            case 35 -> "Spread";
            case 40 -> "Lunar Charged";
            case 41 -> "Lunar Bloom";
            case 42 -> "Lunar Crystallize";
            case 43 -> "Astral Superconduct";
            case 44 -> "Astral Swirl";
            default -> null;
        };
    }

    private static void probeOnce(
            AttackResult result, GameEntity attacker, ElementType element, String resolved) {
        if (PROBE_LEFT.getAndDecrement() <= 0) return;
        // This is a probe, not a fault: it fires per attack and says nothing an operator acts on.
        if (!Grasscutter.getLogger().isDebugEnabled()) return;
        try {
            if (result == null) {
                Grasscutter.getLogger()
                        .debug(
                                "[DPS-REACT] miss result=null elem={} atk={}",
                                element,
                                attacker != null ? attacker.getClass().getSimpleName() : null);
                return;
            }
            String ability = resolveAbilityName(attacker, result);
            StringBuilder ints = new StringBuilder();
            for (int v : collectAllInts(result)) {
                ints.append(v).append(',');
            }
            ensureAttackTagIndex();
            EnumSet<AttackTagKind> tags =
                    ability != null
                            ? ABILITY_ATTACK_TAGS.getOrDefault(
                                    ability, EnumSet.noneOf(AttackTagKind.class))
                            : EnumSet.noneOf(AttackTagKind.class);
            Grasscutter.getLogger()
                    .debug(
                            "[DPS-REACT] miss dmg={} elem={} anim={} ability={} atk={} hashes=[{}] attackTags={}",
                            result.getDamage(),
                            element,
                            result.getAnimEventId(),
                            ability,
                            attacker != null ? attacker.getClass().getSimpleName() : null,
                            ints,
                            tags);
        } catch (Throwable ignored) {
        }
    }
}
