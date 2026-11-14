package org.bitwisemadness.abilitytensura.mastery;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Magic;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.MasteryAxis;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilitytensura.config.TensuraServerConfig;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;
import org.jetbrains.annotations.Nullable;

/**
 * The mastery contract for the cataloged arts (ability-tensura's
 * REQ-CORE-12 consumer slice): one declaration per tracked id, riding
 * the same ARR skill-registry event as the catalogs so the id sets
 * always line up (an art that is a menu ability is a mastery
 * participant, and nothing else is).
 *
 * <p>Per activation an art grants the configured base gain - through a
 * gain handler that reads {@code mastery.skillXpGain} <b>live</b> at
 * award time (TensuraServerConfig's read-at-use-time rule; a config
 * reload applies to the very next activation) - and routes half of it
 * to the art's definition category mastery ({@code tensura:battlewill}
 * or the skill/magic system the enumeration assigned). Packs retune the
 * share, the gain shape or any effect through core's mastery_spec
 * datapack overlay (core#138); the server config owns the number.</p>
 *
 * <p><b>The award funnel is the mastery SOURCE itself</b> (owner's
 * brief): core XP flows only from ARR's own {@code addMasteryPoint} -
 * the single point every source funnels through (activations, damage,
 * kills, the admin command) - and only once ARR mastery is maxed. The
 * source mixin awards under the spec's CUSTOM trigger with the
 * config-live gain handler and the declared share routing; core's
 * pipeline ACTIVATION tail is structurally silent (the trigger set
 * names only CUSTOM), the legacy keybind packet mixin is retired (its
 * activation reaches the source like every other), toggles never start
 * a hold and grant nothing, and mode changes run outside and grant
 * nothing.</p>
 */
public final class TensuraMastery {

    /** Every grant's share routed to the art's definition category. */
    public static final double CATEGORY_SHARE = 0.5;

    /** The gain handler's name (registered once; resolves at first award). */
    public static final String GAIN_HANDLER = "ability_tensura:skill_xp";

    private TensuraMastery() {
    }

    /**
     * Declares every tracked id's spec - called at ARR's skill-registry
     * event beside the catalogs. Idempotent: a re-fired event (or a late
     * enumeration) declares only what is missing, and an id whose
     * definition is missing is skipped rather than invented.
     */
    public static void declareAll() {
        for (ResourceLocation id : UnlockGate.trackedIds()) {
            if (MasteryDeclarations.has(id)) {
                continue;
            }
            var registry = AbilityRegistries.registry();
            AbilityDefinition definition = registry == null ? null : registry.get(id);
            if (definition == null) {
                continue;
            }
            MasterySpec composed = MasterySpec.gained(0.0).withGainHandler(GAIN_HANDLER)
                    .withShare(MasteryTarget.category(definition.category()),
                            CATEGORY_SHARE)
                    // the effect side (issue #111): COST rides the mixin'd
                    // instance funnels; POWER rides the tagged damage
                    // source - both declared, never derived
                    .withEffect(MasteryAxis.COST)
                    .withEffect(MasteryAxis.POWER);
            // CAST_TIME only where a cast time exists (issue #111 step 3):
            // the Magic base is the sole getCastingTime holder, and the
            // skill registry is live at this event (the timing gametest)
            if (SkillAPI.getSkillRegistry().get(id) instanceof Magic) {
                composed = composed.withEffect(MasteryAxis.CAST_TIME);
            }
            // the award funnel is the mastery SOURCE itself (owner brief):
            // only CUSTOM grants - the pipeline ACTIVATION tail and the
            // retired keybind mixin are structurally silent (5-arg = cap)
            MasteryDeclarations.declare(id, new MasterySpec(composed.baseGain(),
                    Set.of(MasteryTrigger.CUSTOM), composed.shares(),
                    composed.gainHandler(), composed.effects()));
        }
    }

    /**
     * Registers the config-live gain handler - called at common setup
     * (before any activation can award; names resolve at first use, so
     * the order against {@link #declareAll} is free). The handler is
     * also the phase-3 mastery gate: a Tensura art earns core XP only
     * once ARR's own system has fully mastered it - a gain of 0 is a
     * grant core skips, on every funnel.
     */
    public static void registerGainHandler() {
        if (MasteryGainHandlers.has(GAIN_HANDLER)) {
            return;
        }
        MasteryGainHandlers.declare(GAIN_HANDLER,
                (player, source, trigger) -> mastered(player, source)
                        ? TensuraServerConfig.SKILL_XP_GAIN.get() : 0.0);
    }

    /**
     * Whether {@code player} has fully mastered {@code id} in ARR's own
     * system - the owner's rule (core XP starts after Tensura mastery):
     * the same read the menu's "Mastered" status answers with
     * ({@code masteryFraction >= 1}) - mastery at or above a usable
     * maximum. Unlearned arts, a null player, and skills ARR gives no
     * maximum to answer false: an ARR abstention never becomes a silent
     * yes.
     */
    private static boolean mastered(@Nullable ServerPlayer player, ResourceLocation id) {
        if (player == null) {
            return false;
        }
        var instance = SkillAPI.getSkillsFrom(player).getSkill(id).orElse(null);
        return instance != null && mastered(instance);
    }

    /**
     * The instance-level read of the same rule: at ARR's cap, with a
     * usable cap. The source-award mixin calls this with the instance it
     * already holds (no storage re-fetch).
     */
    public static boolean mastered(ManasSkillInstance instance) {
        int maxMastery = instance.getMaxMastery();
        return maxMastery > 0 && instance.getMastery() >= maxMastery;
    }
}
