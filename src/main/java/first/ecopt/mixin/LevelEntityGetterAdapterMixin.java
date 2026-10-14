package first.ecopt.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import first.ecopt.duck.EcoptLevelEntityGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;

/** 接入点：把段存储暴露给 LevelMixin，用于按查询框遍历实体段。 */
@Mixin(LevelEntityGetterAdapter.class)
@SuppressWarnings("rawtypes")
public abstract class LevelEntityGetterAdapterMixin implements EcoptLevelEntityGetter {

    @Shadow
    @Final
    private EntitySectionStorage sectionStorage;

    @Override
    @SuppressWarnings("unchecked")
    public EntitySectionStorage<Entity> ecopt$sectionStorage() {
        return this.sectionStorage;
    }
}
