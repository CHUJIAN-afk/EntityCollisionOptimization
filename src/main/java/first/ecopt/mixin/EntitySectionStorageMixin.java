package first.ecopt.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import first.ecopt.duck.SectionPosHolder;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;

/**
 * {@code EntitySection} 自己不保存段坐标，这里在 {@code EntitySectionStorage#createSection(long)}
 * 返回时把段坐标写进去，供快速路径使用（判断"查询框是否完整包含整段"、以及子格索引的段原点）。
 */
@Mixin(EntitySectionStorage.class)
@SuppressWarnings("rawtypes")
public abstract class EntitySectionStorageMixin {

    @Inject(method = "createSection", at = @At("RETURN"))
    private void ecopt$tagSectionPos(long sectionPos, CallbackInfoReturnable<EntitySection> cir) {
        EntitySection section = cir.getReturnValue();
        if (section instanceof SectionPosHolder holder) {
            holder.ecopt$setSectionPos(sectionPos);
        }
    }
}
