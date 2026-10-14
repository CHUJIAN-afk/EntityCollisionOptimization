package first.ecopt.event;

import first.ecopt.EcoptTicks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 客户端 tick：推进客户端侧 tick 序号。 */
@EventBusSubscriber(modid = first.ecopt.EntityCollisionOptimization.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        EcoptTicks.bump();
    }
}
