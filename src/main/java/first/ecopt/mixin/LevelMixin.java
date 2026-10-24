package first.ecopt.mixin;

import java.util.List;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import first.ecopt.EcoptCollisions;
import first.ecopt.EcoptNoCollision;
import first.ecopt.duck.EcoptLevelEntityGetter;
import first.ecopt.duck.EcoptLevelIndex;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 接入点：把 Level 的实体段存储接出来，供碰撞/推挤查询按框遍历段（每段自带增量表，不再每 tick 全量重建）。 */
@Mixin(Level.class)
public abstract class LevelMixin implements EcoptLevelIndex, LevelAccessor {

    /**
     * Level 只继承接口默认实现。向类合并具体覆盖方法，避免 Mixin 0.8.5 的接口注入限制。
     * 实现 LevelAccessor 也让 ForgeGradle 按接口继承关系把新增方法重映射到 SRG 名称。
     */
    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) {
        List<VoxelShape> fast = EcoptCollisions.query((Level) (Object) this, entity, box);
        // Mixin 0.8.5 会错误改写接口 super 的 invokespecial；回退在普通逻辑类中执行。
        return fast != null ? fast : EcoptCollisions.queryVanilla((Level) (Object) this, entity, box);
    }

    @Override
    public boolean noCollision(Entity entity, AABB box) {
        return EcoptNoCollision.test((Level) (Object) this, entity, box);
    }

    /** 原版 Level#getEntities() 是 protected，用 Invoker 取实体表。 */
    @Invoker("getEntities")
    protected abstract LevelEntityGetter<Entity> ecopt$entityGetter();

    @Override
    public boolean ecopt$forEachSection(AABB box, Consumer<EntitySection<Entity>> consumer) {
        if (this.ecopt$entityGetter() instanceof EcoptLevelEntityGetter getter) {
            getter.ecopt$sectionStorage().forEachAccessibleNonEmptySection(box, section -> {
                consumer.accept(section);
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
            return true;
        }
        return false;
    }
}
