package first.ecopt;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import first.ecopt.duck.EcoptChunkCacheHolder;
import first.ecopt.duck.EcoptSectionStamp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 区块定长缓存：按当前段引用与修改版本校验，未变更的方块跨 tick 复用。<br>
 * 线程安全：条目不可变、写路径 synchronized、读路径用 VarHandle acquire/release 无锁探测，
 * 且探测有上限——表满时直接读段，不会自旋（修复多线程下 size 计数失真导致表填满后死循环的卡死）。
 */
public final class EcoptBlockCache {

    private static final int SLOTS = 1024;
    private static final int MASK = SLOTS - 1;
    private static final int LIMIT = 768;
    private static final VarHandle SLOT = MethodHandles.arrayElementVarHandle(Entry[].class);

    private EcoptBlockCache() {
    }

    /** 单区块缓存表。 */
    public static final class Table {
        final Entry[] entries = new Entry[SLOTS];
        int size;

        void clear() {
            for (int i = 0; i < SLOTS; i++) {
                SLOT.setVolatile(this.entries, i, null);
            }
            this.size = 0;
        }
    }

    /** 不可变条目：整体发布，读侧不会看到半更新状态。 */
    public static final class Entry {
        final long key;
        final BlockState state;
        final LevelChunkSection section;
        final int sectionIndex;
        final int sectionStamp;

        Entry(long key, BlockState state, LevelChunkSection section, int sectionIndex, int sectionStamp) {
            this.key = key;
            this.state = state;
            this.section = section;
            this.sectionIndex = sectionIndex;
            this.sectionStamp = sectionStamp;
        }

        /** 段被替换或改写则本条失效（读取侧无锁调用）。 */
        boolean valid(LevelChunk chunk) {
            LevelChunkSection[] sections = chunk.getSections();
            return this.sectionIndex >= 0 && this.sectionIndex < sections.length
                    && sections[this.sectionIndex] == this.section
                    && ((EcoptSectionStamp) this.section).ecopt$sectionStamp() == this.sectionStamp;
        }
    }

    /** 读方块状态：命中无锁返回，未命中走加锁的插入路径。 */
    public static BlockState getBlockState(LevelChunk chunk, BlockPos pos) {
        Table table = ((EcoptChunkCacheHolder) chunk).ecopt$cache();
        if (table == null) {
            table = new Table();
            ((EcoptChunkCacheHolder) chunk).ecopt$setCache(table);
        }
        long key = pos.asLong();
        int slot = slot(key);
        for (int probe = 0; probe < SLOTS; probe++) {
            Entry entry = (Entry) SLOT.getAcquire(table.entries, slot);
            if (entry == null) {
                return fill(chunk, pos, key, table);
            }
            if (entry.key == key) {
                return entry.valid(chunk) ? entry.state : fill(chunk, pos, key, table);
            }
            slot = (slot + 1) & MASK;
        }
        return read(chunk, pos);
    }

    /** 加锁插入/替换；表满则清空后重插，绝不在探测上空转。 */
    private static BlockState fill(LevelChunk chunk, BlockPos pos, long key, Table table) {
        int index = chunk.getSectionIndex(pos.getY());
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length || sections[index] == null) {
            return Blocks.AIR.defaultBlockState();
        }
        LevelChunkSection section = sections[index];
        BlockState state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        Entry fresh = new Entry(key, state, section, index, ((EcoptSectionStamp) section).ecopt$sectionStamp());
        synchronized (table) {
            if (table.size >= LIMIT) {
                table.clear();
            }
            int slot = slot(key);
            for (int probe = 0; probe < SLOTS; probe++) {
                Entry existing = (Entry) SLOT.getAcquire(table.entries, slot);
                if (existing == null) {
                    SLOT.setRelease(table.entries, slot, fresh);
                    table.size++;
                    return state;
                }
                if (existing.key == key) {
                    SLOT.setRelease(table.entries, slot, fresh);
                    return state;
                }
                slot = (slot + 1) & MASK;
            }
            table.clear();
            SLOT.setRelease(table.entries, slot(key), fresh);
            table.size = 1;
        }
        return state;
    }

    /** 直接读段，不经过缓存。 */
    private static BlockState read(LevelChunk chunk, BlockPos pos) {
        int index = chunk.getSectionIndex(pos.getY());
        LevelChunkSection[] sections = chunk.getSections();
        return index < 0 || index >= sections.length || sections[index] == null
                ? Blocks.AIR.defaultBlockState()
                : sections[index].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    /** 坐标 → 起始槽位。 */
    private static int slot(long key) {
        long mixed = key ^ (key >>> 32);
        return (int) (mixed * 0x9E3779B1L) >>> 22;
    }
}