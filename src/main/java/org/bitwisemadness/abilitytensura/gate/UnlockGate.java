package org.bitwisemadness.abilitytensura.gate;

import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.activation.ActivationContext;
import org.bitwisemadness.abilitycore.activation.EligibilityRule;
import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilitycore.api.AbilityUnlocks;
import org.bitwisemadness.abilitycore.api.ActivationOutcome;
import org.bitwisemadness.abilitycore.api.ActivationRequest;
import org.bitwisemadness.abilitycore.api.ActivationResult;
import org.bitwisemadness.abilitycore.api.ActivationSource;
import org.bitwisemadness.abilitycore.api.AvailabilityProviders;
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilitycore.api.Metric;
import org.bitwisemadness.abilitycore.api.VisibilityHandlers;

/**
 * The unlock gate (owner directive, 2026-11-06): <b>every ability this mod
 * registers is gated on the player having learned it in Tensura.</b> Three
 * halves, each on its natural seam:
 *
 * <ul>
 *   <li><b>Activation</b> - an {@link EligibilityRule} on
 *       {@link AbilityCore#ELIGIBILITY} (the hotbar's {@code CoreScopeCompat}
 *       pattern: registered at common setup, {@code appliesTo() = Set.of()}
 *       means every source) refuses {@link ActivationOutcome#REFUSED_RESTRICTED}
 *       for a tracked id the player has not learned. ARR's own
 *       {@code startHoldSkill} refuses unlearned skills too (getSkill empty
 *       -&gt; false, #52 evidence) - this rule is the uniform, explicit gate
 *       ahead of it, so refusals carry the right vocabulary no matter which
 *       consumer fired.</li>
 *   <li><b>Display</b> - {@link AvailabilityProviders#declare} per tracked
 *       id: not learned -&gt; {@code unavailable("not unlocked in Tensura")}
 *       (the bar's dim verdict); metrics stay {@code unknown} - per-ability
 *       cooldown/cost overlays are #42's overlay slice, not this gate's.</li>
 *   <li><b>Assignment/menu filtering</b> - no seam exists upstream today
 *       (the menu enumerates {@code AbilityDiscovery.entries()} unfiltered;
 *       {@code AssignAbilityPayload.handle} validates nothing per-player);
 *       filed as an ability-hotbar issue. Until it lands, assignment of an
 *       unlearned ability is possible but useless - activation refuses.</li>
 * </ul>
 *
 * <p>The decision core ({@link #refusalFor}) is pure and fixture-able -
 * {@code CoreScopeCompat}'s shape. The ARR read
 * ({@code SkillAPI.getSkillsFrom(player).getSkill(id)}) lives only in the
 * rule/provider bodies, so core stays upstream-free (the leak-gate rule)
 * and the pure half never loads ManasCore. Learned state is read through
 * ARR's own storage on whichever side asks (#52: client skill state is
 * synced - ARR's own screens render it client-side; in-game check pending).</p>
 */
public final class UnlockGate {

    /**
     * Every id our catalogs registered this session, accumulated during the
     * {@code RegisterEvent} phase (which completes before common setup, so
     * the snapshot below is complete when it is taken). Exact ids beat
     * category matching: nothing outside our catalogs is ever gated.
     */
    private static final Set<ResourceLocation> TRACKED = new LinkedHashSet<>();

    private UnlockGate() {
    }

    /** Catalogs call this as they register an id (RegisterEvent phase). */
    public static void track(ResourceLocation id) {
        TRACKED.add(id);
    }

    /**
     * The tracked ids as an unmodifiable view - the registration seam for
     * {@code TensuraActivation}'s handler loop (same phase guarantees:
     * complete before common setup).
     */
    public static Set<ResourceLocation> trackedIds() {
        return java.util.Collections.unmodifiableSet(TRACKED);
    }

    /**
     * Common setup: the eligibility rule and the availability declarations,
     * gated on Tensura being present (without ARR there is no learned state
     * and no ungated consumer path - the pairs' gate pattern). Rules run in
     * registration order; a refusal short-circuits the pipeline.
     */
    public static void register() {
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")) {
            return;
        }
        Set<ResourceLocation> ids = Set.copyOf(TRACKED);
        AbilityCore.ELIGIBILITY.register(new EligibilityRule() {
            @Override
            public Optional<ActivationResult> evaluate(ActivationRequest request, ServerPlayer player,
                    ActivationContext context) {
                ResourceLocation id = request.abilityId();
                if (!ids.contains(id)) {
                    return Optional.empty(); // not one of ours - never gate
                }
                return refusalFor(id, isLearned(player, id));
            }

            @Override
            public Set<ActivationSource> appliesTo() {
                // Every source: the unlock is a property of the player and
                // the ability, not of how the activation arrived.
                return Set.of();
            }
        });
        for (ResourceLocation id : ids) {
            AvailabilityProviders.declare(id, UnlockGate::availabilityFor);
            // The read seam (core#117): consumers ask core, not upstream -
            // all our ids are declared, so downstream never sees unknown.
            AbilityUnlocks.declare(id, UnlockGate::unlockAnswer);
            // Live menu presentations (ability-tensura#66): mastery + toggle,
            // same gating - without Tensura there is no state to present.
            TensuraPresentations.declare(id);
            // Menu visibility (core#124): tensura's own rule - an art
            // lists only when unlocked; unknown never hides (the
            // never-guess read), so bugs degrade to today's listing.
            VisibilityHandlers.declare(id, UnlockGate::menuVisible);
        }
        // Menu tabs (hotbar#154): the three category tabs with their
        // icons - display declarations ride the same setup seam as the
        // presentations above (gated inside, both sides).
        TensuraMenuTabs.declare();
        // Removal half: the event is cancelable (pre-removal), so a
        // presence check would still see the skill and lie - its id is the
        // authority. Guarded callsite (hotbar#120).
        SkillEvents.REMOVE_SKILL.register((instance, entity, changer) -> {
            if (entity instanceof ServerPlayer player
                    && ModList.get().isLoaded("ability_hotbar")) {
                ResourceLocation removed = instance.getSkillId();
                HotbarHygiene.apply(player, id -> id.equals(removed));
            }
            return EventResult.pass();
        });
    }

    /**
     * Login sweep (the offline/legacy half of the removal directive): drop
     * any tracked assignment the player no longer has - forgets that fired
     * while offline, assignments made before this gate existed, crafted
     * slots from the hotbar#135 hole. Guarded callsite (hotbar#120); no-op
     * without Tensura or hotbar.
     */
    public static void reconcile(ServerPlayer player) {
        if (ModList.get() == null || !ModList.get().isLoaded("tensura")
                || !ModList.get().isLoaded("ability_hotbar")) {
            return;
        }
        HotbarHygiene.apply(player, id -> TRACKED.contains(id) && !isLearned(player, id));
    }

    /**
     * The gate's decision, pure so it is fixture-able: a tracked id the
     * player has not learned refuses {@code REFUSED_RESTRICTED}; everything
     * else passes through untouched.
     *
     * @param id      the ability being activated
     * @param learned whether ARR says the player has it
     * @return empty = permitted; present = terminal refusal
     */
    public static Optional<ActivationResult> refusalFor(ResourceLocation id, boolean learned) {
        if (!TRACKED.contains(id) || learned) {
            return Optional.empty();
        }
        return Optional.of(ActivationResult.refused(ActivationOutcome.REFUSED_RESTRICTED));
    }

    /** Whether ARR's own storage has the ability learned for this entity. */
    public static boolean isLearned(LivingEntity entity, ResourceLocation id) {
        if (entity instanceof Player player) {
            // Routed through core (core#117); unknown for a declared id
            // means a provider bug - fail closed as not-learned.
            return AbilityUnlocks.isLearned(player, id).orElse(Boolean.FALSE);
        }
        return readLearned(entity, id); // non-player entities: no contract needed
    }

    /** The raw upstream read - the provider's own source of truth. */
    private static boolean readLearned(LivingEntity entity, ResourceLocation id) {
        return SkillAPI.getSkillsFrom(entity).getSkill(id).isPresent();
    }

    /** The contract's provider answer: live read; abstains to unknown without a player. */
    public static Optional<Boolean> unlockAnswer(Player player, ResourceLocation id) {
        return player == null
                ? Optional.empty()
                : Optional.of(readLearned(player, id));
    }

    /**
     * Tensura's menu visibility rule (core#124): an art lists only when
     * the player has unlocked it - the same live read the eligibility
     * gate, the menu dim and the assignment validation use (one
     * possession question, one answer). Unknown (no player) stays
     * visible: unknown never hides (hotbar#153's never-guess).
     */
    public static boolean menuVisible(Player player, ResourceLocation id) {
        return AbilityUnlocks.isLearned(player, id).orElse(Boolean.TRUE);
    }

    /** The display half: unknown metrics + the unlock verdict. */
    private static AbilityAvailability availabilityFor(Player player, ResourceLocation id) {
        boolean learned = player != null && isLearned(player, id);
        return new AbilityAvailability(
                Metric.unknown(),
                Metric.unknown(),
                learned
                        ? AvailabilityState.available()
                        : AvailabilityState.unavailable("not unlocked in Tensura"));
    }
}
