package org.bitwisemadness.abilitytensura.mixin;

import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.manascore.skill.impl.network.c2s.RequestSkillActivationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.abilitycore.api.MasteryAwards;
import org.bitwisemadness.abilitycore.api.MasteryTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Award mastery for ARR's <b>native keybind</b> activation (ability-tensura's
 * REQ-CORE-12 slice): that path is manascore's own C2S packet - keybind ->
 * {@code RequestSkillActivationPacket} -> {@code Skills.startHoldSkill} -
 * and it never enters core's activation pipeline, so the pipeline's
 * post-activation tail never sees it. Every other surface (bar cell, wheel,
 * pane) runs through the pipeline and grants there - which is exactly why
 * this injection hangs on the packet's own call site and nowhere shared:
 * one award per press, no path can double, and a refusal (unlearned,
 * off-preset, cooldown) returns false and contributes nothing.
 *
 * <p>The award only lands for a {@link ServerPlayer} owner and a declared
 * art (core's opt-in rule makes an undeclared id a no-op). The target is
 * the packet's queued body; manascore is version-pinned by the build and
 * the gametest pins the lambda's existence, so an upstream rename fails
 * the gate loudly instead of silently dropping awards.</p>
 */
@Mixin(RequestSkillActivationPacket.class)
public abstract class NativeSkillActivationAwardMixin {

    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE",
            target = "Lio/github/manasmods/manascore/skill/api/Skills;startHoldSkill"
                    + "(Lnet/minecraft/resources/ResourceLocation;II)Z"))
    private boolean awardMasteryForNativeActivation(Skills storage, ResourceLocation id,
            int keyNumber, int mode) {
        boolean started = storage.startHoldSkill(id, keyNumber, mode);
        if (started && storage.getOwner() instanceof ServerPlayer player) {
            MasteryAwards.award(player, id, MasteryTrigger.ACTIVATION);
        }
        return started;
    }
}
