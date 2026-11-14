package first.ecopt.test.mixin;

import java.util.List;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import first.ecopt.test.TestMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

@Mixin(value = LivingEntity.class, priority = 900)
public abstract class PushCompatibilityMixin {
    @WrapOperation(
            method = "pushEntities()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")
    )
    private List<Entity> ecoptTests$filterPush(Level level, Entity except, AABB box, Predicate<? super Entity> predicate,
                                              Operation<List<Entity>> original) {
        TestMod.pushQueries++;
        return original.call(level, except, box, predicate).stream()
                .filter(entity -> !entity.getTags().contains("ecopt_tests_skip_push")).toList();
    }
}
