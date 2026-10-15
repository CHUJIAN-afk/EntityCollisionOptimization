package first.ecopt.event;

import first.ecopt.EcoptTicks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端 tick：推进客户端侧 tick 序号。 */
@Mod.EventBusSubscriber(modid = first.ecopt.EntityCollisionOptimization.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EcoptTicks.bump();
        }
    }
}
