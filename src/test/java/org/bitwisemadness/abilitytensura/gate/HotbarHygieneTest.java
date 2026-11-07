package org.bitwisemadness.abilitytensura.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.LoadoutPreset;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.junit.jupiter.api.Test;

/**
 * The removal half of the unlock gate (owner directive 2026-11-06): the
 * pure transform clears exactly the predicate's slots and preserves every
 * other shape - ids, selection, active bar, schema version (existing shapes
 * only, no ladder bump). Fixture-shaped like {@code refusalFor}.
 */
class HotbarHygieneTest {

    private static final ResourceLocation UNLEARNT =
            ResourceLocation.fromNamespaceAndPath("tensura", "unlearnt_skill");
    private static final ResourceLocation LEARNED =
            ResourceLocation.fromNamespaceAndPath("tensura", "learned_skill");
    private static final ResourceLocation FOREIGN =
            ResourceLocation.fromNamespaceAndPath("other", "their_ability");
    private static final ResourceLocation BAR_ONE =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "bar_one");
    private static final ResourceLocation BAR_TWO =
            ResourceLocation.fromNamespaceAndPath("ability_tensura", "bar_two");

    private static BarState fixture() {
        return new BarState(
                new Bar[] {
                    new Bar(BAR_ONE, new SlotAssignment[] {
                        new SlotAssignment(UNLEARNT),
                        new SlotAssignment(LEARNED),
                        new SlotAssignment(null)
                    }, 1),
                    new Bar(BAR_TWO, new SlotAssignment[] {
                        new SlotAssignment(FOREIGN)
                    }, 0)
                },
                BAR_ONE,
                3,
                new LoadoutPreset[0]);
    }

    @Test
    void clearsMatchingSlotsAndKeepsEverythingElse() {
        Optional<BarState> out = HotbarHygiene.transform(fixture(),
                id -> id.equals(UNLEARNT));
        assertTrue(out.isPresent(), "a cleared slot must produce a next state");

        BarState next = out.get();
        assertEquals(2, next.bars().length, "bar count must be preserved");
        assertEquals(BAR_ONE, next.activeBarId(), "active bar must be preserved");
        assertEquals(3, next.schemaVersion(), "schema version must not bump");

        SlotAssignment[] one = next.bars()[0].assignments();
        assertNull(one[0].abilityId(), "the unlearnt slot must be cleared");
        assertEquals(LEARNED, one[1].abilityId(), "the learned slot must stay");
        assertNull(one[2].abilityId(), "the already-empty slot stays empty");
        assertEquals(1, next.bars()[0].selectedSlot(), "selection must be preserved");

        assertEquals(FOREIGN, next.bars()[1].assignments()[0].abilityId(),
                "foreign abilities are not ours to remove");
        assertEquals(BAR_TWO, next.bars()[1].id(), "bar ids must be preserved");
    }

    @Test
    void nothingMatchingProducesNoNextState() {
        assertTrue(HotbarHygiene.transform(fixture(),
                id -> id.equals(ResourceLocation.fromNamespaceAndPath("tensura", "absent"))).isEmpty(),
                "no matches must mean nothing to persist or echo");
    }

    @Test
    void predicateDecidesAcrossAllBars() {
        // The mechanism is predicate-agnostic: the login sweep uses
        // tracked-and-not-learned, the REMOVE event the removed id.
        Optional<BarState> out = HotbarHygiene.transform(fixture(), id -> id.equals(FOREIGN));
        assertTrue(out.isPresent());
        assertNull(out.get().bars()[1].assignments()[0].abilityId(),
                "the predicate's match must clear on any bar");
        assertEquals(UNLEARNT, out.get().bars()[0].assignments()[0].abilityId(),
                "non-matching slots must stay");
        assertFalse(out.get().bars()[0].assignments()[0].abilityId() == null,
                "the unlearnt slot is untouched by a foreign-id predicate");
    }
}
