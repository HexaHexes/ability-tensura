package org.bitwisemadness.abilitytensura.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.AmountResolvers;
import org.bitwisemadness.abilitycore.api.ConsumptionStrategies;
import org.bitwisemadness.abilityhotbar.registry.MenuTabs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tensura's menu tabs (ability-tensura#75, the owner's icon brief refined
 * by hotbar#171): the three registrations carry **both identifier forms**
 * - the brief's preferred items as the draw preference (null headless -
 * the degrade rule) and ARR's tab sprites as the fallback - with category
 * membership (pure predicates), the guarded
 * entry registers nothing when the mods are absent (headless: ModList is
 * null), and the loud contract is MenuTabs' own.
 */
class TensuraMenuTabsTest {

    private static final ResourceLocation SKILLS =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "skills");
    private static final ResourceLocation MAGIC =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "magic");
    private static final ResourceLocation BATTLEWILL =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "battlewill");

    @AfterEach
    void isolate() {
        MenuTabs.remove(SKILLS);
        MenuTabs.remove(MAGIC);
        MenuTabs.remove(BATTLEWILL);
    }

    private static AbilityDefinition inCategory(String path) {
        return new AbilityDefinition("tensura.skill.x", null,
                ResourceLocation.fromNamespaceAndPath("tensura", path),
                ActivationType.INSTANT, List.of(), ConsumptionStrategies.ALL,
                AmountResolvers.STATIC, java.util.Set.of(), java.util.Map.of());
    }

    @Test
    void theGuardedEntryRegistersNothingWhenModsAreAbsent() {
        // headless: ModList is null, so the guard returns before any hotbar
        // class is touched - no crash, no tabs (the optional-mod invariant)
        TensuraMenuTabs.register();
        assertTrue(MenuTabs.all().isEmpty(),
                "no Tensura/hotbar in the environment, no tabs registered");
    }

    @Test
    void theThreeTabsRegisterWithIconsAndCategoryMembership() {
        TensuraMenuTabs.registerAll();
        List<MenuTabs.MenuTab> tabs = MenuTabs.all();
        assertEquals(List.of(SKILLS, MAGIC, BATTLEWILL),
                tabs.stream().map(MenuTabs.MenuTab::id).toList(),
                "strip order follows registration order");

        MenuTabs.MenuTab skills = MenuTabs.get(SKILLS).orElseThrow();
        assertEquals(ResourceLocation.fromNamespaceAndPath("tensura",
                "textures/gui/skill/skill_tab_icon"), skills.iconId(),
                "ARR's own tab icon, referenced by location (ADR-005)");
        assertTrue(skills.hasIcon());
        assertNull(skills.icon(),
                "headless: the borrowed creative tab is unresolvable - both-forms "
                        + "registration degrades to the sprite (hotbar#171)");
        assertTrue(skills.accepts().test(
                ResourceLocation.fromNamespaceAndPath("tensura", "fireball"),
                inCategory("skill")), "a skill is a skill");
        assertFalse(skills.accepts().test(
                ResourceLocation.fromNamespaceAndPath("tensura", "fireball"),
                inCategory("magic")), "membership is the category, not the namespace");

        MenuTabs.MenuTab battlewill = MenuTabs.get(BATTLEWILL).orElseThrow();
        assertTrue(battlewill.accepts().test(
                ResourceLocation.fromNamespaceAndPath("tensura", "aura_slash"),
                inCategory("battlewill")));
    }

    @Test
    void doubleRegistrationIsLoud() {
        TensuraMenuTabs.registerAll();
        assertThrows(IllegalStateException.class, TensuraMenuTabs::registerAll,
                "MenuTabs owns the duplicate rule - loud, never silent");
    }
}
