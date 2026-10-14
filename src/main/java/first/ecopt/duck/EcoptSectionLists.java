package first.ecopt.duck;

import java.util.List;

import net.minecraft.world.entity.Entity;

/** 段侧增量表：随 add/remove 维护，不做每 tick 重建。 */
public interface EcoptSectionLists {

    List<Entity> ecopt$hardEntities();

    List<Entity> ecopt$pushables();
}
