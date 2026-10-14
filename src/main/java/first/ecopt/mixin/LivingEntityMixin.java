package first.ecopt.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import first.ecopt.EcoptPush;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 接入点：pushEntities 的实体查询改走 EcoptPush。<br>
 * 用 MixinExtras 的 @WrapOperation（可与其它模组的同点包装叠加），并带 require/expect = 0：
 * 若某模组已用原生 @Redirect 占了同一处调用点，我们让位而不是让启动失败。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @WrapOperation(
            method = "pushEntities()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            ),
            require = 0,
            expect = 0
    )
    private List<Entity> ecopt$pushableEntities(Level level, Entity except, AABB box, Predicate<? super Entity> predicate,
                                                Operation<List<Entity>> original) {
        List<Entity> fast = EcoptPush.query(level, except, box, predicate);
        return fast != null ? fast : original.call(level, except, box, predicate);
    }
}
