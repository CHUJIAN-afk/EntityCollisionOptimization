package first.ecopt;

import first.ecopt.duck.EcoptSectionStamp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** 区块定长缓存：按当前段引用与修改版本校验，未变更的方块跨 tick 复用，查询不读世界时间。 */
public final class EcoptBlockCache {

    private static final int SLOTS = 1024;
    private static final int LIMIT = 768;

    private EcoptBlockCache() {
    }

    /** 单区块缓存表。 */
    public static final class Table {
        final long[] keys = new long[SLOTS];
        final Entry[] entries = new Entry[SLOTS];
        int size;

        void clear() {
            java.util.Arrays.fill(this.entries, null);
            this.size = 0;
        }
    }

    /** 缓存条目。 */
    public static final class Entry {
        long key;
        BlockState state;
        LevelChunkSection section;
        int sectionIndex;
        int sectionStamp;
    }

    /** 读方块状态，命中缓存直接返回。 */
    public static BlockState getBlockState(LevelChunk chunk, BlockPos pos) {
        Table table = ((first.ecopt.duck.EcoptChunkCacheHolder) chunk).ecopt$cache();
        if (table == null) {
            table = new Table();
            ((first.ecopt.duck.EcoptChunkCacheHolder) chunk).ecopt$setCache(table);
            return fill(chunk, pos, null, table);
        }
        long key = pos.asLong();
        int slot = slot(key);
        Entry entry;
        while ((entry = table.entries[slot]) != null) {
            if (entry.key == key) {
                if (entry.section != null && entry.section == chunk.getSections()[entry.sectionIndex]
                        && ((EcoptSectionStamp) entry.section).ecopt$sectionStamp() == entry.sectionStamp) {
                    return entry.state;
                }
                return fill(chunk, pos, entry, table);
            }
            slot = (slot + 1) & (SLOTS - 1);
        }
        return fill(chunk, pos, null, table);
    }

    /** 未命中直接读段并填表，避免递归进入区块读取。 */
    private static BlockState fill(LevelChunk chunk, BlockPos pos, Entry entry, Table table) {
        int index = chunk.getSectionIndex(pos.getY());
        LevelChunkSection[] sections = chunk.getSections();
        if (index < 0 || index >= sections.length || sections[index] == null) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        LevelChunkSection section = sections[index];
        BlockState state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        long key = pos.asLong();
        if (entry == null) {
            if (table.size >= LIMIT) {
                table.clear();
            }
            int slot = slot(key);
            while (table.entries[slot] != null) {
                slot = (slot + 1) & (SLOTS - 1);
            }
            entry = new Entry();
            entry.key = key;
            table.entries[slot] = entry;
            table.size++;
        }
        entry.state = state;
        entry.section = section;
        entry.sectionIndex = index;
        entry.sectionStamp = ((EcoptSectionStamp) section).ecopt$sectionStamp();
        return state;
    }

    /** 坐标 → 起始槽位。 */
    private static int slot(long key) {
        long mixed = key ^ (key >>> 32);
        return (int) (mixed * 0x9E3779B1L) >>> 22;
    }
}
