package first.ecopt.duck;

/** 段版本号与流体存在性缓存，方块改写和调色板重读时失效。 */
public interface EcoptSectionStamp {

    int ecopt$sectionStamp();

    boolean ecopt$hasFluid(long tick);
}
