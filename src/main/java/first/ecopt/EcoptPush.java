package first.ecopt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import first.ecopt.duck.EcoptLevelIndex;
import first.ecopt.duck.EcoptSectionLists;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** 推挤查询候选表：按查询框遍历实体段，只取各段增量表里"按类重写了 isPushable()"的实体，供 pushEntities 使用。 */
public final class EcoptPush {

    private EcoptPush() {
    }

    /** 返回 null 表示不接管（调用方走原版）。 */
    public static List<Entity> query(Level level, Entity except, AABB box, Predicate<? super Entity> predicate) {
        if (!(level instanceof EcoptLevelIndex index)) {
            return null;
        }
        List<Entity> matched = new ArrayList<>(4);
        boolean handled = index.ecopt$forEachSection(box, section -> {
            List<Entity> candidates = ((EcoptSectionLists) section).ecopt$pushables();
            for (int i = 0, n = candidates.size(); i < n; i++) {
                Entity candidate = candidates.get(i);
                if (candidate != except && candidate.getBoundingBox().intersects(box) && predicate.test(candidate)) {
                    matched.add(candidate);
                }
            }
        });
        if (!handled) {
            return null;
        }
        return matched.isEmpty() ? List.of() : matched;
    }
}
