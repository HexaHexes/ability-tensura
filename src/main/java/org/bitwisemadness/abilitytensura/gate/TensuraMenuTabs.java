package org.bitwisemadness.abilitytensura.gate;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.bitwisemadness.abilitytensura.catalog.BattlewillCatalog;
import org.slf4j.Logger;

/**
 * Tensura's ability-menu tabs (hotbar#154's {@link MenuTabs} contract): the
 * three category tabs with the icons the brief names - skills and magic
 * borrow the creative-tab icons of Skill Study Books ({@code
 * tensura_skill_books:ultimate} = "Ultimate Skills", {@code
 * :magic_spiritual} = "Spiritual Magic" - ids pinned from its
 * DeferredRegister paths and {@code itemGroup} keys), battlewills carry
 * ARR's own manual item.
 *
 * <p>Declared like the presentation seam (#69): both sides at common setup,
 * gated on Tensura (an absent registry must never be read), with the two
 * borrowed icons additionally gated on Skill Study Books - without it those
 * two tabs simply do not register and the arts list under All (the
 * contract's declared-none reading). An unresolvable icon degrades to
 * tab-without-icon or no tab (warn), never a setup crash.</p>
 */
public final class TensuraMenuTabs {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SKILL_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "skill");
    private static final ResourceLocation MAGIC_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("tensura", "magic");

    private TensuraMenuTabs() {
    }

    /** Registers the three tabs - called from {@link UnlockGate#register()}. */
    public static void declare() {
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")) {
            return;
        }
        // Soft-hook guard first (the hotbar#120 callsite rule): without the
        // hotbar there is no menu to tab, and its classes must never link.
        if (!ModList.get().isLoaded("ability_hotbar")) {
            return;
        }
        if (ModList.get().isLoaded("tensura_skill_books")) {
            registerWithStack(ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                    "ability_tensura.tab.skill",
                    borrowedTabIcon("ultimate"), SKILL_CATEGORY);
            registerWithStack(ResourceLocation.fromNamespaceAndPath("tensura", "magic"),
                    "ability_tensura.tab.magic",
                    borrowedTabIcon("magic_spiritual"), MAGIC_CATEGORY);
        }
        registerWithStack(ResourceLocation.fromNamespaceAndPath("tensura", "battlewill"),
                "ability_tensura.tab.battlewill",
                manualIcon(), BattlewillCatalog.CATEGORY);
    }

    /**
     * The icon of Skill Study Books' own creative tab - the brief's
     * "same icon as" ruling. Null when the mod's tab id is unknown (the
     * caller then registers no tab rather than crashing common setup).
     */
    private static ItemStack borrowedTabIcon(String path) {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(
                ResourceLocation.fromNamespaceAndPath("tensura_skill_books", path));
        if (tab == null) {
            LOGGER.warn("Skill Study Books tab '{}:{}' not found - skipping the tab",
                    "tensura_skill_books", path);
            return null;
        }
        return tab.getIconItem();
    }

    /** ARR's battlewill manual - the brief's chosen battlewills icon. */
    private static ItemStack manualIcon() {
        return new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("tensura", "battlewill_manual")));
    }

    /**
     * Registers one category tab (membership = the definition's category),
     * degrading to no registration when the icon cannot be resolved: an
     * empty stack (missing item) or null (missing borrowed tab) logs once
     * and skips - the arts keep listing under All.
     */
    private static void registerWithStack(ResourceLocation id, String displayNameKey,
            ItemStack icon, ResourceLocation category) {
        if (icon == null || icon.isEmpty() || icon.is(Items.AIR)) {
            LOGGER.warn("Menu tab {} skipped: icon could not be resolved", id);
            return;
        }
        MenuTabs.register(id, displayNameKey, icon,
                (entryId, definition) -> category.equals(definition.category()));
    }
}
