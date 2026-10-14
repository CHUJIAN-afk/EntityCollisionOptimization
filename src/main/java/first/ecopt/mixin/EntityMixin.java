package first.ecopt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import first.ecopt.duck.EcoptSectionStamp;
import first.ecopt.duck.EcoptVisitable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 查询时间戳去重；竖直下落先探支撑方块，整方块直接判边界；干燥区段跳过流体扫描。 */
@Mixin(Entity.class)
public abstract class EntityMixin implements EcoptVisitable {

    @Unique
    private long ecopt$queryStamp;
    @Unique
    private BlockPos.MutableBlockPos ecopt$probePos;

    @Override
    public long ecopt$queryStamp() {
        return this.ecopt$queryStamp;
    }

    @Override
    public void ecopt$setQueryStamp(long stamp) {
        this.ecopt$queryStamp = stamp;
    }

    @Inject(method = "updateFluidHeightAndDoFluidPushing()V", at = @At("HEAD"), cancellable = true)
    private void ecopt$skipDryFluidScan(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        AABB bounds = self.getBoundingBox().deflate(0.001D);
        int x0 = Mth.floor(bounds.minX) >> 4;
        int x1 = (Mth.ceil(bounds.maxX) - 1) >> 4;
        int z0 = Mth.floor(bounds.minZ) >> 4;
        int z1 = (Mth.ceil(bounds.maxZ) - 1) >> 4;
        int y0 = Math.max(level.getSectionIndex(Mth.floor(bounds.minY)), 0);
        int y1 = Math.min(level.getSectionIndex(Mth.ceil(bounds.maxY) - 1), level.getSectionsCount() - 1);
        long tick = level.getGameTime();
        boolean dry = true;
        for (int x = x0; x <= x1 && dry; x++) {
            for (int z = z0; z <= z1 && dry; z++) {
                if (level.getChunkForCollisions(x, z) instanceof LevelChunk chunk) {
                    LevelChunkSection[] sections = chunk.getSections();
                    if (sections != null) {
                        for (int y = y0; y <= y1 && dry; y++) {
                            dry = sections[y] != null && !((EcoptSectionStamp) sections[y]).ecopt$hasFluid(tick);
                        }
                    } else {
                        dry = false;
                    }
                } else {
                    dry = false;
                }
            }
        }
        if (dry) {
            ci.cancel();
        }
    }

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private void ecopt$supportingBlockShortcut(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (movement.x == 0.0D && movement.z == 0.0D && movement.y < 0.0D) {
            Entity self = (Entity) (Object) this;
            Level level = self.level();
            AABB box = self.getBoundingBox();
            if (this.ecopt$probePos == null) {
                this.ecopt$probePos = new BlockPos.MutableBlockPos();
            }
            int yBase = Mth.floor(box.minY);
            int x0 = Mth.floor(box.minX + 1.0E-7);
            int x1 = Mth.floor(box.maxX - 1.0E-7);
            int z0 = Mth.floor(box.minZ + 1.0E-7);
            int z1 = Mth.floor(box.maxZ - 1.0E-7);
            int cx = Mth.floor((box.minX + box.maxX) * 0.5D);
            int cz = Mth.floor((box.minZ + box.maxZ) * 0.5D);
            // 单列足迹直接探一次；多列先探中心列（多数情况直接命中），没挡住才扫其余列
            boolean blocked = ecopt$blockedAt(level, self, box, movement, cx, yBase, cz);
            if (!blocked && (x0 != x1 || z0 != z1)) {
                for (int bx = x0; bx <= x1 && !blocked; bx++) {
                    for (int bz = z0; bz <= z1 && !blocked; bz++) {
                        if (bx != cx || bz != cz) {
                            blocked = ecopt$blockedAt(level, self, box, movement, bx, yBase, bz);
                        }
                    }
                }
            }
            if (blocked) {
                cir.setReturnValue(Vec3.ZERO);
            }
        }
    }

    /** 该列上下两层是否有方块把竖直位移完全挡住。 */
    @Unique
    private boolean ecopt$blockedAt(Level level, Entity self, AABB box, Vec3 movement, int bx, int yBase, int bz) {
        boolean blocked = false;
        for (int layer = 1; layer >= 0 && !blocked; layer--) {
            int y = yBase - layer;
            if (!level.isOutsideBuildHeight(y)) {
                this.ecopt$probePos.set(bx, y, bz);
                BlockState state = level.getBlockState(this.ecopt$probePos);
                if (!state.isAir()) {
                    VoxelShape shape = state.getCollisionShape(level, this.ecopt$probePos, CollisionContext.of(self));
                    if (shape == Shapes.block() && shape instanceof CubeVoxelShape) {
                        blocked = Math.abs(movement.y) < 1.0E-7
                                || box.minY == y + 1.0D
                                && box.minX + 1.0E-7 < bx + 1.0D && box.maxX - 1.0E-7 >= bx
                                && box.minZ + 1.0E-7 < bz + 1.0D && box.maxZ - 1.0E-7 >= bz;
                    } else if (!shape.isEmpty()) {
                        blocked = shape.move(bx, y, bz).collide(Direction.Axis.Y, box, movement.y) == 0.0D;
                    }
                }
            }
        }
        return blocked;
    }
}
