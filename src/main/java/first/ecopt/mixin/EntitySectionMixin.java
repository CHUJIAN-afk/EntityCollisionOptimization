package first.ecopt.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import first.ecopt.EcoptClassFilters;
import first.ecopt.EcoptTicks;
import first.ecopt.duck.AllInstancesHolder;
import first.ecopt.duck.EcoptSectionLists;
import first.ecopt.duck.EcoptVisitable;
import first.ecopt.duck.SectionPosHolder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;

/**
 * 接入点 + 逻辑宿主：EntitySection 的无类型 getEntities(AABB, consumer)。<br>
 * ① 两套增量表（可碰撞 / 可推挤）随 add/remove 维护，碰撞与推挤查询直接用它们，**不再每 tick 全量重建**；<br>
 * ② 索引循环直接遍历内部 ArrayList，去掉 Guava 迭代器包装；<br>
 * ③ 查询框完整包含整段时跳过逐实体 AABB 判定；<br>
 * ④ 小查询框且同 tick 内本段被查 ≥2 次时改用段内子格索引（每格 2 格方块，8³ = 512 桶，惰性分配），
 * 实体按包围盒覆盖的全部子格归档、查询按实体时间戳去重，add/remove 立即失效。
 */
@Mixin(EntitySection.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class EntitySectionMixin implements SectionPosHolder, EcoptSectionLists {

    @Shadow
    @Final
    private ClassInstanceMultiMap storage;

    @Unique
    private long ecopt$sectionPos;
    @Unique
    private boolean ecopt$hasSectionPos;
    @Unique
    private long ecopt$stamp;
    @Unique
    private int ecopt$queries;
    @Unique
    private boolean ecopt$indexValid;
    @Unique
    private List<EntityAccess>[] ecopt$cells;
    @Unique
    private int[] ecopt$usedCells;
    @Unique
    private int[] ecopt$cellMark;
    @Unique
    private int ecopt$buildId;
    @Unique
    private int ecopt$usedCount;
    @Unique
    private List<EntityAccess> ecopt$big;
    @Unique
    private List<Entity> ecopt$hard;
    @Unique
    private List<Entity> ecopt$pushable;

    @Override
    public List<Entity> ecopt$hardEntities() {
        return this.ecopt$hard == null ? List.of() : this.ecopt$hard;
    }

    @Override
    public List<Entity> ecopt$pushables() {
        return this.ecopt$pushable == null ? List.of() : this.ecopt$pushable;
    }

    @Override
    public long ecopt$sectionPos() {
        return this.ecopt$sectionPos;
    }

    @Override
    public void ecopt$setSectionPos(long sectionPos) {
        this.ecopt$sectionPos = sectionPos;
        this.ecopt$hasSectionPos = true;
        this.ecopt$indexValid = false;
        this.ecopt$stamp = 0L;
    }

    /** 实体进段：维护两张增量表（同一实体重复 add 时靠 contains 去重）。 */
    @Inject(method = "add", at = @At("HEAD"))
    private void ecopt$onAdd(EntityAccess entity, CallbackInfo ci) {
        this.ecopt$indexValid = false;
        if (entity instanceof Entity added && !added.isSpectator()) {
            if (EcoptClassFilters.maybeHard(added)) {
                if (this.ecopt$hard == null) {
                    this.ecopt$hard = new ArrayList<>(4);
                }
                if (!this.ecopt$hard.contains(added)) {
                    this.ecopt$hard.add(added);
                }
            }
            if (EcoptClassFilters.maybePushable(added)) {
                if (this.ecopt$pushable == null) {
                    this.ecopt$pushable = new ArrayList<>(4);
                }
                if (!this.ecopt$pushable.contains(added)) {
                    this.ecopt$pushable.add(added);
                }
            }
        }
    }

    /** 实体离段：原版移除成功才从两张增量表移除（失败说明实体不在这段，动了会漏检）。 */
    @Inject(method = "remove", at = @At("RETURN"))
    private void ecopt$onRemove(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        this.ecopt$indexValid = false;
        if (Boolean.TRUE.equals(cir.getReturnValue()) && entity instanceof Entity removed) {
            if (this.ecopt$hard != null) {
                this.ecopt$hard.remove(removed);
            }
            if (this.ecopt$pushable != null) {
                this.ecopt$pushable.remove(removed);
            }
        }
    }

    @Inject(
            method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ecopt$fastGetEntities(AABB bounds, AbortableIterationConsumer consumer,
                                       CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir) {
        List<EntityAccess> all = (List<EntityAccess>) ((AllInstancesHolder) this.storage).ecopt$allInstances();
        if (!all.isEmpty()) {
            cir.setReturnValue(this.ecopt$scan(bounds, consumer, all));
        }
    }

    /** 选路：小框且同 tick 查够次数走子格索引，否则线性扫描。 */
    @Unique
    private AbortableIterationConsumer.Continuation ecopt$scan(AABB bounds, AbortableIterationConsumer consumer,
                                                              List<EntityAccess> all) {
        AbortableIterationConsumer.Continuation result;
        long stamp = EcoptTicks.current();
        boolean small = bounds.getXsize() <= 8.0D && bounds.getYsize() <= 8.0D && bounds.getZsize() <= 8.0D;
        if (this.ecopt$hasSectionPos && small && stamp != 0L) {
            if (stamp != this.ecopt$stamp) {
                this.ecopt$stamp = stamp;
                this.ecopt$queries = 0;
                this.ecopt$indexValid = false;
            }
            if (++this.ecopt$queries >= 2) {
                if (!this.ecopt$indexValid) {
                    this.ecopt$buildIndex(all);
                    this.ecopt$indexValid = true;
                }
                result = this.ecopt$scanIndexed(bounds, consumer);
            } else {
                result = this.ecopt$scanLinear(bounds, consumer, all);
            }
        } else {
            result = this.ecopt$scanLinear(bounds, consumer, all);
        }
        return result;
    }

    /** 线性扫描；查询框完整包含整段时省掉逐实体判定。 */
    @Unique
    private AbortableIterationConsumer.Continuation ecopt$scanLinear(AABB bounds, AbortableIterationConsumer consumer,
                                                                    List<EntityAccess> all) {
        boolean covered = this.ecopt$hasSectionPos && ecopt$covers(bounds);
        for (int i = 0, n = all.size(); i < n; i++) {
            EntityAccess entity = all.get(i);
            if ((covered || entity.getBoundingBox().intersects(bounds)) && consumer.accept(entity).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    /** 重建子格索引：桶惰性分配，buildId 标记保证一次重建里每个用到的桶只登记一次。 */
    @Unique
    private void ecopt$buildIndex(List<EntityAccess> all) {
        if (this.ecopt$cells == null) {
            this.ecopt$cells = new List[512];
            this.ecopt$usedCells = new int[512];
            this.ecopt$cellMark = new int[512];
            this.ecopt$usedCount = 0;
            this.ecopt$big = new ArrayList<>();
        } else {
            int[] used = this.ecopt$usedCells;
            for (int i = 0, n = this.ecopt$usedCount; i < n; i++) {
                this.ecopt$cells[used[i]].clear();
            }
            this.ecopt$usedCount = 0;
            this.ecopt$big.clear();
        }
        int buildId = ++this.ecopt$buildId;
        double ox = (double) (SectionPos.x(this.ecopt$sectionPos) << 4);
        double oy = (double) (SectionPos.y(this.ecopt$sectionPos) << 4);
        double oz = (double) (SectionPos.z(this.ecopt$sectionPos) << 4);
        for (int i = 0, n = all.size(); i < n; i++) {
            EntityAccess entity = all.get(i);
            if (entity instanceof EcoptVisitable) {
                AABB bb = entity.getBoundingBox();
                int x0 = ecopt$cell(bb.minX, ox);
                int x1 = ecopt$cell(bb.maxX, ox);
                int y0 = ecopt$cell(bb.minY, oy);
                int y1 = ecopt$cell(bb.maxY, oy);
                int z0 = ecopt$cell(bb.minZ, oz);
                int z1 = ecopt$cell(bb.maxZ, oz);
                for (int cx = x0; cx <= x1; cx++) {
                    for (int cy = y0; cy <= y1; cy++) {
                        for (int cz = z0; cz <= z1; cz++) {
                            int index = cx | (cy << 3) | (cz << 6);
                            List<EntityAccess> cell = this.ecopt$cells[index];
                            if (cell == null) {
                                cell = new ArrayList<>(8);
                                this.ecopt$cells[index] = cell;
                            }
                            if (this.ecopt$cellMark[index] != buildId) {
                                this.ecopt$cellMark[index] = buildId;
                                this.ecopt$usedCells[this.ecopt$usedCount++] = index;
                            }
                            cell.add(entity);
                        }
                    }
                }
            } else {
                this.ecopt$big.add(entity);
            }
        }
    }

    /** 按查询框覆盖的子格（外加 1 格移动余量）收集实体并投递。 */
    @Unique
    private AbortableIterationConsumer.Continuation ecopt$scanIndexed(AABB bounds, AbortableIterationConsumer consumer) {
        long stamp = EcoptTicks.nextQueryId();
        double ox = (double) (SectionPos.x(this.ecopt$sectionPos) << 4);
        double oy = (double) (SectionPos.y(this.ecopt$sectionPos) << 4);
        double oz = (double) (SectionPos.z(this.ecopt$sectionPos) << 4);
        int xa = ecopt$cell(bounds.minX - 2.0D, ox);
        int xb = ecopt$cell(bounds.maxX + 2.0D, ox);
        int ya = ecopt$cell(bounds.minY - 2.0D, oy);
        int yb = ecopt$cell(bounds.maxY + 2.0D, oy);
        int za = ecopt$cell(bounds.minZ - 2.0D, oz);
        int zb = ecopt$cell(bounds.maxZ + 2.0D, oz);
        for (int cx = xa; cx <= xb; cx++) {
            for (int cy = ya; cy <= yb; cy++) {
                for (int cz = za; cz <= zb; cz++) {
                    List<EntityAccess> cell = this.ecopt$cells[cx | (cy << 3) | (cz << 6)];
                    if (cell != null && ecopt$deliver(cell, bounds, consumer, stamp).shouldAbort()) {
                        return AbortableIterationConsumer.Continuation.ABORT;
                    }
                }
            }
        }
        if (!this.ecopt$big.isEmpty() && ecopt$deliver(this.ecopt$big, bounds, consumer, stamp).shouldAbort()) {
            return AbortableIterationConsumer.Continuation.ABORT;
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    /** 投递一个桶；同一实体可能归档在多个子格，按查询 id 去重。 */
    @Unique
    private AbortableIterationConsumer.Continuation ecopt$deliver(List<EntityAccess> cell, AABB bounds,
                                                                 AbortableIterationConsumer consumer, long stamp) {
        for (int i = 0, n = cell.size(); i < n; i++) {
            EntityAccess entity = cell.get(i);
            if (entity instanceof EcoptVisitable visitable) {
                if (visitable.ecopt$queryStamp() == stamp) {
                    continue;
                }
                visitable.ecopt$setQueryStamp(stamp);
            }
            if (entity.getBoundingBox().intersects(bounds) && consumer.accept(entity).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    /** 段内相对坐标 → 子格下标（越界夹到边界格）。 */
    @Unique
    private static int ecopt$cell(double coordinate, double origin) {
        int cell = Mth.floor(coordinate - origin) >> 1;
        return cell < 0 ? 0 : (cell > 7 ? 7 : cell);
    }

    /** 查询框是否完整包含本段。 */
    @Unique
    private boolean ecopt$covers(AABB bounds) {
        double minX = (double) (SectionPos.x(this.ecopt$sectionPos) << 4);
        double minY = (double) (SectionPos.y(this.ecopt$sectionPos) << 4);
        double minZ = (double) (SectionPos.z(this.ecopt$sectionPos) << 4);
        return bounds.minX <= minX && bounds.minY <= minY && bounds.minZ <= minZ
                && bounds.maxX >= minX + 16.0D && bounds.maxY >= minY + 16.0D && bounds.maxZ >= minZ + 16.0D;
    }
}
