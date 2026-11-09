package org.bitwisemadness.abilitytensura.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilitycore.config.ConfigDiagnostics;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.bitwisemadness.abilitytensura.config.TensuraClientConfig;

/**
 * Tensura's registered menu tabs (ability-tensura#75, refined by the
 * owner's icon brief): **Skills, Magic and Battlewill** as three tabs in
 * the hotbar's menu, each carrying the brief's icon - battlewills the
 * {@code tensura:battlewill_manual} item, skills/magic the icons of Skill
 * Study Books' own creative tabs ({@code tensura_skill_books:ultimate} =
 * "Ultimate Skills", {@code :magic_spiritual} = "Spiritual Magic") - with
 * ARR's tab sprite registered alongside as the fallback whenever the item
 * cannot be resolved (mod absent, registries headless) <b>or renders
 * nothing</b> (skill-books' study-book model empties every vanilla GUI
 * context, so the menu draws its sprite instead of a blank tab).
 * Membership over the definition's category (pure predicates, no upstream
 * reads at query time).
 *
 * <p>Guarded callsite: hotbar is an <b>optional</b> dependency of this
 * mod, so {@link #register} checks both mods and {@link #registerAll}
 * - the only place a hotbar class is referenced - runs behind that check
 * (the {@code HotbarHygiene} pattern: the guarded method is never linked
 * when hotbar is absent). Without Tensura, {@link UnlockGate#register}
 * never runs at all; without hotbar, this class registers nothing.</p>
 */
public final class TensuraMenuTabs {

    private static final ResourceLocation SKILLS_TAB =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "skills");
    private static final ResourceLocation MAGIC_TAB =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "magic");
    private static final ResourceLocation BATTLEWILL_TAB =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "battlewill");

    /** ARR's own tab icons, referenced by location (ADR-005) - the fallback. */
    private static final ResourceLocation SKILL_ICON =
            ResourceLocation.fromNamespaceAndPath("tensura",
                    "textures/gui/skill/skill_tab_icon");
    private static final ResourceLocation MAGIC_ICON =
            ResourceLocation.fromNamespaceAndPath("tensura",
                    "textures/gui/magic/magic_tab_icon");
    private static final ResourceLocation BATTLEWILL_ICON =
            ResourceLocation.fromNamespaceAndPath("tensura",
                    "textures/gui/battlewill/battlewill_tab_icon");

    /**
     * Our subcategory tags to ARR's own per-category art (owner: the
     * chips were missing their icons - skill-books' study-book stack
     * renders nothing in a GUI context, so these are SPRITES: skill and
     * magic group buttons, battlewill's tab icon as its family mark).
     */
    private static final Map<String, String> CHIP_SPRITES = Map.ofEntries(
            Map.entry("type:common", "textures/gui/skill/common"),
            Map.entry("type:extra", "textures/gui/skill/extra"),
            Map.entry("type:intrinsic", "textures/gui/skill/intrinsic"),
            Map.entry("type:resistance", "textures/gui/skill/resistance"),
            Map.entry("type:unique", "textures/gui/skill/unique"),
            Map.entry("type:ultimate", "textures/gui/skill/ultimate"),
            Map.entry("type:aspectual", "textures/gui/magic/aspectual_magic_button"),
            Map.entry("type:spiritual", "textures/gui/magic/spiritual_magic_button"),
            Map.entry("type:summoning", "textures/gui/magic/summoning_magic_button"),
            Map.entry("type:melee", "textures/gui/battlewill/battlewill_tab_icon"),
            Map.entry("type:projectile", "textures/gui/battlewill/battlewill_tab_icon"),
            Map.entry("type:utility", "textures/gui/battlewill/battlewill_tab_icon"));

    private TensuraMenuTabs() {
    }

    /** Setup entry (hotbar optional): registers the three tabs when both mods are present. */
    public static void register() {
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")
                || !ModList.get().isLoaded("ability_hotbar")) {
            return;
        }
        registerAll();
    }

    /**
     * The registrations themselves (the linkable half - tests call this
     * directly): the three tabs ride hotbar's declarable CategoryTab
     * family (generalized for every provider, hotbar#183).
     *
     * <p>The pack chooses which tabs appear, their order and the Skills
     * strip's order ({@code menu.tabs.*}, {@code menu.tabOrder},
     * {@code menu.skillsSubCategoryOrder}), and whether chips carry their
     * sprite ({@code menu.showChipIcons}). This runs at common setup,
     * which also happens on dedicated servers where the CLIENT spec is
     * never loaded - so every read goes through core's
     * {@code readOrDefault} and falls back to the shipped default.</p>
     */
    static void registerAll() {
        // membership is definition.category() equality; icons keep their
        // both-forms degrade; the skills strip keeps the owner's
        // progression order unless the pack reorders it
        List<MenuTabs.CategoryTab> tabs = new ArrayList<>();
        for (String which : tabOrder()) {
            if (!tabShown(which)) {
                continue;
            }
            switch (which) {
                case "skills" -> tabs.add(new MenuTabs.CategoryTab(SKILLS_TAB,
                        "ability_tensura.tab.skills", SKILL_ICON, borrowedTabIcon("ultimate"),
                        ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                        skillsSubOrder(), null));
                case "magic" -> tabs.add(new MenuTabs.CategoryTab(MAGIC_TAB,
                        "ability_tensura.tab.magic", MAGIC_ICON, borrowedTabIcon("magic_spiritual"),
                        ResourceLocation.fromNamespaceAndPath("tensura", "magic"),
                        null, null));
                case "battlewill" -> tabs.add(new MenuTabs.CategoryTab(BATTLEWILL_TAB,
                        "ability_tensura.tab.battlewill", BATTLEWILL_ICON, manualIcon(),
                        ResourceLocation.fromNamespaceAndPath("tensura", "battlewill"),
                        null, null));
                default -> { /* unknown entry: the spec's validator already refused it */ }
            }
        }
        if (!tabs.isEmpty()) {
            MenuTabs.registerCategoryTabs(tabs);
        }
        if (chipIconsShown()) {
            registerChipIcons();
        }
    }

    /** The pack's tab order; the shipped order when the spec is unloaded. */
    private static List<String> tabOrder() {
        return List.copyOf(ConfigDiagnostics.readOrDefault("menu.tabOrder",
                () -> TensuraClientConfig.TAB_ORDER.get(),
                List.of("skills", "magic", "battlewill")));
    }

    /** Whether one tab appears (menu.tabs.*); shipped default when unloaded. */
    private static boolean tabShown(String which) {
        return switch (which) {
            case "skills" -> ConfigDiagnostics.readOrDefault("menu.tabs.skills",
                    () -> TensuraClientConfig.TAB_SKILLS.get(), true);
            case "magic" -> ConfigDiagnostics.readOrDefault("menu.tabs.magic",
                    () -> TensuraClientConfig.TAB_MAGIC.get(), true);
            case "battlewill" -> ConfigDiagnostics.readOrDefault("menu.tabs.battlewill",
                    () -> TensuraClientConfig.TAB_BATTLEWILL.get(), true);
            default -> false;
        };
    }

    /** The Skills strip's order; the owner's progression when unloaded. */
    private static List<String> skillsSubOrder() {
        List<? extends String> configured = ConfigDiagnostics.readOrDefault(
                "menu.skillsSubCategoryOrder",
                () -> TensuraClientConfig.SKILLS_SUB_CATEGORY_ORDER.get(),
                List.of("type:resistance", "type:intrinsic", "type:common",
                        "type:extra", "type:unique", "type:ultimate"));
        return List.copyOf(configured);
    }

    /** Whether chips carry their sprite; shipped default when unloaded. */
    private static boolean chipIconsShown() {
        return ConfigDiagnostics.readOrDefault("menu.showChipIcons",
                () -> TensuraClientConfig.SHOW_CHIP_ICONS.get(), true);
    }

    /**
     * Chip icons from ARR's own per-category art (owner: the chips were
     * missing their icons - SPRITES, not item stacks, because a study
     * book's baked model renders nothing in any vanilla GUI context).
     * Each tag maps to its tensura texture; unknown paths never register.
     */
    private static void registerChipIcons() {
        for (var entry : CHIP_SPRITES.entrySet()) {
            MenuTabs.subCategoryIcon(entry.getKey(),
                    ResourceLocation.fromNamespaceAndPath("tensura", entry.getValue()),
                    null);
        }
    }

    /**
     * Skill Study Books' creative-tab icon - the brief's "same icon as"
     * ruling for skills/magic. Null means the sprite-only registration.
     */
    private static ItemStack borrowedTabIcon(String path) {
        try {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(
                    ResourceLocation.fromNamespaceAndPath("tensura_skill_books", path));
            return tab == null ? null : tab.getIconItem();
        } catch (Throwable unavailable) {
            return null; // headless fixtures run without bootstrapped registries
        }
    }

    /** ARR's battlewill manual - the brief's battlewills icon (null = sprite fallback). */
    private static ItemStack manualIcon() {
        try {
            // The item registry is defaulted: an unknown id reads AIR, and
            // the empty stack below routes to the sprite fallback.
            Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("tensura", "battlewill_manual"));
            return new ItemStack(item);
        } catch (Throwable unavailable) {
            return null; // headless fixtures run without bootstrapped registries
        }
    }
}
