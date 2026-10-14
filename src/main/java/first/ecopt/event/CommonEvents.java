package first.ecopt.event;

import first.ecopt.EcoptTicks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 服务端 tick：推进段内子格索引使用的 tick 序号。 */
@EventBusSubscriber(modid = first.ecopt.EntityCollisionOptimization.MODID)
public final class CommonEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EcoptTicks.bump();
    }
}
