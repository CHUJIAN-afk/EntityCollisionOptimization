package first.ecopt;

import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.concurrent.atomic.AtomicLong;

/**
 * tick 序号与查询 id。
 *
 * <p><b>不要在这里用 ThreadLocal</b>：它落在碰撞扫描的热路径上，火焰图里
 * {@code ThreadLocal.get() → getEntry → getEntryAfterMiss} 一层就吃掉 0.6%+。
 * 现在用一个 {@code AtomicLong}（读即一次 volatile 读），tick 事件里自增。
 *
 * <p>流体存在性缓存用 {@code Level#getGameTime()}；方块缓存仅校验段引用与修改版本。
 *
 * <p>{@code EntitySection} 拿不到 Level，所以段内子格索引继续用这里的全局序号。
 * 返回值 0 表示"还没有 tick 过"（例如模组的工作线程发起查询），此时索引逻辑会退回线性扫描。
 */
public final class EcoptTicks {

    private static final AtomicLong TICK = new AtomicLong();
    private static final AtomicLong QUERY_ID = new AtomicLong();

    private EcoptTicks() {
    }

    /** 全局单调 tick 序号；0 表示还没 tick 过。 */
    public static long current() {
        return TICK.get();
    }

    public static void bump() {
        TICK.incrementAndGet();
    }

    /** 每次"走索引"的查询取一个唯一 id，用于实体去重标记。 */
    public static long nextQueryId() {
        return QUERY_ID.incrementAndGet();
    }
}
