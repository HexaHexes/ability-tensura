package org.bitwisemadness.abilitytensura.gate;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilityhotbar.network.BarStateSync;
import org.bitwisemadness.abilityhotbar.state.Bar;
import org.bitwisemadness.abilityhotbar.state.BarState;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;

/**
 * The hotbar half of the unlock gate (owner directive, 2026-11-06):
 * <b>unlearnt abilities are removed from the player</b> - their bar
 * assignments are cleared, persisted and echoed.
 *
 * <p><b>Soft-hook discipline (REPO.md: ability_hotbar is optional; the
 * hotbar#120 linkage lesson):</b> this class references hotbar types, so it
 * must only ever be reached from call sites <i>inside</i>
 * {@code ModList.isLoaded("ability_hotbar")} guard branches - a guarded
 * branch never links the call when hotbar is absent, and the gametest boots
 * without hotbar as the living proof. Nothing else in the gate touches it.</p>
 *
 * <p>The decision half is {@link #transform}: pure over the state record
 * (fixture-able like {@code refusalFor}), predicate-driven so the caller
 * decides what counts as unlearnt (login sweep = tracked-and-not-learned;
 * the REMOVE event = the removed id itself, whose authority is the event -
 * ARR still lists the skill mid-removal, so presence checks would lie).</p>
 */
final class HotbarHygiene {

    private HotbarHygiene() {
    }

    /**
     * Clears every non-null slot whose ability matches the predicate, bar
     * by bar; ids and shapes are otherwise preserved (bar ids, selection,
     * schema version - no ladder bump: this writes existing shapes only).
     *
     * @param state     the player's current bar state
     * @param clearSlot which abilities lose their assignment
     * @return the next state, or empty when nothing matched (nothing to
     *         persist or echo)
     */
    static Optional<BarState> transform(BarState state, Predicate<ResourceLocation> clearSlot) {
        boolean changed = false;
        Bar[] bars = state.bars();
        Bar[] next = new Bar[bars.length];
        for (int i = 0; i < bars.length; i++) {
            Bar bar = bars[i];
            SlotAssignment[] assignments = bar.assignments();
            SlotAssignment[] copy = Arrays.copyOf(assignments, assignments.length);
            for (int slot = 0; slot < copy.length; slot++) {
                ResourceLocation id = copy[slot].abilityId();
                if (id != null && clearSlot.test(id)) {
                    copy[slot] = new SlotAssignment(null);
                    changed = true;
                }
            }
            next[i] = new Bar(bar.id(), copy, bar.selectedSlot());
        }
        return changed
                ? Optional.of(new BarState(next, state.activeBarId(), state.schemaVersion(),
                        state.presets()))
                : Optional.empty();
    }

    /**
     * Server-side apply: transform the player's attachment, persist the
     * result and echo it - hotbar's own accept* one-two, using its public
     * sync point. Call only behind the hotbar presence guard.
     */
    static void apply(ServerPlayer player, Predicate<ResourceLocation> clearSlot) {
        BarState state = player.getData(BarStateAttachments.BAR_STATE);
        transform(state, clearSlot).ifPresent(next -> {
            player.setData(BarStateAttachments.BAR_STATE, next);
            BarStateSync.sendSnapshot(player);
        });
    }
}
