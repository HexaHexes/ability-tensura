package org.bitwisemadness.abilitytensura.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The badge bonus state mapping (owner's brief: the mastery level shown
 * for Tensura abilities also counts the two bars - Learned adds 1,
 * Mastered adds 2): the acquisition phase (ARR's negative counter)
 * reads Known (+0), the learned phase +1, mastery at a usable cap +2,
 * and a cap of 0 (ARR abstention) never counts as mastered.
 */
class LevelBonusStateTest {

    @Test
    void acquisitionPhaseAddsNothing() {
        assertEquals(0, TensuraMastery.levelBonusForState(-50.0, 100),
                "a skill still being acquired is Known, not learned");
        assertEquals(0, TensuraMastery.levelBonusForState(-0.001, 100),
                "the phase boundary itself adds nothing");
    }

    @Test
    void learnedPhaseAddsOne() {
        assertEquals(1, TensuraMastery.levelBonusForState(0.0, 100),
                "at zero the skill is learned");
        assertEquals(1, TensuraMastery.levelBonusForState(49.9, 100),
                "progress below the cap adds one level");
        assertEquals(1, TensuraMastery.levelBonusForState(99.9, 100),
                "just under the cap still counts learned only");
    }

    @Test
    void masteredAddsTwo() {
        assertEquals(2, TensuraMastery.levelBonusForState(100.0, 100),
                "the cap itself is mastered");
        assertEquals(2, TensuraMastery.levelBonusForState(150.0, 100),
                "beyond the cap (SOFT growth) stays mastered");
    }

    @Test
    void unusableCapNeverCountsAsMastered() {
        assertEquals(1, TensuraMastery.levelBonusForState(0.0, 0),
                "ARR abstention (cap 0) can never read mastered");
        assertEquals(1, TensuraMastery.levelBonusForState(999.0, 0),
                "no cap means learned at best");
    }
}
