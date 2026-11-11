package org.bitwisemadness.abilitytensura.mastery;

import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.AbilityDefinition;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasteryGainHandlers;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;
import org.bitwisemadness.abilitytensura.config.TensuraServerConfig;
import org.bitwisemadness.abilitytensura.gate.UnlockGate;

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
 * <p><b>The award funnels are disjoint by construction</b> (the
 * no-double-award argument): core's pipeline tail grants every bar,
 * wheel and pane activation, so this slice deliberately awards nothing
 * inside {@code TensuraActivation}; the native keybind never enters the
 * pipeline and awards at its own entry (the packet mixin); the toggle
 * channel (MENU + NO_SLOT) never starts a hold and core's pipeline
 * skips it (core#139), so flipping a switch grants nothing anywhere.
 * Mode changes run outside both funnels and grant nothing.</p>
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
            MasteryDeclarations.declare(id,
                    MasterySpec.gained(0.0).withGainHandler(GAIN_HANDLER)
                            .withShare(MasteryTarget.category(definition.category()),
                                    CATEGORY_SHARE));
        }
    }

    /**
     * Registers the config-live gain handler - called at common setup
     * (before any activation can award; names resolve at first use, so
     * the order against {@link #declareAll} is free).
     */
    public static void registerGainHandler() {
        if (MasteryGainHandlers.has(GAIN_HANDLER)) {
            return;
        }
        MasteryGainHandlers.declare(GAIN_HANDLER,
                (player, source, trigger) -> TensuraServerConfig.SKILL_XP_GAIN.get());
    }
}
