package org.bitwisemadness.abilitytensura.activation;

import com.mojang.logging.LogUtils;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.network.c2s.RequestAbilityModeChangePacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.storage.ability.IAbility;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.CooldownOwners;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilitycore.api.ModeChangeRequest;
import org.bitwisemadness.abilitycore.api.ModeOwners;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;
import org.slf4j.Logger;

/**
 * The single shared Tensura activation service (#42's selected approach:
 * one service, never per-feature {@code startHoldSkill} calls - drift
 * risk). Public so REQ-ISS-06 can consume it later via the cross-repo
 * contract (DEC-02-family placement decision at readiness).
 *
 * <p><b>Native parity is the contract</b> (AC2): a slot activation must
 * produce the same observable effects as ARR's own keybind. Everything
 * below was pinned by bytecode against {@code manascore-skill 4.0.0.2} /
 * {@code tensura-neoforge 2.0.1.3} (ability-tensura#52 findings, extended):</p>
 *
 * <ul>
 *   <li>{@code startHoldSkill(id, int, int)}'s two ints are
 *       <b>(keyNumber, mode)</b> - the request record
 *       {@code (keyNumber, skillId, mode)} names them; mode is
 *       bounds-checked against {@code getModes()} and indexes the per-mode
 *       cooldown; keyNumber flows into {@code onPressed}.</li>
 *   <li>Tensura's own {@code ACTIVATE_SKILL} listener
 *       ({@code AbilityHandler}) <b>interrupts unless the ability sits in
 *       the active preset at that keyNumber with that mode</b>
 *       ({@code isAbilityInActivePreset(int, skill, int)}), and ARR preset
 *       slots store exactly {@code (skill, mode)} - so parity means
 *       resolving both values <b>server-side from the player's own
 *       preset</b>, never from client input.</li>
 *   <li>ARR messages its own cooldown refusal (red chat line) - our
 *       handler reads the same state so core's pre-activation gate
 *       carries the matching outcome, no second messaging.</li>
 * </ul>
 *
 * <p>Fail-closed throughout (the direct-invocation precedent from
 * ability-iss): unlearned refuses RESTRICTED (defense in depth behind the
 * eligibility rule), not-equipped-in-preset refuses RESTRICTED (the native
 * keybind cannot fire either - parity), stale preset mode refuses
 * UNAVAILABLE, cooldown refuses COOLDOWN through core's pre-activation
 * gate (ARR has messaged), and a
 * remaining {@code false} from the pipeline refuses UNAVAILABLE with
 * whatever specific reason ARR could not surface as a code (ability-iss'
 * boolean-mapping precedent; core's refusal log carries it).</p>
 *
 * <p>Mode context from ability-core#113 (per-slot mode overrides,
 * hotbar#29) is a future input: when {@code ActivationRequest} carries a
 * mode, prefer it over the preset's stored mode. Today the ARR preset's
 * own mode <i>is</i> the native semantics.</p>
 */
public final class TensuraActivation {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TensuraActivation() {
    }

    /**
     * Registers one shared handler per tracked ability on core's pipeline
     * (NATIVE charging: ARR owns every cost). Gated on Tensura - the
     * classes in this file are upstream types.
     */
    public static void registerHandlers() {
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")) {
            return;
        }
        for (ResourceLocation id : UnlockGate.trackedIds()) {
            AbilityCore.HANDLERS.register(id, TensuraActivation::activate,
                    ChargingModel.NATIVE);
            // Cooldown defers to ARR's own per-skill, per-mode window
            // (core's HANDLER contract, alpha.45): core gates with this
            // read, ARR stays the sole owner and start site of its state.
            CooldownOwners.declareHandler(id, TensuraActivation::cooldownRemaining);
            // The mode-change domain half (#53): core routes the hotbar's
            // intent here and ARR's native body moves the mode - no second
            // mode logic exists in this repo.
            ModeChangeHandlers.register(id, TensuraActivation::changeMode);
            // The mode vocabulary + live projection (core#113): what
            // hotbar#29's resolver validates stored modes against and what
            // the mode keybind cycles/displays (single-mode arts declare none).
            if (declareModes(id)) {
                ModeOwners.declareNative(id, TensuraActivation::modeProjection);
            }
        }
    }

    /**
     * The shared activation: learn-gate, preset parity resolve, then the
     * native pipeline entry.
     */
    public static ActivationResult activate(ActivationRequest request, ServerPlayer player) {
        ResourceLocation id = request.abilityId();
        Optional<ManasSkillInstance> maybeInstance =
                SkillAPI.getSkillsFrom(player).getSkill(id);
        if (maybeInstance.isEmpty()) {
            // Defense in depth behind the eligibility rule: a direct
            // handler invocation must fail closed, never guess.
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        ManasSkillInstance instance = maybeInstance.get();

        // The pane's Enable/Disable (hotbar#148): MENU + no slot is the
        // toggle intent - ARR owns toggle state (setToggled +
        // onToggleOn/Off), never the hold pipeline or its gates.
        if (request.source() == ActivationSource.MENU
                && request.slotContext() == ActivationRequest.NO_SLOT) {
            if (!instance.canBeToggled(player)) {
                return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
            }
            boolean togglingOn = !instance.isToggled();
            instance.setToggled(togglingOn);
            if (togglingOn) {
                instance.onToggleOn(player);
            } else {
                instance.onToggleOff(player);
            }
            instance.markDirty();
            return ActivationResult.ACTED;
        }

        int[] slotAndMode = resolvePresetSlot(player, instance);
        if (slotAndMode == null) {
            // Not in the active preset: the native keybind cannot fire
            // either (AbilityHandler interrupts) - same gate, same refusal.
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        int slot = slotAndMode[0];
        int mode = slotAndMode[1];
        if (mode < 0 || mode >= instance.getModes()) {
            // The preset holds a mode this skill no longer offers: stale
            // config, fail closed (startHoldSkill would reject it anyway).
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        if (!SkillAPI.getSkillsFrom(player).startHoldSkill(id, slot, mode)) {
            // Boolean-only domain: no stable codes for the rest (mastery,
            // policy, energy) - the ability-iss precedent carries
            // UNAVAILABLE; core's refusal log records the detail.
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        return ActivationResult.ACTED;
    }

    /**
     * The per-skill, per-mode cooldown gate (core's HANDLER contract,
     * alpha.45): ARR's own window, resolved with the same preset slot the
     * activation uses, so core's pre-activation gate and {@link #activate}
     * can never disagree. Not-learned, not-equipped, and stale-preset
     * cases answer 0 - those are RESTRICTED/UNAVAILABLE verdicts, not
     * cooldown questions. Mode context (#113) stays future input exactly
     * as in {@link #activate}: the preset's mode is today's semantics.
     */
    public static long cooldownRemaining(ActivationRequest request, ServerPlayer player) {
        // The toggle intent is not a cooldown question (hotbar#148's
        // Enable/Disable must work while the hold window cools)
        if (request.source() == ActivationSource.MENU
                && request.slotContext() == ActivationRequest.NO_SLOT) {
            return 0L;
        }
        Optional<ManasSkillInstance> maybeInstance =
                SkillAPI.getSkillsFrom(player).getSkill(request.abilityId());
        if (maybeInstance.isEmpty()) {
            return 0L;
        }
        ManasSkillInstance instance = maybeInstance.get();
        int[] slotAndMode = resolvePresetSlot(player, instance);
        if (slotAndMode == null) {
            return 0L;
        }
        int mode = slotAndMode[1];
        if (mode < 0 || mode >= instance.getModes()) {
            return 0L;
        }
        if (instance.onCoolDown(mode)
                && !instance.canIgnoreCoolDown(player, mode)) {
            // ARR's boolean is the verdict; the floor of 1 keeps an
            // inconsistent tick read from waving a cooling skill through.
            return Math.max(1L, instance.getCoolDown(mode));
        }
        return 0L;
    }

    /**
     * The mode-cycle domain half (core#113's {@code ModeChangeHandler},
     * #53): the intent is cycle-shaped, so this builds ARR's own cycle
     * packet for the resolved active-preset slot and runs its native
     * server-side body - parity by construction: same nextMode, same
     * vetoes, same native messages, same zero-residue refusals. Not
     * learned and not slotted refuse RESTRICTED before ARR runs (this
     * service's own refusals); after the native body an unchanged slot
     * mode reports the closest native reason without inventing one: one
     * mode to offer - UNAVAILABLE, anything else refused - RESTRICTED.
     * {@code alternative} stays false: the plain native key semantics
     * (the reverse direction needs transport state {@link ModeChangeRequest}
     * carries neither).
     */
    public static ActivationResult changeMode(ModeChangeRequest request, ServerPlayer player) {
        Optional<ManasSkillInstance> maybeInstance =
                SkillAPI.getSkillsFrom(player).getSkill(request.abilityId());
        if (maybeInstance.isEmpty()) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        ManasSkillInstance instance = maybeInstance.get();
        int[] slotAndMode = resolvePresetSlot(player, instance);
        if (slotAndMode == null) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        int before = slotAndMode[1];
        RequestAbilityModeChangePacket.changeModePacket(slotAndMode[0], false)
                .changeMode(player);
        int[] after = resolvePresetSlot(player, instance);
        if (after != null && after[1] != before) {
            return ActivationResult.ACTED;
        }
        return ActivationResult.refused(instance.getModes() <= 1
                ? ActivationOutcome.REFUSED_UNAVAILABLE
                : ActivationOutcome.REFUSED_RESTRICTED);
    }

    /**
     * Declares the mode vocabulary for one catalog id (core#113 - what
     * hotbar#29's resolver validates stored modes against and what the mode
     * keybind cycles): non-empty vocabulary declares it; an empty one means
     * not cycleable (single-mode, non-TensuraSkill) or not expressible
     * (uniform/blank ids upstream - skipped with a log line instead of
     * tripping core's loud duplicate rejection; upstream data must never
     * crash setup).
     *
     * @return true when a vocabulary was declared (the projection pairs with it)
     */
    private static boolean declareModes(ResourceLocation id) {
        List<AbilityModes.Mode> modes = vocabularyFor(id,
                SkillAPI.getSkillRegistry().get(id));
        if (modes.isEmpty()) {
            return false;
        }
        AbilityModes.declare(id, modes);
        return true;
    }

    /**
     * The mode vocabulary for one probed skill - the SINGLE source the
     * declaration and the coverage fixture both read (they can never
     * drift): a default instance supplies the count and the ids
     * (bytecode-pinned name key: skillPath + "_" + modeId); empty when the
     * skill is not a TensuraSkill, has one mode, or offers no distinct
     * non-blank ids (logged - core would reject those loudly). Public so
     * the coverage fixture reads the very same rule.
     */
    public static List<AbilityModes.Mode> vocabularyFor(ResourceLocation id, ManasSkill skill) {
        if (!(skill instanceof TensuraSkill tensuraSkill)) {
            return List.of();
        }
        ManasSkillInstance probe = skill.createDefaultInstance();
        int count = probe.getModes();
        if (count <= 1) {
            return List.of();
        }
        List<AbilityModes.Mode> modes = new ArrayList<>(count);
        Set<String> seen = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            String modeId = tensuraSkill.getModeId(probe, i);
            if (modeId == null || modeId.isBlank() || !seen.add(modeId)) {
                LOGGER.warn("Tensura mode vocabulary for {} skipped: mode id '{}' at index {}",
                        id, modeId, i);
                return List.of();
            }
            modes.add(new AbilityModes.Mode(modeId, id.getPath() + "_" + modeId));
        }
        return modes;
    }

    /**
     * The live mode projection (core#113): the active preset's stored mode
     * index for this skill, mapped through the owning skill to its native
     * mode id - read live, server-side (core stores the function, never the
     * state); unknown (empty) when the skill is not held, not slotted, or
     * the index is out of range - never a guessed mode.
     */
    private static Optional<String> modeProjection(ServerPlayer player, ResourceLocation id) {
        Optional<ManasSkillInstance> maybeInstance =
                SkillAPI.getSkillsFrom(player).getSkill(id);
        if (maybeInstance.isEmpty()) {
            return Optional.empty();
        }
        ManasSkillInstance instance = maybeInstance.get();
        int[] slotAndMode = resolvePresetSlot(player, instance);
        if (slotAndMode == null) {
            return Optional.empty();
        }
        int modeIndex = slotAndMode[1];
        if (modeIndex < 0 || modeIndex >= instance.getModes()
                || !(instance.getSkill() instanceof TensuraSkill tensuraSkill)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tensuraSkill.getModeId(instance, modeIndex));
    }

    /**
     * The player's active-preset slot holding this instance's skill, as
     * (slotIndex, mode) - the exact pair Tensura's own listener validates;
     * null when the skill is not equipped (or storage is unavailable).
     */
    private static int[] resolvePresetSlot(ServerPlayer player, ManasSkillInstance instance) {
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        if (ability == null) {
            return null;
        }
        List<AbilitySlot> slots = ability.getAbilitySlots();
        for (int i = 0; i < slots.size(); i++) {
            AbilitySlot slot = slots.get(i);
            if (slot.getSkill() == instance.getSkill()) {
                return new int[] {i, slot.getMode()};
            }
        }
        return null;
    }
}
