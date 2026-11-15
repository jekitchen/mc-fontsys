package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.Window;
import com.mojang.renderpearl.api.device.GpuSurface;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.ui.AdvancedFontScreen;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Window resizes on both graphics backends (PRD D62). The Vulkan crash first seen in
 * {@code AdvancedScreenGameTest} came from resizing the window in a test without setting the
 * window surface up again ({@link GameTestWindow}). This shrinks and grows the window several
 * times, through {@link GameTestWindow}, with a system font applied: on the title screen, on the
 * settings screen (preview and font names, D49) and on the Advanced screen. After every resize it
 * checks the window surface was set up again at the window's new size, so a test that bypasses
 * the helper or a change in Fabric's test input shows up here on OpenGL too, not only as a lost
 * device on Vulkan. Run it on Vulkan with {@code -PgraphicsBackend=vulkan}.
 */
@SuppressWarnings("UnstableApiUsage")
public class WindowResizeGameTest implements FabricClientGameTest {
	/** Shrinks and grows, small and large steps. */
	private static final int[][] SIZES = {{640, 480}, {854, 480}, {1280, 720}, {700, 500}, {1100, 650}, {640, 480}};

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		try {
			if (pick.isPresent()) {
				GameTestFonts.apply(context, GameTestFonts.settingsFor(pick.get()));
			} else {
				Fontsys.LOGGER.warn("[gametest] No system fonts found; resizing with the vanilla font only.");
			}
			resizeAll(context, "title screen");
			context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
			context.waitTicks(10);
			resizeAll(context, "settings screen");
			context.clickScreenButton("fontsys.screen.advanced");
			context.waitForScreen(AdvancedFontScreen.class);
			context.waitTicks(5);
			resizeAll(context, "Advanced screen");
			context.setScreen(TitleScreen::new);
		} finally {
			GameTestWindow.reset(context);
			context.waitTicks(5);
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			context.setScreen(TitleScreen::new);
		}
	}

	private static void resizeAll(ClientGameTestContext context, String where) {
		for (int[] size : SIZES) {
			GameTestWindow.resize(context, size[0], size[1]);
			context.waitTicks(10);
			String problem = context.computeOnClient(mc -> {
				Window window = mc.getWindow();
				Window.FramebufferSize pixels = window.queryFramebufferSize();
				Optional<GpuSurface.Configuration> surface = mc.windowSurface().currentConfiguration();
				if (surface.isEmpty()) {
					return "the window surface has no configuration";
				}
				if (surface.get().width() != pixels.width() || surface.get().height() != pixels.height()) {
					return "the window surface is " + surface.get().width() + "x" + surface.get().height()
						+ " but the window is " + pixels.width() + "x" + pixels.height() + " pixels";
				}
				if (mc.gameRenderer.mainRenderTarget().width != pixels.width() || mc.gameRenderer.mainRenderTarget().height != pixels.height()) {
					return "the main render target is " + mc.gameRenderer.mainRenderTarget().width + "x" + mc.gameRenderer.mainRenderTarget().height
						+ " but the window is " + pixels.width() + "x" + pixels.height() + " pixels";
				}
				return null;
			});
			if (problem != null) {
				throw new AssertionError(where + ", after resizing to " + size[0] + "x" + size[1] + ": " + problem);
			}
		}
	}
}
