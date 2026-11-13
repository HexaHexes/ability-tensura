package org.bitwisemadness.abilitytensura.mixin;

import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The core COST axis on ARR's runtime cost funnel (issue #111, step 2):
 * every charge read - activation's energy check, per-tick drains, the
 * art-internal costs of projectiles and shields - passes through the
 * instance's magicule/aura getters, which delegate to the skill-level
 * methods where each art's own override lands (virtual dispatch). One
 * injection therefore scales skills, magic and battlewills alike, while
 * the registration-time declared cost (which reads the skill directly
 * with a null entity) stays the honest base the pane quotes.
 *
 * <p>The multiplier applies only for a ServerPlayer holder - client-side
 * reads and storage-less probes keep the base (core's effect reads are
 * server truth, solicited display rides the snapshot). The id must declare
 * COST (core's opt-in; {@code TensuraMastery.declareAll} does). Upstream
 * shape is version-pinned by the build, and the gametest pins both method
 * signatures so a rename fails the gate loudly instead of silently
 * dropping the discount.</p>
 */
@Mixin(TensuraSkillInstance.class)
public abstract class MasteryCostMultiplierMixin {

    @Inject(method = "getMagiculeCost", at = @At("RETURN"), cancellable = true)
    private void abilitytensura$scaleMagiculeCost(LivingEntity entity, int mode,
            CallbackInfoReturnable<Double> cir) {
        abilitytensura$applyCostMultiplier(entity, cir);
    }

    @Inject(method = "getAuraCost", at = @At("RETURN"), cancellable = true)
    private void abilitytensura$scaleAuraCost(LivingEntity entity, int mode,
            CallbackInfoReturnable<Double> cir) {
        abilitytensura$applyCostMultiplier(entity, cir);
    }

    /** The COST multiplier over the base read - inert without a holder. */
    private void abilitytensura$applyCostMultiplier(LivingEntity entity,
            CallbackInfoReturnable<Double> cir) {
        TensuraSkillInstance self = (TensuraSkillInstance) (Object) this;
        var storage = self.hasOwningStorage() ? self.getOwningStorage() : null;
        LivingEntity holder = storage != null ? storage.getOwner() : entity;
        if (!(holder instanceof ServerPlayer player)) {
            return;
        }
        double base = cir.getReturnValueD();
        double multiplier = MasteryEffects.costMultiplier(player, self.getSkillId());
        if (base > 0.0 && multiplier != 1.0) {
            cir.setReturnValue(base * multiplier);
        }
    }
}
