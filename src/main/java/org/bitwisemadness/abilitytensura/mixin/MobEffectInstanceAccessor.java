package org.bitwisemadness.abilitytensura.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The write path for the core DURATION axis (issue #111, step 5):
 * {@code MobEffectInstance.duration} is a private {@code int} with no
 * public setter - {@code mapDuration} only computes (the store lives
 * inside {@code tickDownDuration}'s {@code dup_x1; putfield}), so the
 * attributed-effect scaling reaches the live field through this
 * accessor. ARR's own {@code MixinMobEffectInstance} adds no duration
 * setter either, and the gametest pins the field by reflection, so an
 * upstream rename fails the gate instead of silently no-oping the
 * axis.
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {

    /** Writes the scaled duration onto the instance being applied. */
    @Accessor("duration")
    void setDuration(int duration);
}
