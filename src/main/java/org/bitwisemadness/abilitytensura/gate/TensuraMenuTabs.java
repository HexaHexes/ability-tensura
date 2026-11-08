package org.bitwisemadness.abilitytensura.gate;

import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;

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

    /** Our subcategory tags to Tensura Skill Books' per-category creative
     * tabs - the study-book stack whose texture differs per subcategory. */
    private static final Map<String, String> SKILL_BOOK_CHIPS = Map.ofEntries(
            Map.entry("type:common", "common"),
            Map.entry("type:extra", "extra"),
            Map.entry("type:intrinsic", "intrinsic"),
            Map.entry("type:resistance", "resistance"),
            Map.entry("type:unique", "unique"),
            Map.entry("type:ultimate", "ultimate"),
            Map.entry("type:aspectual", "magic_aspectual"),
            Map.entry("type:spiritual", "magic_spiritual"),
            Map.entry("type:summoning", "magic_summoning"));

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
     */
    static void registerAll() {
        // membership is definition.category() equality; icons keep their
        // both-forms degrade; the skills strip keeps the owner's
        // progression order (ultimate reserved - the pack never tags it)
        MenuTabs.registerCategoryTabs(List.of(
                new MenuTabs.CategoryTab(SKILLS_TAB, "ability_tensura.tab.skills",
                        SKILL_ICON, borrowedTabIcon("ultimate"),
                        ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                        List.of("type:resistance", "type:intrinsic", "type:common",
                                "type:extra", "type:unique", "type:ultimate"),
                        null),
                new MenuTabs.CategoryTab(MAGIC_TAB, "ability_tensura.tab.magic",
                        MAGIC_ICON, borrowedTabIcon("magic_spiritual"),
                        ResourceLocation.fromNamespaceAndPath("tensura", "magic"),
                        null, null),
                new MenuTabs.CategoryTab(BATTLEWILL_TAB, "ability_tensura.tab.battlewill",
                        BATTLEWILL_ICON, manualIcon(),
                        ResourceLocation.fromNamespaceAndPath("tensura", "battlewill"),
                        null, null)));
        registerChipIcons();
    }

    /**
     * Chip icons from Skill Books' per-category study books (owner: the
     * mod shows a different texture per subcategory). Headless fixtures
     * resolve no stack - the strip degrades to text, never a crash.
     */
    private static void registerChipIcons() {
        for (var entry : SKILL_BOOK_CHIPS.entrySet()) {
            ItemStack stack = borrowedTabIcon(entry.getValue());
            if (stack != null && !stack.isEmpty()) {
                MenuTabs.subCategoryIcon(entry.getKey(), null, stack);
            }
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
