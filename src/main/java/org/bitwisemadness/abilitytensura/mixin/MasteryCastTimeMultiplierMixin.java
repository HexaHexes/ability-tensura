package org.bitwisemadness.abilitytensura.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Magic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.bitwisemadness.abilitycore.api.MasteryEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The core CAST_TIME axis on ARR's magic cast funnel (issue #111, step 3):
 * {@code getCastingTime(ManasSkillInstance, LivingEntity, int, boolean)}
 * is the canonical overload every other one delegates into, so one
 * return-wrap moves the cast setup and its remaining-time render together
 * - ISS's {@code CastTimeMasteryMixin} shape. Only a ServerPlayer caster
 * scales (core effect reads are server truth; the solicited snapshot
 * carries the display side to the menu), zero-length/instant casts stay
 * untouched, and the id must declare CAST_TIME (declareAll grants it only
 * to Magic ids - the sole getCastingTime holders). Fails loudly at apply
 * time if upstream renames the method (defaultRequire 1).
 */
@Mixin(Magic.class)
public abstract class MasteryCastTimeMultiplierMixin {

    @ModifyReturnValue(method = "getCastingTime"
                    + "(Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;"
                    + "Lnet/minecraft/world/entity/LivingEntity;IZ)I", at = @At("RETURN"))
    private int abilitytensura$discountCastTime(int original,
            ManasSkillInstance instance, LivingEntity caster, int stage, boolean flag) {
        if (original <= 0 || !(caster instanceof ServerPlayer player)) {
            return original;
        }
        double multiplier = MasteryEffects.castTimeMultiplier(player, instance.getSkillId());
        return multiplier == 1.0 ? original : (int) Math.round(original * multiplier);
    }
}
