package org.bitwisemadness.abilitytensura.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Tensura's server policy (the fleet audit's tensura entry):
 * {@code ability_tensura-server.toml} - auto-synced, per-world
 * overridable, the only authority channel.
 *
 * <p><b>Read timing is this class's contract:</b> NeoForge loads SERVER
 * configs at <b>server start</b> - after mod construction and after the
 * registry phase - so every key here is read at <b>use time</b> (a gate
 * evaluation, an activation, a preset mapping), never at registration.
 * Registration always happens; the key decides what the running code
 * does.</p>
 */
public final class TensuraServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Whether an unlearned art refuses activation (the gate's rule). */
    public static final ModConfigSpec.BooleanValue REQUIRE_UNLOCK =
            BUILDER.comment("Refuse activating an art the player has not learned in Tensura "
                            + "(the unlock gate's eligibility rule)")
                    .define("gate.requireUnlock", true);

    /** Forgetting a skill clears its assignments from the bars. */
    public static final ModConfigSpec.BooleanValue REMOVE_UNLEARNED_FROM_BARS =
            BUILDER.comment("When a skill is forgotten, clear it from every hotbar bar - "
                            + "and sweep unlearned assignments at login")
                    .define("gate.removeUnlearnedFromBars", true);

    /** Menu listing rule for unlearned arts. */
    public static final ModConfigSpec.BooleanValue HIDE_UNLEARNED_IN_MENU =
            BUILDER.comment("Hide arts the player has not learned from the ability menu "
                            + "(known and learned arts always list)")
                    .define("gate.hideUnlearnedInMenu", true);

    /** Mastery floor before a merely known art lists. */
    public static final ModConfigSpec.IntValue KNOWN_MIN_MASTERY =
            BUILDER.comment("Minimum mastery before a merely known art lists in the menu")
                    .defineInRange("gate.knownMinMastery", 0, 0, 10000);

    /** Mastery floor before a resistance lists. */
    public static final ModConfigSpec.IntValue RESISTANCE_MIN_MASTERY =
            BUILDER.comment("Minimum mastery before a resistance lists in the menu")
                    .defineInRange("gate.resistanceMinMastery", 0, 0, 10000);

    /** How a hotbar bar maps onto ARR's preset slots. */
    public static final ModConfigSpec.EnumValue<PresetMapping> PRESET_MAPPING =
            BUILDER.comment("How hotbar bars map onto ARR's preset slots: TAIL gives bar 0 "
                            + "the last preset (native presets untouched), HEAD the first, "
                            + "OFF keeps ARR's own active preset")
                    .defineEnum("activation.presetMapping", PresetMapping.TAIL);

    /** Casting equips an art into the preset on demand. */
    public static final ModConfigSpec.BooleanValue AUTO_EQUIP_ON_CAST =
            BUILDER.comment("Cast equips on demand: an art that is not in ARR's preset is "
                            + "placed in the first free slot (a full preset refuses)")
                    .define("activation.autoEquipOnCast", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** The bar-to-preset mapping policy (rendered by name in a config screen). */
    public enum PresetMapping implements TranslatableEnum {
        /** Bar 0 owns ARR's last preset (the shipped tail mapping). */
        TAIL,
        /** Bar 0 owns ARR's first preset. */
        HEAD,
        /** No mapping: ARR's own active preset decides (the legacy no-op). */
        OFF;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(translationKey());
        }

        /** Our own key convention (hotbar's BarAnchor shape), not an interface method. */
        public String translationKey() {
            return "ability_tensura.preset_mapping." + name().toLowerCase(Locale.ROOT);
        }
    }

    private TensuraServerConfig() {
    }
}
