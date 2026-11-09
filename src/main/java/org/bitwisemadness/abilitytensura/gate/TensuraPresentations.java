package org.bitwisemadness.abilitytensura.gate;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.bitwisemadness.abilitycore.api.AbilityPresentation;
import org.bitwisemadness.abilitycore.api.PresentationField;
import org.bitwisemadness.abilitycore.api.PresentationProviders;
import org.bitwisemadness.abilitycore.api.Toggle;
import org.bitwisemadness.abilitytensura.config.TensuraClientConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Tensura's menu presentations (ability-tensura#66): the cataloged
 * abilities' live fields mapped from ARR/ManasCore at the provider
 * boundary, for core#122's contract (hotbar#148 renders them).
 *
 * <p><b>The #66 probe, resolved</b> (javap over manascore-skill 4.0.0.2 and
 * tensura-neoforge 2.0.1.3): {@code ManasSkillInstance.getMastery() /
 * getMaxMastery()} is the learning/mastery read, and {@code canBeToggled}
 * / {@code isToggled()} the toggle pair - all on the instance ARR syncs
 * client-side (the #52 note in {@link UnlockGate}: ARR's own screens render
 * this state in-game; the live client check is this issue's manual step).</p>
 *
 * <p>Nothing is guessed: no instance (unlearned or not ours), a
 * non-positive maximum, or a skill that cannot be toggled all present
 * <b>nothing</b> - the menu keeps the unchanged base row. The ARR reads
 * live only in {@link #present}; the pure helpers never touch ManasCore.</p>
 */
public final class TensuraPresentations {

    /** The provider's field label (our modid namespace, shipped in our lang). */
    public static final String MASTERY_LABEL = "ability_tensura.menu.field.mastery";
    /** The status label: Known / Learned / Mastered (owner's brief). */
    public static final String STATUS_LABEL = "ability_tensura.menu.field.status";

    private TensuraPresentations() {
    }

    /** Registers the shared presentation provider for one cataloged id. */
    public static void declare(ResourceLocation abilityId) {
        PresentationProviders.declare(abilityId, TensuraPresentations::present);
    }

    /**
     * The provider: instance reads for one ability (skill, magic and
     * battlewill are all Manas skills), client-safe per #52.
     *
     * @param player    the asking player; null (fixtures) presents nothing
     * @param abilityId the cataloged id
     * @return the presentation - an empty base when there is nothing to say
     */
    public static AbilityPresentation present(@Nullable Player player, ResourceLocation abilityId) {
        if (player == null) {
            return base();
        }
        ManasSkillInstance instance =
                SkillAPI.getSkillsFrom(player).getSkill(abilityId).orElse(null);
        if (instance == null) {
            // Unlearned but ours (the owner's known-from-start rule lists
            // e.g. every resistance from ARR's start): present Known.
            return TensuraClientConfig.SHOW_STATUS.get()
                    ? new AbilityPresentation(null,
                            List.of(PresentationField.text(STATUS_LABEL, "Known")))
                    : base();
        }
        return new AbilityPresentation(toggleFor(player, instance),
                statusAndMastery(instance));
    }

    /**
     * Status first - Known/Learned/Mastered is ARR's own distinction
     * (owner's brief) - then mastery when ARR exposes a usable maximum.
     * Values follow masteryText's precedent: raw display strings, never
     * guessed (the fraction helper abstains the same way). Either field
     * can be switched off ({@code menu.showStatus} / {@code
     * menu.showMastery}); with both off the row shows only the toggle.
     */
    static List<PresentationField> statusAndMastery(ManasSkillInstance instance) {
        float fraction = masteryFraction(instance.getMastery(), instance.getMaxMastery());
        PresentationField status = PresentationField.text(STATUS_LABEL,
                fraction >= 1f ? "Mastered" : "Learned");
        List<PresentationField> mastery = TensuraClientConfig.SHOW_MASTERY.get()
                ? masteryFields(instance)
                : List.of();
        if (!TensuraClientConfig.SHOW_STATUS.get()) {
            return mastery;
        }
        return mastery.isEmpty() ? List.of(status) : List.of(status, mastery.get(0));
    }

    /** The toggle half: null when the skill cannot be toggled (never "off"). */
    @Nullable
    static Toggle toggleFor(Player player, ManasSkillInstance instance) {
        return instance.canBeToggled(player) ? new Toggle(instance.isToggled()) : null;
    }

    /** The mastery progress field, or none when ARR exposes no usable maximum. */
    static List<PresentationField> masteryFields(ManasSkillInstance instance) {
        float fraction = masteryFraction(instance.getMastery(), instance.getMaxMastery());
        if (fraction < 0f) {
            return List.of();
        }
        return List.of(PresentationField.progress(MASTERY_LABEL, fraction,
                masteryText(instance.getMastery(), instance.getMaxMastery())));
    }

    /** 0..1 progress, or -1 when there is no usable maximum (never a guessed bar). */
    static float masteryFraction(double mastery, int maxMastery) {
        if (maxMastery <= 0 || !Double.isFinite(mastery)) {
            return -1f;
        }
        double clamped = Math.max(0d, Math.min(1d, mastery / maxMastery));
        return (float) clamped;
    }

    /** The bar's text: whole points over the maximum ("12/50") - floored, never overstated. */
    static String masteryText(double mastery, int maxMastery) {
        long current = (long) Math.floor(Math.max(0d, mastery));
        return current + "/" + maxMastery;
    }

    private static AbilityPresentation base() {
        return new AbilityPresentation(null, List.of());
    }
}
