package com.rosetta.remotedebugbridge.mixin.staticprobe;

import com.rosetta.remotedebugbridge.mixin.StaticMixinProbe;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P4 static Mixin probe.
 *
 * <p>Targets {@code Chicken#aiStep} (SRG {@code m_8107_}), which is declared by
 * {@code Chicken} itself and is not used by any of the 16 Rosetta server mixins
 * shipped in the Mohist production jar (those cover Cow, Pig, Goat, Animal,
 * Ravager, ItemEntity, EndCrystal, MinecartItem, AbstractHurtingProjectile,
 * Interaction, ContainerOpenersCounter, CampfireBlock, IceBlock and the
 * dispense behaviours).</p>
 *
 * <p>Using the SRG selector here, this is only meaningful in the SRG production
 * runtime; the standard ForgeGradle refmap flow maps official names for dev
 * runs when the annotation processor is enabled.</p>
 */
@Mixin(Chicken.class)
public abstract class ChickenAiStepProbeMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void rosetta$probeAiStep(CallbackInfo ci) {
        StaticMixinProbe.onChickenAiStep((Object) this);
    }
}
