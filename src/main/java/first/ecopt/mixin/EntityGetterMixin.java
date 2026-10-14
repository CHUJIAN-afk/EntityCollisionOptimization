package first.ecopt.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import first.ecopt.EcoptCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 接入点：getEntityCollisions 改走 EcoptCollisions 候选表。 */
@Mixin(EntityGetter.class)
public interface EntityGetterMixin {

    @WrapMethod(method = "getEntityCollisions")
    private List<VoxelShape> ecopt$getEntityCollisions(Entity entity, AABB collisionBox, Operation<List<VoxelShape>> original) {
        if (this instanceof Level level) {
            List<VoxelShape> fast = EcoptCollisions.query(level, entity, collisionBox);
            if (fast != null) {
                return fast;
            }
        }
        return original.call(entity, collisionBox);
    }
}
