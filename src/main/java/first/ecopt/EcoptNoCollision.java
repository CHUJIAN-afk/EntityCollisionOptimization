package first.ecopt;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * noCollision 快速实现：三重循环扫方块（按区块分组）、空气与空形状早退、整方块只做 AABB 相交、命中即返回，
 * 方块读取走 EcoptBlockCache；实体与世界边界部分与原版逐条对齐。
 */
public final class EcoptNoCollision {

    private EcoptNoCollision() {
    }

    public static boolean test(Level level, Entity entity, AABB box) {
        CollisionContext context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
        int x0 = Mth.floor(box.minX - 1.0E-7) - 1;
        int x1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int y0 = Math.max(Mth.floor(box.minY - 1.0E-7) - 1, level.getMinBuildHeight());
        int y1 = Math.min(Mth.floor(box.maxY + 1.0E-7) + 1, level.getMaxBuildHeight() - 1);
        int z0 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int z1 = Mth.floor(box.maxZ + 1.0E-7) + 1;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockGetter chunkGetter = null;
        long chunkKey = Long.MIN_VALUE;
        VoxelShape boxShape = null;
        boolean blocked = false;
        for (int x = x0; x <= x1 && !blocked; x++) {
            for (int z = z0; z <= z1 && !blocked; z++) {
                long key = ChunkPos.asLong(x >> 4, z >> 4);
                if (key != chunkKey) {
                    chunkKey = key;
                    chunkGetter = level.getChunkForCollisions(x >> 4, z >> 4);
                }
                if (chunkGetter != null) {
                    for (int y = y0; y <= y1; y++) {
                        pos.set(x, y, z);
                        BlockState state = chunkGetter instanceof LevelChunk chunk
                                ? EcoptBlockCache.getBlockState(chunk, pos)
                                : chunkGetter.getBlockState(pos);
                        if (!state.isAir()) {
                            VoxelShape shape = state.getCollisionShape(level, pos, context);
                            if (!shape.isEmpty()) {
                                if (shape == Shapes.block()) {
                                    if (box.intersects(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D)) {
                                        blocked = true;
                                        break;
                                    }
                                } else {
                                    if (boxShape == null) {
                                        boxShape = Shapes.create(box);
                                    }
                                    if (Shapes.joinIsNotEmpty(shape.move(x, y, z), boxShape, BooleanOp.AND)) {
                                        blocked = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (blocked || !level.getEntityCollisions(entity, box).isEmpty()) {
            return false;
        }
        if (entity == null) {
            return true;
        }
        WorldBorder border = level.getWorldBorder();
        return !border.isInsideCloseToBorder(entity, box)
                || !Shapes.joinIsNotEmpty(border.getCollisionShape(), Shapes.create(box), BooleanOp.AND);
    }
}
