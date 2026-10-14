package first.ecopt.duck;

import first.ecopt.EcoptBlockCache;

/** 区块侧：托管方块缓存表。 */
public interface EcoptChunkCacheHolder {

    EcoptBlockCache.Table ecopt$cache();

    void ecopt$setCache(EcoptBlockCache.Table table);
}
