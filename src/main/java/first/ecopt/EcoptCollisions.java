package first.ecopt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import first.ecopt.duck.EcoptLevelIndex;
import first.ecopt.duck.EcoptSectionLists;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 碰撞查询候选表：按查询框遍历实体段，只取各段增量表里"按类重写了 canBeCollidedWith()"的实体，原版 predicate 逐条复用。 */
public final class EcoptCollisions {

    private EcoptCollisions() {
    }

    /** 返回 null 表示不接管（调用方走原版）。 */
    public static List<VoxelShape> query(Level level, Entity except, AABB box) {
        if (box.getSize() < 1.0E-7) {
            return List.of();
        }
        if (!(level instanceof EcoptLevelIndex index)) {
            return null;
        }
        Predicate<Entity> predicate = except == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(except::canCollideWith);
        AABB grown = box.inflate(1.0E-7);
        List<Entity> matched = new ArrayList<>(4);
        boolean handled = index.ecopt$forEachSection(grown, section -> {
            List<Entity> candidates = ((EcoptSectionLists) section).ecopt$hardEntities();
            for (int i = 0, n = candidates.size(); i < n; i++) {
                Entity candidate = candidates.get(i);
                if (candidate != except && predicate.test(candidate) && candidate.getBoundingBox().intersects(grown)) {
                    matched.add(candidate);
                }
            }
        });
        if (!handled) {
            return null;
        }
        return matched.isEmpty() ? List.of() : new EcoptShapeList(matched);
    }
}
