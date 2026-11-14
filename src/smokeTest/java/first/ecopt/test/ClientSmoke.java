package first.ecopt.test;

import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** 真实渲染主界面后保留一段时间，再保存游戏帧缓冲截图并正常退出。 */
@EventBusSubscriber(modid = "ecopt_tests", value = Dist.CLIENT)
public final class ClientSmoke {
    private static long firstFrame;
    private static int frames;
    private static boolean done;

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) throws Exception {
        if (Boolean.getBoolean("ecopt.smoke.client") && !done && event.getScreen() instanceof TitleScreen) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getOverlay() == null) {
                if (firstFrame == 0) {
                    firstFrame = System.nanoTime();
                    LogUtils.getLogger().info("[ecopt-smoke] TitleScreen first frame rendered");
                }
                frames++;
                if (System.nanoTime() - firstFrame >= 10_000_000_000L && frames >= 60) {
                    done = true;
                    Path evidence = minecraft.gameDirectory.toPath().resolve("smoke-results");
                    Files.createDirectories(evidence);
                    try (NativeImage screenshot = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                        screenshot.writeToFile(evidence.resolve("title-screen.png"));
                    }
                    String result = "PASS TitleScreen rendered for >=10 seconds; frames=" + frames;
                    Files.writeString(evidence.resolve("client-pass.txt"), result);
                    LogUtils.getLogger().info("[ecopt-smoke] {}", result);
                    minecraft.stop();
                }
            }
        }
    }
}
