package first.ecopt.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import first.ecopt.duck.EcoptSectionStamp;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** 段修改时推进版本号；按 tick 与版本缓存调色板中的流体存在性。 */
@Mixin(LevelChunkSection.class)
public abstract class LevelChunkSectionMixin implements EcoptSectionStamp {

    @Unique
    private int ecopt$stamp;
    @Unique
    private int ecopt$fluidStamp = -1;
    @Unique
    private long ecopt$fluidTick = Long.MIN_VALUE;
    @Unique
    private boolean ecopt$hasFluid;

    @Shadow
    public abstract boolean maybeHas(Predicate<BlockState> predicate);

    @Override
    public int ecopt$sectionStamp() {
        return this.ecopt$stamp;
    }

    @Override
    public boolean ecopt$hasFluid(long tick) {
        if (this.ecopt$fluidTick != tick || this.ecopt$fluidStamp != this.ecopt$stamp) {
            this.ecopt$hasFluid = this.maybeHas(state -> !state.getFluidState().getFluidType().isAir());
            this.ecopt$fluidStamp = this.ecopt$stamp;
            this.ecopt$fluidTick = tick;
        }
        return this.ecopt$hasFluid;
    }

    @Inject(
            method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN")
    )
    private void ecopt$bumpStamp(int x, int y, int z, BlockState state, boolean useLocks, CallbackInfoReturnable<BlockState> cir) {
        this.ecopt$stamp++;
    }

    @Inject(method = {"read", "recalcBlockCounts"}, at = @At("RETURN"))
    private void ecopt$invalidatePalette(CallbackInfo ci) {
        this.ecopt$stamp++;
    }
}
