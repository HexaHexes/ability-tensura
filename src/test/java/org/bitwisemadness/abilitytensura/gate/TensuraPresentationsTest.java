package org.bitwisemadness.abilitytensura.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tensura's presentations (ability-tensura#66): the mastery math is pure
 * and never overstates (floored text, -1 for an unusable maximum), the
 * declaration rides core's loud registry, and a null-player read presents
 * the unchanged base - no guess ever reaches the menu. The live ARR reads
 * (getMastery/canBeToggled) are #52-class client state: documented manual
 * check, per the issue.
 */
class TensuraPresentationsTest {

    private static final ResourceLocation ABILITY =
            ResourceLocation.fromNamespaceAndPath("tensura", "fireball");

    @AfterEach
    void isolate() {
        PresentationProviders.remove(ABILITY);
    }

    @Test
    void masteryFractionScalesAndNeverGuesses() {
        assertEquals(0.25f, TensuraPresentations.masteryFraction(12.5, 50), 0.0001f,
                "mastery over maximum");
        assertEquals(1f, TensuraPresentations.masteryFraction(80, 50), 0.0001f,
                "over-max clamps to a full bar");
        assertEquals(0f, TensuraPresentations.masteryFraction(-5, 50), 0.0001f,
                "negative floors at empty");
        assertEquals(-1f, TensuraPresentations.masteryFraction(10, 0),
                "an unusable maximum presents no bar at all");
        assertEquals(-1f, TensuraPresentations.masteryFraction(Double.NaN, 50),
                "a non-finite read presents nothing");
    }

    @Test
    void masteryTextFloorsSoTheBarNeverOverstates() {
        assertEquals("12/50", TensuraPresentations.masteryText(12.0, 50));
        assertEquals("12/50", TensuraPresentations.masteryText(12.9, 50),
                "floor, not round - the bar and the text agree");
        assertEquals("0/10", TensuraPresentations.masteryText(-3, 10),
                "a nonsense read renders the floor, never a negative");
    }

    @Test
    void declarationRidesTheCoreRegistryLoudly() {
        TensuraPresentations.declare(ABILITY);
        assertTrue(PresentationProviders.has(ABILITY), "the shared provider is declared");
        assertThrows(IllegalStateException.class,
                () -> TensuraPresentations.declare(ABILITY), "one provider per id");
    }

    @Test
    void aNullPlayerPresentsNothing() {
        var base = TensuraPresentations.present(null, ABILITY);
        assertTrue(base.fields().isEmpty(), "no player, no guessed fields");
        assertNull(base.toggle(), "no player, no guessed toggle");
    }

    @Test
    void theMasteryLabelIsOurOwnNamespace() {
        assertEquals("ability_tensura.menu.field.mastery", TensuraPresentations.MASTERY_LABEL,
                "provider-owned key under our modid - never ARR's key space");
    }

    @Test
    void theStatusLabelIsOurOwnNamespace() {
        assertEquals("ability_tensura.menu.field.status", TensuraPresentations.STATUS_LABEL,
                "Known/Learned/Mastered reads under our modid - owner's brief");
    }
}
