package first.ecopt.duck;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;

/** 从 LevelEntityGetterAdapter 取出段存储（服务端与客户端的管理器都用它）。 */
public interface EcoptLevelEntityGetter {

    EntitySectionStorage<Entity> ecopt$sectionStorage();
}
