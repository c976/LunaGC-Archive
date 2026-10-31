package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.GatherData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.server.GadgetMapping;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityItem;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.SpawnDataEntry;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public final class GatherInteractHelper {
   private static final int UNLOCK_NONE = 0;
   private static final int UNLOCK_EXPLICIT = 1;
   private static final int UNLOCK_STATE = 2;
   private static Int2ObjectMap<GatherData> byGadgetId;
   private static Int2ObjectMap<GatherData> byItemId;
   private static IntSet hardcodedInitDisableGadgetIds;
   private static Int2ObjectMap<String> serverControllerByGadgetId;
   private static final IntSet explicitlyGatherEnabled = IntSets.synchronize(new IntOpenHashSet());
   private static final IntSet gatherLootDropped = IntSets.synchronize(new IntOpenHashSet());
   private static final IntSet breakFirstGadgetIds = IntOpenHashSet.of(new int[]{70540028, 70520018, 70520019, 70510005});
   private static final IntSet breakFirstPointTypes = IntOpenHashSet.of(2029, 3006);
   private static final IntSet breakFirstItemIds = IntOpenHashSet.of(100033, 100054);
   private static final int ORE_SHELL_GADGET_ID = 70520018;

   private GatherInteractHelper() {
   }

   private static void ensureGadgetIndex() {
      if (byGadgetId == null) {
         Int2ObjectOpenHashMap var0 = new Int2ObjectOpenHashMap();
         Int2ObjectOpenHashMap var1 = new Int2ObjectOpenHashMap();
         IntOpenHashSet var2 = new IntOpenHashSet();
         ObjectIterator var3 = GameData.getGatherDataMap().values().iterator();

         while (var3.hasNext()) {
            GatherData var4 = (GatherData)var3.next();
            int var5 = var4.getGadgetId();
            if (var5 > 0) {
               var0.putIfAbsent(var5, var4);
               if (var4.initDisableInteract()) {
                  var2.add(var5);
               }
            }

            if (var4.getItemId() > 0) {
               var1.putIfAbsent(var4.getItemId(), var4);
            }
         }

         byGadgetId = var0;
         byItemId = var1;
         hardcodedInitDisableGadgetIds = var2;
      }
   }

   private static void ensureControllerIndex() {
      if (serverControllerByGadgetId == null) {
         Int2ObjectOpenHashMap var0 = new Int2ObjectOpenHashMap();
         ObjectIterator var1 = GameData.getGadgetMappingMap().values().iterator();

         while (var1.hasNext()) {
            GadgetMapping var2 = (GadgetMapping)var1.next();
            if (var2.getGadgetId() > 0 && var2.getServerController() != null) {
               var0.putIfAbsent(var2.getGadgetId(), var2.getServerController());
            }
         }

         serverControllerByGadgetId = var0;
      }
   }

   private static String resolveServerController(int var0) {
      if (var0 <= 0) {
         return null;
      } else {
         ensureControllerIndex();
         return (String)serverControllerByGadgetId.get(var0);
      }
   }

   private static String resolveServerController(EntityGadget var0) {
      if (var0 == null) {
         return null;
      } else {
         String var1 = resolveServerController(var0.getGadgetId());
         if (var1 != null) {
            return var1;
         } else {
            GatherData var2 = resolveGatherData(var0);
            return var2 != null ? resolveServerController(var2.getGadgetId()) : null;
         }
      }
   }

   private static boolean hasOreJsonName(EntityGadget var0) {
      GadgetData var1 = var0.getGadgetData();
      if (var1 != null && var1.getJsonName() != null) {
         String var2 = var1.getJsonName();
         return var2.contains("Gather_Advance_Ore")
            || var2.contains("Gather_Default_Ore")
            || var2.contains("Gather_Small_Ore")
            || var2.contains("Gather_MagicOre");
      } else {
         return false;
      }
   }

   private static int resolveUnlockMode(EntityGadget var0) {
      if (var0 == null) {
         return 0;
      } else if (!breakFirstGadgetIds.contains(var0.getGadgetId()) && !hasOreJsonName(var0)) {
         String var1 = resolveServerController(var0);
         if (var1 != null) {
            if (var1.startsWith("SubfieldDrop")
               || var1.startsWith("ElementFlora")
               || var1.equals("ElementRock")
               || var1.equals("DreamFlower")
               || var1.equals("DatePalm")
               || var1.equals("MoonLotus")
               || var1.startsWith("Gather_ElectricRock")
               || var1.startsWith("Gather_FireFlower")
               || var1.startsWith("Gather_IceFlower")) {
               return 1;
            }

            if (var1.equals("GlazedLily")) {
               return 2;
            }
         }

         GatherData var2 = resolveGatherData(var0);
         return var2 != null && var2.initDisableInteract() ? 2 : 0;
      } else {
         return 1;
      }
   }

   public static int resolveVisibleGatherGadgetId(GatherData var0) {
      if (var0 == null) {
         return 0;
      } else if (!breakFirstItemIds.contains(var0.getItemId()) && !breakFirstPointTypes.contains(var0.getId()) && var0.getGadgetId() != 70540028) {
         if (var0.initDisableInteract()) {
            String var1 = resolveServerController(var0.getGadgetId());
            if (var1 != null && var1.startsWith("SubfieldDrop")) {
               return var0.getGadgetId();
            }
         }

         return var0.getGadgetId();
      } else {
         return 70520018;
      }
   }

   private static boolean isHardBreakFirstGather(EntityGadget var0, GatherData var1) {
      if (breakFirstGadgetIds.contains(var0.getGadgetId())) {
         return true;
      } else if (breakFirstPointTypes.contains(var0.getPointType())) {
         return true;
      } else {
         return var1 == null ? false : breakFirstItemIds.contains(var1.getItemId()) || breakFirstPointTypes.contains(var1.getId());
      }
   }

   public static GatherData resolveGatherData(EntityGadget var0) {
      if (var0 == null) {
         return null;
      } else {
         int var1 = var0.getPointType();
         if (var1 > 0) {
            GatherData var2 = (GatherData)GameData.getGatherDataMap().get(var1);
            if (var2 != null) {
               return var2;
            }
         }

         SpawnDataEntry var9 = var0.getSpawnEntry();
         if (var9 != null && var9.getGatherItemId() > 0) {
            ensureGadgetIndex();
            GatherData var3 = (GatherData)byItemId.get(var9.getGatherItemId());
            if (var3 != null) {
               return var3;
            }
         }

         int var10 = var0.getGadgetId();
         if (var10 > 0) {
            ensureGadgetIndex();
            GatherData var4 = (GatherData)byGadgetId.get(var10);
            if (var4 != null) {
               return var4;
            }
         }

         GadgetData var11 = var0.getGadgetData();
         if (var11 != null && var11.getJsonName() != null) {
            String var5 = var11.getJsonName();
            ObjectIterator var6 = GameData.getGatherDataMap().values().iterator();

            while (var6.hasNext()) {
               GatherData var7 = (GatherData)var6.next();
               GadgetData var8 = (GadgetData)GameData.getGadgetDataMap().get(var7.getGadgetId());
               if (var8 != null && var5.equals(var8.getJsonName())) {
                  return var7;
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }

   /** Prefer getRawInteractEnabled(); fall back to field before EntityGadget patch. */
   private static boolean rawInteractEnabled(EntityGadget gadget) {
      try {
         java.lang.reflect.Method m = gadget.getClass().getMethod("getRawInteractEnabled");
         Object v = m.invoke(gadget);
         return v instanceof Boolean && (Boolean) v;
      } catch (Throwable ignored) {
      }
      try {
         java.lang.reflect.Field f = emu.grasscutter.game.entity.EntityGadget.class.getDeclaredField("interactEnabled");
         f.setAccessible(true);
         return f.getBoolean(gadget);
      } catch (Throwable ignored) {
         return true;
      }
   }

   public static boolean needsDisabledInteractUntilReady(EntityGadget var0) {
      if (var0 == null) {
         return false;
      } else if (resolveUnlockMode(var0) != 0) {
         return true;
      } else {
         GatherData var1 = resolveGatherData(var0);
         if (var1 != null && var1.initDisableInteract()) {
            return true;
         } else {
            int var2 = var0.getGadgetId();
            if (var2 > 0) {
               ensureGadgetIndex();
               return hardcodedInitDisableGadgetIds.contains(var2);
            } else {
               return false;
            }
         }
      }
   }

   public static boolean computeClientInteractEnabled(EntityGadget var0) {
      if (var0 == null) {
         return true;
      } else {
         GatherData var1 = resolveGatherData(var0);
         if (isHardBreakFirstGather(var0, var1)) {
            return explicitlyGatherEnabled.contains(var0.getId());
         } else if (!needsDisabledInteractUntilReady(var0)) {
            return rawInteractEnabled(var0);
         } else if (explicitlyGatherEnabled.contains(var0.getId())) {
            return true;
         } else {
            int var2 = resolveUnlockMode(var0);
            return var2 == 2 && var0.getState() != 0;
         }
      }
   }

   public static boolean safeClientInteractEnabled(EntityGadget var0) {
      try {
         return computeClientInteractEnabled(var0);
      } catch (Throwable var2) {
         Grasscutter.getLogger()
            .warn(
               "GatherInteract fallback gadgetId={} configId={}",
               new Object[]{var0 != null ? var0.getGadgetId() : 0, var0 != null ? var0.getConfigId() : 0, var2}
            );
         return false;
      }
   }

   public static boolean isGatherInteractAllowed(EntityGadget var0) {
      return safeClientInteractEnabled(var0);
   }

   public static void markGatherInteractEnabled(EntityGadget var0) {
      if (var0 != null) {
         explicitlyGatherEnabled.add(var0.getId());
      }
   }

   /** Entity ids are never reused, so both sets would otherwise only ever grow. */
   public static void clearEntityState(int entityId) {
      explicitlyGatherEnabled.remove(entityId);
      gatherLootDropped.remove(entityId);
   }

   public static void markGatherInteractDisabled(EntityGadget var0) {
      if (var0 != null) {
         explicitlyGatherEnabled.remove(var0.getId());
      }
   }

   public static void onGatherReady(EntityGadget var0) {
      if (var0 != null && needsDisabledInteractUntilReady(var0)) {
         markGatherInteractEnabled(var0);
         var0.setInteractEnabled(true);
      }
   }

   /** Fire/Ice/Sakura elemental flora and ElementFlora controllers. */
   public static boolean isElementalFlora(EntityGadget gadget) {
      if (gadget == null) {
         return false;
      }
      String controller = resolveServerController(gadget);
      if (controller != null
         && (controller.equals("ElementFlora")
            || controller.startsWith("ElementFlora")
            || controller.startsWith("Gather_FireFlower")
            || controller.startsWith("Gather_IceFlower"))) {
         return true;
      }
      String json = jsonName(gadget);
      return json.contains("FireFlower")
         || json.contains("IceFlower")
         || json.contains("Cherrypetal")
         || json.contains("ElementFlora");
   }

   private static String jsonName(EntityGadget gadget) {
      GadgetData data = gadget.getGadgetData();
      return data != null && data.getJsonName() != null ? data.getJsonName() : "";
   }

   private static boolean isFireFlora(EntityGadget gadget) {
      String json = jsonName(gadget);
      if (json.contains("FireFlower")) {
         return true;
      }
      String controller = resolveServerController(gadget);
      return controller != null && controller.startsWith("Gather_FireFlower");
   }

   private static boolean isIceFlora(EntityGadget gadget) {
      String json = jsonName(gadget);
      if (json.contains("IceFlower")) {
         return true;
      }
      String controller = resolveServerController(gadget);
      return controller != null && controller.startsWith("Gather_IceFlower");
   }

   /** Sakura Bloom (gadget 70520034 / Cherrypetals): unlocks on Electro. */
   private static boolean isCherryPetals(EntityGadget gadget) {
      if (gadget == null) {
         return false;
      }
      if (gadget.getGadgetId() == 70520034) {
         return true;
      }
      return jsonName(gadget).contains("Cherrypetal");
   }

   /**
    * Unlock gather interact after the correct element extinguishes/melts the flora.
    * Flaming Flower: Hydro/Cryo; Frostbearing Flower: Pyro; Sakura Bloom: Electro.
    * ElementFlora also unlocks via client ExecuteGadgetLua.
    */
   public static boolean tryUnlockElementalFlora(EntityGadget gadget, emu.grasscutter.game.props.ElementType element) {
      if (gadget == null || !isElementalFlora(gadget)) {
         return false;
      }
      if (computeClientInteractEnabled(gadget)) {
         return true;
      }
      if (element == null) {
         element = emu.grasscutter.game.props.ElementType.None;
      }

      boolean unlock = false;
      if (isCherryPetals(gadget)) {
         unlock = element == emu.grasscutter.game.props.ElementType.Electric;
      } else if (isFireFlora(gadget)) {
         unlock =
            element == emu.grasscutter.game.props.ElementType.Water
               || element == emu.grasscutter.game.props.ElementType.Ice
               || element == emu.grasscutter.game.props.ElementType.Frozen;
      } else if (isIceFlora(gadget)) {
         unlock = element == emu.grasscutter.game.props.ElementType.Fire;
      } else {
         // Generic ElementFlora fallback (client already reacted).
         unlock =
            element == emu.grasscutter.game.props.ElementType.Water
               || element == emu.grasscutter.game.props.ElementType.Ice
               || element == emu.grasscutter.game.props.ElementType.Frozen
               || element == emu.grasscutter.game.props.ElementType.Fire
               || element == emu.grasscutter.game.props.ElementType.Electric;
      }

      if (!unlock) {
         return false;
      }

      onGatherReady(gadget);
      if (gadget.getState() == 0) {
         try {
            gadget.updateState(901); // GadgetState.Action01
         } catch (Throwable ignored) {
         }
      }
      Grasscutter.getLogger()
         .info(
            "ElementFlora unlock gadgetId={} cfg={} elem={} json={}",
            gadget.getGadgetId(),
            gadget.getConfigId(),
            element,
            jsonName(gadget));
      return true;
   }

   static boolean hasSubfieldGatherLoot(int gatherItemId, int spawnGatherItemId) {
      return gatherItemId > 0 || spawnGatherItemId > 0;
   }

   public static boolean shouldDropGroundLootOnBreak(EntityGadget var0) {
      String var1 = resolveServerController(var0);
      if (var1 != null) {
         if (var1.startsWith("SubfieldDrop")) {
            GatherData gather = resolveGatherData(var0);
            SpawnDataEntry spawn = var0.getSpawnEntry();
            return hasSubfieldGatherLoot(
               gather != null ? gather.getItemId() : 0,
               spawn != null ? spawn.getGatherItemId() : 0
            );
         }

         if (var1.startsWith("Gather_ElectricRock")) {
            return true;
         }

         if (var1.equals("ElementFlora") || var1.startsWith("Gather_FireFlower") || var1.startsWith("Gather_IceFlower")) {
            return false;
         }
      }

      return breakFirstGadgetIds.contains(var0.getGadgetId()) || hasOreJsonName(var0);
   }

   public static void onGatherBreak(EntityGadget var0) {
      if (var0 != null && needsDisabledInteractUntilReady(var0)) {
         if (shouldDropGroundLootOnBreak(var0)) {
            dropBrokenGatherLoot(var0);
         }

         onGatherReady(var0);
      }
   }

   public static void dropBrokenGatherLoot(EntityGadget var0) {
      if (var0 != null && !gatherLootDropped.contains(var0.getId())) {
         int var1 = 0;
         if (var0.getSpawnEntry() != null && var0.getSpawnEntry().getGatherItemId() > 0) {
            var1 = var0.getSpawnEntry().getGatherItemId();
         } else {
            GatherData var2 = resolveGatherData(var0);
            if (var2 != null) {
               var1 = var2.getItemId();
            }
         }

         if (var1 <= 0) {
            Grasscutter.getLogger().warn("GatherInteract drop skipped: no item for gadgetId={} pointType={}", var0.getGadgetId(), var0.getPointType());
         } else {
            ItemData var4 = (ItemData)GameData.getItemDataMap().get(var1);
            if (var4 != null) {
               EntityItem var3 = new EntityItem(var0.getScene(), null, var4, var0.getPosition().nearby2d(1.0F).addY(0.5F), 1, true);
               var0.getScene().addEntity(var3);
               gatherLootDropped.add(var0.getId());
            }
         }
      }
   }

   public static void applyCheckSpawnsFightProperty(GameEntity var0, FightProperty var1, float var2) {
      if (var2 != Float.POSITIVE_INFINITY) {
         var0.setFightProperty(var1, var2);
      }
   }

   public static void restoreBreakableCombatHp(EntityGadget var0) {
      if (var0 != null) {
         float var1 = var0.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
         if (var1 == Float.POSITIVE_INFINITY || !(var1 > 0.0F) || !(var1 < 100000.0F)) {
            ConfigEntityGadget var2 = var0.getConfigGadget();
            if (var2 == null && var0.getGadgetData() != null) {
               var2 = GameData.getGadgetConfigData().get(var0.getGadgetData().getJsonName());
            }

            if (var2 != null && var2.getCombat() != null && var2.getCombat().getProperty() != null) {
               float var3 = var2.getCombat().getProperty().getHP();
               if (!(var3 <= 0.0F) && !(var3 > 10000.0F)) {
                  var0.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, var3);
                  var0.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, var3);
                  var0.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, var3);
                  var0.setLockHP(false);
               }
            }
         }
      }
   }

   public static void logBlockedInteract(EntityGadget var0) {
      GatherData var1 = resolveGatherData(var0);
      Grasscutter.getLogger()
         .info(
            "GatherInteract blocked gadgetId={} pointType={} state={} enabled={} gatherItem={} controller={}",
            new Object[]{
               var0.getGadgetId(),
               var0.getPointType(),
               var0.getState(),
               computeClientInteractEnabled(var0),
               var1 != null ? var1.getItemId() : 0,
               resolveServerController(var0)
            }
         );
   }
}
