package first.ecopt;

import java.util.AbstractList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 惰性碰撞形状列表：只有真正取元素时才 {@code Shapes.create(...)}。
 *
 * <p>思路来自 Lithium 的 {@code LazyList}：调用方最常见的用法是
 * {@code CollisionGetter#noCollision} 里的 {@code getEntityCollisions(...).isEmpty()}，
 * 这时完全不需要构造任何 VoxelShape。{@code Entity#collide} 也只有在实体真的挡住去路时才会遍历元素。
 */
public final class EcoptShapeList extends AbstractList<VoxelShape> {

    private final List<Entity> entities;

    public EcoptShapeList(List<Entity> entities) {
        this.entities = entities;
    }

    @Override
    public VoxelShape get(int index) {
        return Shapes.create(this.entities.get(index).getBoundingBox());
    }

    @Override
    public int size() {
        return this.entities.size();
    }

    @Override
    public boolean isEmpty() {
        return this.entities.isEmpty();
    }
}
