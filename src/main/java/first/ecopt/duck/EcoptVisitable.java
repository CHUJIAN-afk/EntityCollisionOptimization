package first.ecopt.duck;

/**
 * 由 {@code EntityMixin} 实现：给每个实体加一个"本查询已处理"时间戳，
 * 用于段内子格索引的多格归档去重（否则跨子格的实体会被投递多次）。
 */
public interface EcoptVisitable {

    long ecopt$queryStamp();

    void ecopt$setQueryStamp(long stamp);
}
