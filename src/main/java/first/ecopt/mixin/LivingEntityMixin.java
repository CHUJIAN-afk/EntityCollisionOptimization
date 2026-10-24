package first.ecopt.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import first.ecopt.EcoptPush;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 接入点：pushEntities 的实体查询改走 EcoptPush。<br>
 * 使用原生 @Redirect，并带 require/expect = 0：其它高优先级重定向占用调用点时让位。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Redirect(
            method = "pushEntities()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"
            ),
            require = 0,
            expect = 0
    )
    private List<Entity> ecopt$pushableEntities(Level level, Entity except, AABB box, Predicate<? super Entity> predicate) {
        List<Entity> fast = EcoptPush.query(level, except, box, predicate);
        return fast != null ? fast : level.getEntities(except, box, predicate);
    }
}
