package org.bitwisemadness.abilitytensura.gate;

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
 * ARR's tab sprite as the fallback whenever an item cannot be resolved
 * (mod absent, registries headless). Membership over the definition's
 * category (pure predicates, no upstream reads at query time).
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

    /** The registrations themselves (the linkable half - tests call this directly). */
    static void registerAll() {
        registerTab(SKILLS_TAB, "ability_tensura.tab.skills", SKILL_ICON,
                borrowedTabIcon("ultimate"), inCategory("tensura", "skill"));
        registerTab(MAGIC_TAB, "ability_tensura.tab.magic", MAGIC_ICON,
                borrowedTabIcon("magic_spiritual"), inCategory("tensura", "magic"));
        registerTab(BATTLEWILL_TAB, "ability_tensura.tab.battlewill", BATTLEWILL_ICON,
                manualIcon(), inCategory("tensura", "battlewill"));
    }

    /**
     * Registers one tab with the brief's preferred item icon, falling back
     * to ARR's own tab sprite whenever the stack cannot be resolved -
     * skill-books' tab not registered, registries not bootstrapped
     * (headless fixtures), or a lookup failure. Degrades, never throws.
     */
    private static void registerTab(ResourceLocation id, String displayNameKey,
            ResourceLocation fallbackSprite, ItemStack preferred,
            java.util.function.BiPredicate<ResourceLocation,
                    org.bitwisemadness.abilitycore.api.AbilityDefinition> accepts) {
        if (preferred != null && !preferred.isEmpty()) {
            MenuTabs.register(id, displayNameKey, preferred, accepts);
        } else {
            MenuTabs.register(id, displayNameKey, fallbackSprite, accepts);
        }
    }

    /**
     * Skill Study Books' creative-tab icon - the brief's "same icon as"
     * ruling for skills/magic. Null means the sprite fallback.
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

    /** Membership: entries whose definition carries this exact category. */
    private static java.util.function.BiPredicate<ResourceLocation,
            org.bitwisemadness.abilitycore.api.AbilityDefinition> inCategory(
            String namespace, String path) {
        ResourceLocation category = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return (id, definition) -> definition.category().equals(category);
    }
}
