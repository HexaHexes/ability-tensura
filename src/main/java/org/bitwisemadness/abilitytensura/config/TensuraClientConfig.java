package org.bitwisemadness.abilitytensura.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Tensura's client preferences (the fleet audit's tensura entry):
 * {@code ability_tensura-client.toml} - display-only, never synced,
 * never policy.
 *
 * <p><b>Read timing:</b> CLIENT configs load during the loading screen,
 * <b>after</b> the registry phase but <b>before</b> common setup - so
 * keys consumed while this mod registers its menu tabs (common setup)
 * read fine on the physical client. Common setup also runs on dedicated
 * servers, where the CLIENT spec never loads: those reads go through
 * core's {@code ConfigDiagnostics.readOrDefault} and fall back to the
 * shipped default instead of throwing.</p>
 */
public final class TensuraClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Whether the Skills tab appears. */
    public static final ModConfigSpec.BooleanValue TAB_SKILLS =
            BUILDER.comment("Show the Skills tab in the ability menu")
                    .define("menu.tabs.skills", true);

    /** Whether the Magic tab appears. */
    public static final ModConfigSpec.BooleanValue TAB_MAGIC =
            BUILDER.comment("Show the Magic tab in the ability menu")
                    .define("menu.tabs.magic", true);

    /** Whether the Battlewill tab appears. */
    public static final ModConfigSpec.BooleanValue TAB_BATTLEWILL =
            BUILDER.comment("Show the Battlewill tab in the ability menu")
                    .define("menu.tabs.battlewill", true);

    /** Whether subcategory chips carry their ARR sprite. */
    public static final ModConfigSpec.BooleanValue SHOW_CHIP_ICONS =
            BUILDER.comment("Draw the subcategory chips' sprite icons (off = text-only chips)")
                    .define("menu.showChipIcons", true);

    /** The Skills strip's subcategory order. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SKILLS_SUB_CATEGORY_ORDER =
            BUILDER.comment("Subcategory order of the Skills tab's filter strip")
                    .defineListAllowEmpty("menu.skillsSubCategoryOrder", List.of(
                                    "type:resistance", "type:intrinsic", "type:common",
                                    "type:extra", "type:unique", "type:ultimate"),
                            value -> value instanceof String text && !text.isBlank());

    /** The tab order (registration order drives the strip). */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAB_ORDER =
            BUILDER.comment("Order of this mod's menu tabs: any of skills, magic, battlewill")
                    .defineListAllowEmpty("menu.tabOrder",
                            List.of("skills", "magic", "battlewill"),
                            value -> value instanceof String text
                                    && (text.equals("skills") || text.equals("magic")
                                            || text.equals("battlewill")));

    /** Whether the bridge swaps Tensura Evolution's equip screens. */
    public static final ModConfigSpec.BooleanValue REPLACE_TRS_EQUIP_SCREEN =
            BUILDER.comment("Replace Tensura Evolution's equip-skills screens with this "
                            + "mod's ability menu (when both mods are loaded)")
                    .define("menu.replaceTrsEquipScreen", true);

    /** Whether rows show the Mastery field. */
    public static final ModConfigSpec.BooleanValue SHOW_MASTERY =
            BUILDER.comment("Show the Mastery progress field on menu rows")
                    .define("menu.showMastery", true);

    /** Whether rows show the Status field. */
    public static final ModConfigSpec.BooleanValue SHOW_STATUS =
            BUILDER.comment("Show the Status field (Known / Learned / Mastered) on menu rows")
                    .define("menu.showStatus", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TensuraClientConfig() {
    }
}
