package first.ecopt.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import first.ecopt.EcoptNoCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** 接入点：noCollision 改走 EcoptNoCollision。 */
@Mixin(CollisionGetter.class)
public interface CollisionGetterMixin {

    @WrapMethod(method = "noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z")
    private boolean ecopt$noCollision(Entity entity, AABB box, Operation<Boolean> original) {
        return this instanceof Level level ? EcoptNoCollision.test(level, entity, box) : original.call(entity, box);
    }
}
