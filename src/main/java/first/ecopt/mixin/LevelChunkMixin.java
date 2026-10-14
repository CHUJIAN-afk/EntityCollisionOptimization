package first.ecopt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import first.ecopt.EcoptBlockCache;
import first.ecopt.duck.EcoptChunkCacheHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** 区块方块读取改走段引用与版本校验的缓存，区块自持缓存表。 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin implements EcoptChunkCacheHolder {

    @Unique
    private EcoptBlockCache.Table ecopt$table;

    @Override
    public EcoptBlockCache.Table ecopt$cache() {
        return this.ecopt$table;
    }

    @Override
    public void ecopt$setCache(EcoptBlockCache.Table table) {
        this.ecopt$table = table;
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void ecopt$cachedBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(EcoptBlockCache.getBlockState((LevelChunk) (Object) this, pos));
    }
}
