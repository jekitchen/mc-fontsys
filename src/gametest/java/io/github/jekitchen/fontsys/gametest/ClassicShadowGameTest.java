package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.ui.AdvancedFontScreen;
import io.github.jekitchen.fontsys.ui.ChangeSummary;
import io.github.jekitchen.fontsys.ui.FontConfigScreen;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Classic shadow (PRD D60) at GUI scale 2, where the thinner shadow (D37) is 1 screen pixel and
 * vanilla's full unit is 2, driven with the real mouse and keyboard:
 *
 * <ul>
 *   <li>the checkbox starts off; clicking it edits the draft only, Advanced... counts it, Reset to
 *       defaults turns on, and the Advanced screen's preview shows the classic shadow before
 *       anything is applied;</li>
 *   <li>each font keeps its own (D55): another font starts with it off, and going back brings it
 *       back;</li>
 *   <li>Apply lists Classic shadow: Off → On in "Keep this font?" (D47); Keep rebuilds the font
 *       with it, and text drawn with a shadow is offset 2 pixels instead of 1;</li>
 *   <li>after another font is kept, a new visit to the screen restores it for the first font;</li>
 *   <li>with Crisp edges on, the checkbox is grayed out (inactive, gray label), shows the classic
 *       shadow crisp text always has, and still explains itself in a tooltip.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public class ClassicShadowGameTest implements FabricClientGameTest {
	private static final int SCALE = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		Optional<FaceInfo> pick = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (pick.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the classic shadow test.");
			return;
		}
		FaceInfo first = pick.get();
		FontSettings firstDefaults = GameTestFonts.settingsFor(first);
		try {
			guiScale(context, SCALE);
			GameTestFonts.apply(context, firstDefaults);
			int thin = TextQualityGameTest.shadowPixels(context, "d60-shadow-thin");
			require(thin == 1, "the thinner shadow is 1 px at GUI scale 2, got " + thin);

			Optional<FaceInfo> other = GameTestFonts.pick(context, "Georgia", "Times New Roman", "DejaVu Serif", "Arial")
				.filter(f -> !f.family().equals(first.family()));
			tickOnTheAdvancedScreen(context);
			other.ifPresent(second -> browsingKeepsIt(context, first, second));
			applyAndKeepClassic(context);

			int classic = TextQualityGameTest.shadowPixels(context, "d60-shadow-classic");
			require(classic == SCALE, "the classic shadow is a full GUI unit, " + SCALE + " px, got " + classic);
			Fontsys.LOGGER.info("[gametest] Shadow at GUI scale {}: {} px thin, {} px classic.", SCALE, thin, classic);

			other.ifPresentOrElse(second -> rememberedAfterAnotherFont(context, first, second),
				() -> Fontsys.LOGGER.warn("[gametest] Only one font family found; skipping the per-font Classic shadow check."));
			grayedOutInCrispMode(context, first);
		} finally {
			context.setScreen(TitleScreen::new);
			// Leave the first font remembered at its defaults, so later tests aren't affected.
			GameTestFonts.apply(context, firstDefaults);
			context.runOnClient(mc -> Fontsys.controller().confirm());
			GameTestFonts.apply(context, FontSettings.DEFAULTS);
			guiScale(context, 0);
		}
	}

	/** Off at first; one click turns it on in the draft, not yet applied, and the preview follows. */
	private static void tickOnTheAdvancedScreen(ClientGameTestContext context) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		settle(context);
		openAdvanced(context, Component.translatable("fontsys.screen.advanced").getString());
		require(context.computeOnClient(mc -> box(mc).active), "Classic shadow can be changed with Crisp edges off");
		require(!context.computeOnClient(mc -> box(mc).selected()), "Classic shadow starts off");
		int[][] before = shoot(context, "d60-advanced-off");

		ScreenRectangle r = context.computeOnClient(mc -> box(mc).getRectangle());
		click(context, r.left() + 5, r.top() + r.height() / 2);
		require(context.computeOnClient(mc -> box(mc).selected()), "a click ticks Classic shadow");
		require(draft(context).classicShadow(), "the draft has Classic shadow");
		require(!Fontsys.controller().appliedSettings().classicShadow(), "nothing is applied yet");
		String reset = Component.translatable("fontsys.advanced.reset").getString();
		require(context.computeOnClient(mc -> advancedWidget(mc, reset).active), "Reset to defaults turns on");
		settle(context);
		int[][] after = shoot(context, "d60-advanced-on");
		int preview = differingPixels(before, after, 0.45, 1.0, 24, 28);
		require(preview > 0, "the Advanced screen's preview shows the draft's shadow");
		Fontsys.LOGGER.info("[gametest] Preview pixels changed by Classic shadow: {}.", preview);

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
		require(draft(context).classicShadow(), "Esc keeps the change");
		require(advancedLabel(context).equals(Component.translatable("fontsys.screen.advanced.changed", 1).getString()),
			"Advanced... counts Classic shadow: " + advancedLabel(context));
	}

	/** Unapplied: another font starts with the defaults, and the first one gets it back (D55). */
	private static void browsingKeepsIt(ClientGameTestContext context, FaceInfo first, FaceInfo second) {
		selectFamily(context, second.family());
		require(!draft(context).classicShadow(), "a font never kept starts with Classic shadow off");
		selectFamily(context, first.family());
		require(draft(context).classicShadow(), "going back to " + first.family() + " brings back its unapplied Classic shadow");
	}

	/** Apply lists it in Keep this font?, and Keep rebuilds the font with it. */
	private static void applyAndKeepClassic(ClientGameTestContext context) {
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(10);
		List<ChangeSummary.Row> changes = context.computeOnClient(mc -> ((KeepOrRevertScreen) mc.gui.screen()).changes());
		require(changes.size() == 2 && changes.get(1).equals(new ChangeSummary.Row("fontsys.advanced.classic_shadow",
				new ChangeSummary.Value("fontsys.confirm.off", ""), new ChangeSummary.Value("fontsys.confirm.on", ""))),
			"Keep this font? lists Classic shadow: Off → On: " + changes);
		context.takeScreenshot("d60-keep-classic-shadow");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitFor(mc -> mc.gui.overlay() == null && !(mc.gui.screen() instanceof KeepOrRevertScreen));
		context.waitTicks(5);
		require(Fontsys.controller().appliedSettings().classicShadow(), "Keep applies Classic shadow");
		require(PackContents.loaded().classicShadow(), "the font was rebuilt with Classic shadow");
	}

	/** After another font is kept, a new visit picking the first font restores its Classic shadow. */
	private static void rememberedAfterAnotherFont(ClientGameTestContext context, FaceInfo first, FaceInfo second) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		settle(context);
		selectFamily(context, second.family());
		require(!draft(context).classicShadow(), second.family() + " starts with Classic shadow off");
		context.clickScreenButton("fontsys.screen.apply");
		context.waitForScreen(KeepOrRevertScreen.class);
		context.waitFor(mc -> mc.gui.overlay() == null);
		context.waitTicks(5);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitFor(mc -> mc.gui.overlay() == null && mc.gui.screen() instanceof FontConfigScreen);
		require(Fontsys.controller().appliedSettings().familyName().equals(second.family()), second.family() + " is applied");
		require(!PackContents.loaded().classicShadow(), second.family() + " is drawn with the thinner shadow");

		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		settle(context);
		selectFamily(context, first.family());
		require(draft(context).classicShadow(), first.family() + " comes back with the Classic shadow it was kept with");
		context.setScreen(TitleScreen::new);
	}

	/** Crisp glyphs always have the classic shadow, so the box steps aside and says why. */
	private static void grayedOutInCrispMode(ClientGameTestContext context, FaceInfo first) {
		context.setScreen(() -> new FontConfigScreen(new TitleScreen()));
		settle(context);
		selectFamily(context, first.family());
		require(draft(context).classicShadow(), "the first font has Classic shadow on");

		// Turn it off first, so in crisp mode the box shows checked only because crisp text has the classic shadow.
		openAdvanced(context, Component.translatable("fontsys.screen.advanced.changed", 1).getString());
		ScreenRectangle r = context.computeOnClient(mc -> box(mc).getRectangle());
		click(context, r.left() + 5, r.top() + r.height() / 2);
		require(!context.computeOnClient(mc -> box(mc).selected()), "a click unticks Classic shadow");
		settle(context);
		int whiteActive = whitePixels(shoot(context, "d60-advanced-active"), labelArea(context));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
		require(!draft(context).classicShadow(), "Classic shadow is off in the draft");

		ScreenRectangle crisp = widget(context, Component.translatable("fontsys.screen.crisp").getString());
		click(context, crisp.left() + 5, crisp.top() + crisp.height() / 2);
		require(draft(context).crispEdges(), "Crisp edges is on");
		openAdvanced(context, Component.translatable("fontsys.screen.advanced").getString());
		require(!context.computeOnClient(mc -> box(mc).active), "Classic shadow is grayed out in crisp mode");
		require(context.computeOnClient(mc -> box(mc).selected()), "it shows the classic shadow crisp text always has");
		int[][] before = shoot(context, "d60-advanced-crisp");
		int whiteCrisp = whitePixels(before, labelArea(context));
		require(whiteActive > 20 && whiteCrisp * 4 < whiteActive,
			"the label is gray in crisp mode: " + whiteCrisp + " white pixels, " + whiteActive + " when active");

		// Clicking does nothing; hovering still shows the tooltip.
		click(context, r.left() + 5, r.top() + r.height() / 2);
		require(context.computeOnClient(mc -> box(mc).selected()), "a click doesn't change the grayed-out box");
		context.getInput().setCursorPos((r.left() + r.width() / 2.0) * SCALE, (r.top() + r.height() / 2.0) * SCALE);
		context.waitTicks(15);
		int[][] hovered = shoot(context, "d60-advanced-crisp-tooltip");
		require(tooltipPixels(before, hovered) > 100, "the grayed-out box still explains itself in a tooltip");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(FontConfigScreen.class);
		require(!draft(context).classicShadow(), "showing it checked in crisp mode doesn't change the setting");
		context.setScreen(TitleScreen::new);
		context.waitTicks(5);
	}

	// ---- helpers -------------------------------------------------------------------------------

	private static void openAdvanced(ClientGameTestContext context, String label) {
		ScreenRectangle advanced = widget(context, label);
		click(context, advanced.left() + advanced.width() / 2, advanced.top() + advanced.height() / 2);
		context.waitForScreen(AdvancedFontScreen.class);
		settle(context);
	}

	private static Checkbox box(Minecraft mc) {
		return (Checkbox) advancedWidget(mc, Component.translatable("fontsys.advanced.classic_shadow").getString());
	}

	private static AbstractWidget advancedWidget(Minecraft mc, String prefix) {
		return ((AdvancedFontScreen) mc.gui.screen()).tooltipWidgets().stream()
			.filter(w -> w.getMessage().getString().startsWith(prefix)).findFirst()
			.orElseThrow(() -> new AssertionError("no '" + prefix + "' control on the Advanced screen"));
	}

	/** The checkbox's label, right of the box, in screen pixels: {x0, y0, x1, y1}. */
	private static int[] labelArea(ClientGameTestContext context) {
		ScreenRectangle r = context.computeOnClient(mc -> box(mc).getRectangle());
		int boxSize = context.computeOnClient(mc -> Checkbox.getBoxSize(mc.font));
		return new int[] {(r.left() + boxSize + 4) * SCALE, r.top() * SCALE, r.right() * SCALE, r.bottom() * SCALE};
	}

	/** Pure white pixels, as an active label's text has and a gray one doesn't. */
	private static int whitePixels(int[][] image, int[] area) {
		int count = 0;
		for (int y = area[1]; y < Math.min(area[3], image.length); y++) {
			for (int x = area[0]; x < Math.min(area[2], image[y].length); x++) {
				if (image[y][x] == 0xFFFFFF) {
					count++;
				}
			}
		}
		return count;
	}

	private static FontSettings draft(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof AdvancedFontScreen advanced
			? advanced.draft() : ((FontConfigScreen) mc.gui.screen()).draft());
	}

	private static String advancedLabel(ClientGameTestContext context) {
		return context.computeOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).advancedLabel().getString());
	}

	private static void selectFamily(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> ((FontConfigScreen) mc.gui.screen()).selectFamily(Fontsys.fontIndex().families(false).stream()
			.filter(f -> f.name().equals(name)).findFirst().orElseThrow()));
		context.waitTicks(3);
	}

	/** The current screen's control with this label. */
	private static ScreenRectangle widget(ClientGameTestContext context, String label) {
		return context.computeOnClient(mc -> mc.gui.screen().children().stream()
			.filter(c -> c instanceof AbstractWidget w && w.getMessage().getString().equals(label))
			.map(c -> ((AbstractWidget) c).getRectangle()).findFirst()
			.orElseThrow(() -> new AssertionError("no '" + label + "' on " + mc.gui.screen())));
	}

	/** Lets the preview rebuild, with the mouse out of the way of tooltips. */
	private static void settle(ClientGameTestContext context) {
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(20);
	}

	private static void click(ClientGameTestContext context, int guiX, int guiY) {
		context.getInput().setCursorPos(guiX * SCALE + SCALE / 2.0, guiY * SCALE + SCALE / 2.0);
		context.waitTicks(3);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** 0 is Auto. A new GUI scale reloads an automatic-sharpness font; wait for it. */
	private static void guiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
		context.waitTicks(40);
		context.waitFor(mc -> mc.gui.overlay() == null, 20 * 60);
		context.waitTicks(10);
	}

	/** The screenshot's pixels as 0xRRGGBB. */
	private static int[][] shoot(ClientGameTestContext context, String name) {
		Path shot = context.takeScreenshot(name);
		try (InputStream in = Files.newInputStream(shot); NativeImage image = NativeImage.read(in)) {
			int[][] pixels = new int[image.getHeight()][image.getWidth()];
			for (int y = 0; y < pixels.length; y++) {
				for (int x = 0; x < pixels[y].length; x++) {
					pixels[y][x] = image.getPixel(x, y) & 0xFFFFFF;
				}
			}
			return pixels;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Pixels that differ between two screenshots, from {@code left} to {@code right} (fractions
	 * of the width), and from {@code top} GUI units below the top to {@code bottom} above the bottom.
	 */
	private static int differingPixels(int[][] a, int[][] b, double left, double right, int top, int bottom) {
		int count = 0;
		int height = Math.min(a.length, b.length);
		for (int y = top * SCALE; y < height - bottom * SCALE; y++) {
			int width = Math.min(a[y].length, b[y].length);
			for (int x = (int) (width * left); x < (int) (width * right); x++) {
				if (a[y][x] != b[y][x]) {
					count++;
				}
			}
		}
		return count;
	}

	/** Pixels that look like a tooltip's dark purple background now but didn't before. */
	private static int tooltipPixels(int[][] before, int[][] after) {
		int count = 0;
		for (int y = 0; y < Math.min(before.length, after.length); y++) {
			for (int x = 0; x < Math.min(before[y].length, after[y].length); x++) {
				if (tooltipLike(after[y][x]) && !tooltipLike(before[y][x])) {
					count++;
				}
			}
		}
		return count;
	}

	/** The tooltip background, 0xF0100010, over anything. */
	private static boolean tooltipLike(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		return r <= 40 && g <= 18 && b <= 40 && Math.abs(r - b) <= 12 && r >= g + 4;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
