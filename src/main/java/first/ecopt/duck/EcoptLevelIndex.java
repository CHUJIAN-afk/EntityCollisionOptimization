package first.ecopt.duck;

import java.util.function.Consumer;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;

/** Level 侧：按查询框遍历可访问实体段；返回 false 表示拿不到段存储（调用方退回原版）。 */
public interface EcoptLevelIndex {

    boolean ecopt$forEachSection(AABB box, Consumer<EntitySection<Entity>> consumer);
}
