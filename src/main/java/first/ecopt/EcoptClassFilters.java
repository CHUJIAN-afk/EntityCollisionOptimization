package first.ecopt;

import net.minecraft.world.entity.Entity;
import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * 按"类是否重写了某个方法"做的候选集判断，思路来自 Lithium 的 EntityClassGroup：
 * 反射只做一次（ClassValue 按类缓存），之后都是数组查表级别的开销。
 *
 * <p>注意：这里返回的是<b>超集</b>——"这个类有可能返回 true"，真正的判定仍然交给调用方的 predicate。
 * 例如 {@code LivingEntity} 没有重写 {@code canBeCollidedWith()}，所以生物/玩家根本不在
 * "硬实体"候选里（它们永远通不过 {@code canBeCollidedWith()} 过滤）。
 */
public final class EcoptClassFilters {

    // Forge 在开发环境将 SRG 名称映射为 Parchment/Mojmap，生产环境保留 SRG。
    private static final String HARD_METHOD = runtimeName("m_5829_");
    private static final String PUSHABLE_METHOD = runtimeName("m_6094_");
    private static final String COLLISION_METHOD = runtimeName("m_7337_");

    private EcoptClassFilters() {
    }

    /** 类是否重写了 canBeCollidedWith()（船 / 潜影贝 / 末影水晶等"实心"实体）。 */
    private static final ClassValue<Boolean> HARD = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return overrides(type, HARD_METHOD);
        }
    };

    /** 类是否重写了 isPushable()（能被推挤的实体）。 */
    private static final ClassValue<Boolean> PUSHABLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return overrides(type, PUSHABLE_METHOD);
        }
    };

    private static final ClassValue<Boolean> CUSTOM_COLLISION = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return overrides(type, COLLISION_METHOD, Entity.class);
        }
    };

    public static boolean hasCustomCollision(Entity entity) {
        return CUSTOM_COLLISION.get(entity.getClass());
    }

    public static boolean maybeHard(Entity entity) {
        return HARD.get(entity.getClass());
    }

    public static boolean maybePushable(Entity entity) {
        return PUSHABLE.get(entity.getClass());
    }

    private static String runtimeName(String srgName) {
        return ObfuscationReflectionHelper.remapName(INameMappingService.Domain.METHOD, srgName);
    }

    private static boolean overrides(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters).getDeclaringClass() != Entity.class;
        } catch (NoSuchMethodException e) {
            // 反射拿不到就保守地当作"可能"，宁可多测几个也不能漏
            return true;
        }
    }
}
