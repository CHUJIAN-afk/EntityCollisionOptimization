package first.ecopt.duck;

/**
 * 由 {@code EntitySectionMixin} 实现，用来把段坐标写进 {@code EntitySection}
 * （原版 {@code EntitySection} 自己并不保存段坐标，是在 EntitySectionStorage#createSection 里创建的）。
 */
public interface SectionPosHolder {

    long ecopt$sectionPos();

    void ecopt$setSectionPos(long sectionPos);
}
