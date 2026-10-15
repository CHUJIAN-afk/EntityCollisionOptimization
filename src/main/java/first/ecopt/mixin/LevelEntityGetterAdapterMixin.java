package first.ecopt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import first.ecopt.duck.EcoptLevelEntityGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;

/**
 * 接入点：把段存储暴露给 LevelMixin，用于按查询框遍历实体段。
 *
 * <p>用 {@code @Accessor} 而不是 {@code @Shadow}：注解处理器会把 {@code @Accessor} 的目标
 * 写进 refmap，1.20.1 Forge 生产环境（SRG 名字）才能正确定位 {@code sectionStorage} 字段。
 */
@Mixin(LevelEntityGetterAdapter.class)
@SuppressWarnings("rawtypes")
public abstract class LevelEntityGetterAdapterMixin implements EcoptLevelEntityGetter {

    @Override
    @Accessor("sectionStorage")
    public abstract EntitySectionStorage<Entity> ecopt$sectionStorage();
}
