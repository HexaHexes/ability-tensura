package org.bitwisemadness.abilitytensura.mixin;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.TensuraSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.bitwisemadness.abilitytensura.mastery.TensuraMastery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Core XP from the same sources as ARR mastery (owner's brief): every
 * ARR source - activations, damage, kills, the admin command - funnels
 * through {@code TensuraSkill.addMasteryPoint}, and ARR's own body
 * returns early once mastery is maxed (verified in bytecode: temporary
 * guard, mastered early-out, learning branch - no super call, which is
 * why the mixin targets THIS class and not ManasSkill). Awarding HERE
 * makes the source set identical by construction: pre-max the call
 * grants ARR mastery and core waits; post-max ARR gains nothing and the
 * ATTEMPT grants core XP through the spec's CUSTOM trigger, the
 * config-live gain handler and the declared share routing. Toggles and
 * mode changes never reach this method, so they grant nothing
 * (unchanged); undeclared and blacklisted ids fall through core's own
 * guards inside the award. The gametest pins the method signature, so
 * an upstream rename fails the gate instead of dropping every award.
 */
@Mixin(TensuraSkill.class)
public abstract class MasterySourceAwardMixin {

    @Inject(method = "addMasteryPoint"
                    + "(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;"
                    + "Lnet/minecraft/world/entity/LivingEntity;D)V",
            at = @At("HEAD"), cancellable = true)
    private void abilitytensura$awardCoreXpFromTheMasterySource(
            ManasSkillInstance instance, LivingEntity entity, double amount, CallbackInfo cir) {
        if (instance.isTemporarySkill() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (!TensuraMastery.mastered(instance)) {
            return; // pre-max: ARR's body grants its mastery, core waits
        }
        MasteryAwards.award(player, instance.getSkillId(), MasteryTrigger.CUSTOM);
        cir.cancel(); // post-max ARR returns anyway - the attempt IS the funnel
    }
}
