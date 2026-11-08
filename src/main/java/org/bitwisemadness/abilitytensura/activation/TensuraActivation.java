package org.bitwisemadness.abilitytensura.activation;

import com.mojang.logging.LogUtils;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.network.c2s.RequestAbilityModeChangePacket;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.storage.ability.IAbility;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.AbilityModes;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.ActivationType;
import org.bitwisemadness.abilitycore.api.ChargingModel;
import org.bitwisemadness.abilitycore.api.CooldownOwners;
import org.bitwisemadness.abilitycore.api.ModeChangeHandlers;
import org.bitwisemadness.abilitycore.api.ModeChangeRequest;
import org.bitwisemadness.abilitycore.api.ModeOwners;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.bitwisemadness.abilityhotbar.client.MenuQuery;
import org.bitwisemadness.abilityhotbar.config.HotbarConfig;
import org.bitwisemadness.abilityhotbar.registry.HotbarRegistry;
import org.bitwisemadness.abilityhotbar.state.BarStateAttachments;
import org.bitwisemadness.abilityhotbar.state.SlotAssignment;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;
import org.jetbrains.annotations.Nullable;
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
 *       slots store exactly {@code (skill, mode)} - so both values resolve
 *       <b>server-side from the player's own preset</b>, never from client
 *       input. An art slotted to our hotbar but not equipped there has no
 *       preset slot to act through, so the service <b>mirrors on demand</b>
 *       ({@link #equippedSlot}): first free slot of the active preset via
 *       ARR's own setter - the owner's cast directive, so a hotbar slot
 *       casts without ARR-side setup - and a full preset refuses rather
 *       than displacing an occupied slot.</li>
 *   <li>ARR messages its own cooldown refusal (red chat line) - our
 *       handler reads the same state so core's pre-activation gate
 *       carries the matching outcome, no second messaging.</li>
 * </ul>
 *
 * <p>Fail-closed throughout (the direct-invocation precedent from
 * ability-iss): unlearned refuses RESTRICTED (defense in depth behind the
 * eligibility rule), a full preset refuses RESTRICTED with ARR's red
 * action-bar line naming the reason (the domain messages, the pipeline
 * carries codes), stale preset mode refuses
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
     * The shared activation: learn-gate, on-demand preset mirror, then the
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
            // The client never re-reads a mutated instance on its own: the
            // storage idiom pushes the instance's dirty flag into the
            // storage so the framework's flush reaches the pane's state.
            SkillAPI.getSkillsFrom(player).checkAndMarkDirty(instance);
            return ActivationResult.ACTED;
        }

        // Owner's custom-preset-per-hotbar: the active bar maps to its own
        // ARR preset (the tail - native presets untouched) and mirrors the
        // bar's slots in first; unmapped bars keep ARR's active preset
        mapBarToPreset(player, activeBarId(player));
        int[] slotAndMode = equippedSlot(player, instance, 0);
        if (slotAndMode == null) {
            // No slot to act through: ARR's preset is full - the mirror
            // equips on demand but never displaces an occupied slot, and
            // the domain (this service) messages the reason.
            return refuseWithoutPresetSlot(player);
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
     * learned refuses RESTRICTED before ARR runs, a skill with nothing
     * to step through refuses UNAVAILABLE before the mirror can equip it
     * as residue, and a full preset refuses through
     * {@link #refuseWithoutPresetSlot}; after the native body an
     * unchanged slot mode reports ARR's own veto - refused RESTRICTED,
     * never an invented reason. {@code alternative} stays false: the
     * plain native key semantics (the reverse direction needs transport
     * state {@link ModeChangeRequest} carries neither).
     */
    public static ActivationResult changeMode(ModeChangeRequest request, ServerPlayer player) {
        Optional<ManasSkillInstance> maybeInstance =
                SkillAPI.getSkillsFrom(player).getSkill(request.abilityId());
        if (maybeInstance.isEmpty()) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
        }
        ManasSkillInstance instance = maybeInstance.get();
        if (instance.getModes() <= 1) {
            return ActivationResult.refused(ActivationOutcome.REFUSED_UNAVAILABLE);
        }
        // the pill cycles against ITS bar (core#132): map that bar's
        // preset and mirror its slots, exactly like the activation path
        mapBarToPreset(player,
                request.barId() == null ? activeBarId(player) : request.barId());
        int[] slotAndMode = equippedSlot(player, instance, 0);
        if (slotAndMode == null) {
            return refuseWithoutPresetSlot(player);
        }
        int before = slotAndMode[1];
        RequestAbilityModeChangePacket.changeModePacket(slotAndMode[0], false)
                .changeMode(player);
        int[] after = resolvePresetSlot(player, instance);
        if (after != null && after[1] != before) {
            return ActivationResult.ACTED;
        }
        return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
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

    /**
     * The preset slot this art acts through, <b>equipping on demand</b>
     * (the owner's cast directive): ARR's ACTIVATE_SKILL listener and its
     * native mode body both resolve through the active preset, so an art
     * slotted to our hotbar but not equipped there has no slot to act on.
     * The mirror places it in the first free slot of the active preset
     * (ARR's own setter plus its dirty idiom) and answers with that slot;
     * an existing slot wins untouched, and a full preset (or absent
     * storage) answers null - no slot invented, no occupied slot
     * displaced.
     */
    private static int[] equippedSlot(
            ServerPlayer player, ManasSkillInstance instance, int mode) {
        int[] existing = resolvePresetSlot(player, instance);
        if (existing != null) {
            return existing;
        }
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        if (ability == null) {
            return null;
        }
        List<AbilitySlot> slots = ability.getAbilitySlots();
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) {
                ability.setAbilitySlot(ability.getActivePreset(), i, instance.getSkill(), mode);
                ability.markDirty();
                return new int[] {i, mode};
            }
        }
        return null;
    }

    /**
     * The refusal when ARR's active preset has no room for the art (the
     * mirror's only failure for a live player): ARR's own red action-bar
     * line names the reason - its cooldown line's style - and the outcome
     * stays RESTRICTED. The domain messages, the pipeline carries codes.
     */
    private static ActivationResult refuseWithoutPresetSlot(ServerPlayer player) {
        if (TensuraStorages.getAbilityFrom(player) != null) {
            player.displayClientMessage(Component.translatable(
                            "ability_tensura.msg.preset_full")
                    .withStyle(ChatFormatting.RED), true);
        }
        return ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED);
    }

    /**
     * Whether hotbar is present (hotbar is optional for this mod - every
     * hotbar-typed read below runs behind this gate, the HotbarHygiene
     * guarded-callsite pattern).
     */
    private static boolean hotbarLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("ability_hotbar");
    }

    /** The hotbar state's active bar, or null (hotbar absent / no state). */
    @Nullable
    private static ResourceLocation activeBarId(ServerPlayer player) {
        if (!hotbarLoaded()) {
            return null;
        }
        return player.getData(BarStateAttachments.BAR_STATE).activeBarId();
    }

    /**
     * Points ARR's active preset at the bar's custom preset and mirrors
     * the bar's slots into it (owner: custom presets for our hotbars -
     * the first-empty-slot mirror collided with a full native bar). A
     * null bar or an unmanaged bar is the legacy no-op: ARR's current
     * active preset keeps deciding (hotbar absent, unknown id).
     */
    private static void mapBarToPreset(ServerPlayer player, @Nullable ResourceLocation bar) {
        Integer preset = presetForBar(player, bar);
        if (bar == null || preset == null) {
            return;
        }
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        if (ability == null) {
            return;
        }
        ability.setActivePreset(preset);
        mirrorBarIntoPreset(player, bar, preset);
    }

    /**
     * The ARR preset a hotbar bar acts through: hotbar's manageable bars
     * in their own cycle order map onto the <b>tail</b> of ARR's presets
     * - bar 0 owns the LAST preset, the next bar the one before - so the
     * player's native presets stay untouched and every bar gets
     * <b>isolated skills and modes</b> (no cross-bar collisions, no
     * fighting a full native bar). Null bar or an unmanaged bar answers
     * null (fail closed to ARR's active preset).
     */
    @Nullable
    private static Integer presetForBar(ServerPlayer player, @Nullable ResourceLocation bar) {
        if (bar == null || !hotbarLoaded()) {
            return null;
        }
        int index = MenuQuery.manageableBars(
                HotbarConfig.effectiveHotbarCount(), HotbarRegistry.all()).indexOf(bar);
        if (index < 0) {
            return null;
        }
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        if (ability == null) {
            return null;
        }
        int preset = ability.getPresets().size() - 1 - index;
        return preset >= 0 ? preset : null;
    }

    /**
     * Mirrors the hotbar bar's slots into its preset (owner: our hotbar
     * replaces the preset system): each bar slot lands at the same index
     * with its stored mode mapped through the declared cycle (a slot
     * storing none keeps the preset's current mode), every other preset
     * slot clears. Clamped by the preset's own size; ARR's dirty idiom
     * carries it. Hotbar-typed reads: gated via {@link #presetForBar}.
     */
    private static void mirrorBarIntoPreset(ServerPlayer player,
            ResourceLocation bar, int preset) {
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        if (ability == null) {
            return;
        }
        SlotAssignment[] row =
                player.getData(BarStateAttachments.BAR_STATE).assignmentsOf(bar);
        if (row == null) {
            return;
        }
        List<AbilitySlot> slots = ability.getAbilitySlots(preset);
        for (int i = 0; i < slots.size(); i++) {
            AbilitySlot existing = slots.get(i);
            SlotAssignment slot = i < row.length ? row[i] : null;
            ManasSkill skill = null;
            ResourceLocation id = null;
            String storedMode = null;
            if (slot != null && slot.isAssigned() && slot.abilityId() != null) {
                skill = SkillAPI.getSkillRegistry().get(slot.abilityId());
                id = slot.abilityId();
                storedMode = slot.mode();
            }
            if (skill == null) {
                if (!existing.isEmpty()) {
                    ability.setAbilitySlot(preset, i, AbilitySlot.getEmpty());
                }
                continue;
            }
            ability.setAbilitySlot(preset, i, skill,
                    modeIndexOf(id, storedMode, existing.getMode()));
        }
        ability.markDirty();
    }

    /**
     * The preset's integer mode for our stored mode id: the declared
     * cycle's index, the preset's current mode when our slot stores none
     * (keep what ARR had), or 0 for a fresh default.
     */
    private static int modeIndexOf(@Nullable ResourceLocation id,
            @Nullable String storedMode, int fallback) {
        if (storedMode == null || id == null) {
            return fallback;
        }
        Optional<List<AbilityModes.Mode>> modes = AbilityModes.get(id);
        if (modes.isEmpty()) {
            return fallback;
        }
        List<AbilityModes.Mode> declared = modes.get();
        for (int i = 0; i < declared.size(); i++) {
            if (declared.get(i).id().equals(storedMode)) {
                return i;
            }
        }
        return fallback;
    }

    /** Definitions-time skill resolutions (the timing probe, gametest-pinned). */
    private static int skillsResolved;

    /**
     * How many cataloged ids resolved a live ARR skill when the
     * definitions were built - the gametest pins this above zero, so the
     * registration order can never silently drift to "skill registry
     * still empty" (every shape would degrade to INSTANT, no costs).
     */
    public static int skillsResolvedAtRegistration() {
        return skillsResolved;
    }

    /**
     * One art's static menu shape (owner's bytecode brief): its
     * activation type mapped from ARR's own signals and its declared
     * usage costs - display metadata only, charging stays NATIVE
     * (ADR-002; {@link ResourceCost} is exactly that descriptor).
     *
     * @param activation the mapped type - INSTANT is the honest fallback
     *                   when ARR is absent or the registry was not yet
     *                   populated (the registration probe pins the cold)
     * @param costs      the cast currencies the art quotes at mode 0,
     *                   positive amounts only (empty = none declared)
     */
    public record ArtShape(ActivationType activation, List<ResourceCost> costs) {
    }

    /**
     * The shape for one catalog id, resolved against ARR's live skill
     * registry. Guarded inside (the pairs' gate pattern): without Tensura
     * the answer is the INSTANT/empty fallback and the upstream read is
     * never linked - both catalogs call this at RegisterEvent time.
     */
    public static ArtShape shapeOf(ResourceLocation id) {
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")) {
            return new ArtShape(ActivationType.INSTANT, List.of());
        }
        ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
        if (skill == null) {
            return new ArtShape(ActivationType.INSTANT, List.of());
        }
        skillsResolved++;
        return new ArtShape(activationTypeFor(skill), costsFor(skill));
    }

    /**
     * The activation type, mapped from ARR's bytecode (owner's brief):
     * the magic family's own cast-time config decides first (a cast bar
     * reads CHARGED, zero-time magic INSTANT - its {@code canTick}/
     * {@code onHeld} hooks are the cast mechanic, not the label); a
     * skill overriding {@code canTick} ticks while held - CHANNELLED;
     * a non-ticking art overriding {@code onHeld} fires on release -
     * CHARGED (the battlewill charge pattern); everything else presses
     * once - INSTANT. UNKNOWN is never produced: it refuses activation,
     * and these signals always yield a real shape.
     */
    private static ActivationType activationTypeFor(ManasSkill skill) {
        if (skill instanceof Magic magic) {
            return magic.getDefaultCastTime() > 0
                    ? ActivationType.CHARGED : ActivationType.INSTANT;
        }
        if (declaresOverride(skill, "canTick",
                ManasSkillInstance.class, LivingEntity.class)) {
            return ActivationType.CHANNELLED;
        }
        if (declaresOverride(skill, "onHeld",
                ManasSkillInstance.class, LivingEntity.class, int.class, int.class)) {
            return ActivationType.CHARGED;
        }
        return ActivationType.INSTANT;
    }

    /**
     * Whether the skill's own class overrides the named ManasSkill hook:
     * reflection over the declaring class, so registration never needs an
     * entity instance (the upstream-shape reads stay data-free).
     */
    private static boolean declaresOverride(ManasSkill skill, String name,
            Class<?>... parameters) {
        try {
            Method hook = skill.getClass().getMethod(name, parameters);
            return hook.getDeclaringClass() != ManasSkill.class;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    /**
     * The declared usage costs (owner: show them for all abilities): the
     * cast currency the art itself quotes - magicule for magic, aura for
     * battlewills - at mode 0's default instance, positive amounts only.
     * A null-entity read or an upstream refusal degrades to no costs:
     * enrichment can never fail a registration.
     */
    private static List<ResourceCost> costsFor(ManasSkill skill) {
        if (!(skill instanceof TensuraSkill tensura)) {
            return List.of();
        }
        try {
            ManasSkillInstance probe = skill.createDefaultInstance();
            List<ResourceCost> costs = new ArrayList<>(2);
            double magicule = tensura.getMagiculeCost(null, probe, 0);
            if (magicule > 0) {
                costs.add(new ResourceCost("tensura:magicule", magicule));
            }
            double aura = tensura.getAuraCost(null, probe, 0);
            if (aura > 0) {
                costs.add(new ResourceCost("tensura:aura", aura));
            }
            return List.copyOf(costs);
        } catch (RuntimeException upstream) {
            return List.of();
        }
    }
}
