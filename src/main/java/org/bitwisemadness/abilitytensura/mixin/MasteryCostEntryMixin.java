package org.bitwisemadness.abilitytensura.mixin;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The COST axis at ARR's skill-level cost READS (issue #111, coverage
 * fix): the instance-level mixin covers instance-entry readers (upkeep,
 * damage helper), but the cast flow reads the skill-level getters
 * directly - {@code TensuraSkill.isOutOfEnergy(...IF)} computes the
 * aura/magicule amounts every {@code EnergyHelper.isOutOfEnergy(entity,
 * instance, mode)} caller feeds in, and {@code Magic}'s override of the
 * double overload CONSUMES exactly those amounts (verified in bytecode;
 * Skill and Battlewill never override it - they are check-gated). The
 * redirects sit at the call sites, so virtual dispatch still lands on
 * each art's own override and the RETURNED value scales - a mixin on
 * the base method bodies alone could never see those (~170 overrides).
 *
 * <p>{@code createSource}'s snapshot (skill-level reads, super-called by
 * Skill and Magic) scales too, so the stored aura/magicule cost equals
 * what the cast charges - and because damage math reads that snapshot
 * (TensuraDamageHelper), a cost-linked damage art's formula now moves
 * with COST; the POWER work in #111 step 4 must compose deliberately on
 * top, never double-count.</p>
 *
 * <p>ServerPlayer casters only (client reads and probes keep the base -
 * core effect reads are server truth), zero costs and a 1.0 multiplier
 * stay byte-identical, and both method descriptors are pinned in the
 * gametest so an upstream rename fails the gate loudly instead of
 * silently unscaling the charge.</p>
 */
@Mixin(TensuraSkill.class)
public abstract class MasteryCostEntryMixin {

    private static final String AURA_GETTER =
            "Lio/github/manasmods/tensura/ability/TensuraSkill;getAuraCost"
                    + "(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;I)D";

    private static final String MAGICULE_GETTER =
            "Lio/github/manasmods/tensura/ability/TensuraSkill;getMagiculeCost"
                    + "(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;I)D";

    @Redirect(method = "isOutOfEnergy"
                    + "(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;IF)Z",
            at = @At(value = "INVOKE", target = AURA_GETTER))
    private double abilitytensura$checkAuraCost(TensuraSkill self, LivingEntity caster,
            ManasSkillInstance instance, int mode) {
        return abilitytensura$scale(self.getAuraCost(caster, instance, mode), instance, caster);
    }

    @Redirect(method = "isOutOfEnergy"
                    + "(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;IF)Z",
            at = @At(value = "INVOKE", target = MAGICULE_GETTER))
    private double abilitytensura$checkMagiculeCost(TensuraSkill self, LivingEntity caster,
            ManasSkillInstance instance, int mode) {
        return abilitytensura$scale(self.getMagiculeCost(caster, instance, mode), instance, caster);
    }

    @Redirect(method = "createSource"
                    + "(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;"
                    + "Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/resources/ResourceKey;I)"
                    + "Lnet/minecraft/world/damagesource/DamageSource;",
            at = @At(value = "INVOKE", target = AURA_GETTER))
    private double abilitytensura$snapshotAuraCost(TensuraSkill self, LivingEntity caster,
            ManasSkillInstance instance, int mode) {
        return abilitytensura$scale(self.getAuraCost(caster, instance, mode), instance, caster);
    }

    @Redirect(method = "createSource"
                    + "(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;"
                    + "Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/resources/ResourceKey;I)"
                    + "Lnet/minecraft/world/damagesource/DamageSource;",
            at = @At(value = "INVOKE", target = MAGICULE_GETTER))
    private double abilitytensura$snapshotMagiculeCost(TensuraSkill self, LivingEntity caster,
            ManasSkillInstance instance, int mode) {
        return abilitytensura$scale(self.getMagiculeCost(caster, instance, mode), instance, caster);
    }

    /** The COST multiplier over one base read - inert without a holder. */
    private static double abilitytensura$scale(double base, ManasSkillInstance instance,
            LivingEntity caster) {
        if (!(caster instanceof ServerPlayer player) || base <= 0.0) {
            return base;
        }
        double multiplier = MasteryEffects.costMultiplier(player, instance.getSkillId());
        return multiplier == 1.0 ? base : base * multiplier;
    }
}
