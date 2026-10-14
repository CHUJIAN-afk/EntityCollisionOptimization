package first.ecopt.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import first.ecopt.duck.EcoptLevelEntityGetter;
import first.ecopt.duck.EcoptLevelIndex;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;

/** 接入点：把 Level 的实体段存储接出来，供碰撞/推挤查询按框遍历段（每段自带增量表，不再每 tick 全量重建）。 */
@Mixin(Level.class)
public abstract class LevelMixin implements EcoptLevelIndex {

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
