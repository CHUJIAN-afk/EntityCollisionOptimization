package first.ecopt;

import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.fml.common.Mod;

/** 实体碰撞 / 实体段查询优化：mixin 接入点在 mixin 包，运行逻辑在根包与 event 包。 */
@Mod(EntityCollisionOptimization.MODID)
public final class EntityCollisionOptimization {

    public static final String MODID = "ecopt";


}
