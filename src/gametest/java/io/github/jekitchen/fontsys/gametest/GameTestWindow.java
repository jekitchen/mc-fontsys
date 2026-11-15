package io.github.jekitchen.fontsys.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;

/**
 * Resizing the game window in a client game test. Every test resizes through here, never with
 * {@code context.getInput().resizeWindow} alone (PRD D62).
 *
 * <p>Why: Fabric's {@code TestInput.resizeWindow} sets the window's size itself and cancels the
 * window's own resize callbacks, so vanilla's {@code Minecraft.framebufferSizeChanged()} never
 * runs, and with it {@code invalidateSurfaceConfiguration()}, which a real resize calls so the
 * window surface is set up again at the new size before the next frame. OpenGL has nothing to
 * set up, so it doesn't matter there. On Vulkan the swapchain keeps the old size while the window
 * and the game's render targets take the new one, and growing the window then loses the device
 * ({@code VK_ERROR_DEVICE_LOST}): vanilla Minecraft with only Fabric API and a test does it too.
 * So after Fabric's resize this does what vanilla's resize handler does.
 *
 * <p>Fabric also puts the window back to its starting size after each test the same way, so a
 * test that resizes must {@link #reset} before it ends.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestWindow {
	/** The size the game tests start with (Minecraft's default window). */
	static final int DEFAULT_WIDTH = 854;
	static final int DEFAULT_HEIGHT = 480;

	private GameTestWindow() {
	}

	/** Resizes the window to {@code width} × {@code height} and has the window surface set up again for it. */
	static void resize(ClientGameTestContext context, int width, int height) {
		context.getInput().resizeWindow(width, height);
		context.runOnClient(Minecraft::invalidateSurfaceConfiguration);
	}

	/** Back to the size the test started at, so Fabric's own reset after the test changes nothing. */
	static void reset(ClientGameTestContext context) {
		resize(context, DEFAULT_WIDTH, DEFAULT_HEIGHT);
	}
}
