package first.ecopt.duck;

import java.util.List;

/**
 * 由 {@code ClassInstanceMultiMapMixin} 实现，用来拿到 {@code ClassInstanceMultiMap} 内部那个
 * 保存全部实体的 {@code ArrayList}（原版字段 {@code allInstances}）。
 */
public interface AllInstancesHolder {

    List<?> ecopt$allInstances();
}
