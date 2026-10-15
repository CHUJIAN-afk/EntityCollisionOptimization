package first.ecopt.event;

import first.ecopt.EcoptTicks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 服务端 tick：推进段内子格索引使用的 tick 序号。 */
@Mod.EventBusSubscriber(modid = first.ecopt.EntityCollisionOptimization.MODID)
public final class CommonEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            EcoptTicks.bump();
        }
    }
}
