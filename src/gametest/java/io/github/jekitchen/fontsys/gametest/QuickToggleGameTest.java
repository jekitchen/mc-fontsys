package io.github.jekitchen.fontsys.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.config.ConfigStore;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.discovery.FaceInfo;
import io.github.jekitchen.fontsys.pack.PackContents;
import io.github.jekitchen.fontsys.platform.ClientHooks;
import io.github.jekitchen.fontsys.ui.KeepOrRevertScreen;
import java.util.Arrays;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The quick toggle key (PRD D59): registered unbound in the Fontsys category; a press switches
 * between the player's font and Minecraft's, keeps every other setting and saves the result;
 * with no font picked it only says how to pick one; presses during a reload, during the
 * keep-or-revert countdown or into a text box change nothing; a font that won't load says so.
 * In the shared in-world scene, where the key works (vanilla clicks key mappings only while no
 * screen is open).
 */
@SuppressWarnings("UnstableApiUsage")
public class QuickToggleGameTest implements FabricClientGameTest {
	/** Free in vanilla 26.3, and not used by Sodium or Iris. */
	private static final InputConstants.Key TEST_KEY = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);
		KeyMapping key = context.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings)
			.filter(k -> k.getName().equals("key.fontsys.toggle")).findFirst().orElse(null));
		require(key != null, "the toggle key is registered");
		require(key.getDefaultKey().equals(InputConstants.UNKNOWN), "the toggle key is unbound by default, not " + key.getDefaultKey().getName());
		require(key.getCategory().id().equals(ClientHooks.KEY_CATEGORY), "the toggle key is in the Fontsys category");

		Optional<FaceInfo> face = GameTestFonts.pick(context, "Segoe UI", "Arial", "Helvetica Neue", "DejaVu Sans");
		if (face.isEmpty()) {
			Fontsys.LOGGER.warn("[gametest] No system fonts found; skipping the toggle key test.");
			return;
		}
		// A player who never picked a font.
		GameTestFonts.apply(context, FontSettings.DEFAULTS);
		context.runOnClient(mc -> {
			key.setKey(TEST_KEY);
			KeyMapping.resetMapping();
		});

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			InWorldTextGameTest.buildScene(world);
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);

			// No font picked: a pointer to the settings, and nothing changes.
			int generation = generation();
			context.getInput().pressKey(key);
			context.waitTicks(10);
			requireMessage("fontsys.toggle.no_font");
			require(generation() == generation && !Fontsys.controller().isReloading(), "no font picked: no reload");
			require(Fontsys.controller().appliedSettings().equals(FontSettings.DEFAULTS), "no font picked: settings unchanged");
			context.takeScreenshot("d59-toggle-no-font");

			// A kept font with its own tuning; the toggle switches it off and on, keeping the tuning.
			FontSettings kept = GameTestFonts.settingsFor(face.get()).withLayout(12, FontSettings.AUTO_OVERSAMPLE, 0, 0.5F).withApplyToUniform(true);
			GameTestFonts.apply(context, kept);
			require(PackContents.loaded().active(), "the font is applied before toggling");

			toggle(context, key);
			require(!PackContents.loaded().active(), "toggle: Minecraft's font");
			require(Fontsys.controller().appliedSettings().equals(kept.withEnabled(false)), "toggle off keeps every other setting");
			require(saved().equals(kept.withEnabled(false)), "toggle off is saved");
			require(Fontsys.controller().pendingSettings() == null, "no keep-or-revert dialog");
			requireMessage("fontsys.toggle.off");
			context.takeScreenshot("d59-toggle-vanilla");

			toggle(context, key);
			require(PackContents.loaded().active() && PackContents.providerLoaded(), "toggle again: the player's font");
			require(Fontsys.controller().appliedSettings().equals(kept), "toggle on restores the same settings");
			require(saved().equals(kept), "toggle on is saved");
			requireMessage("fontsys.toggle.on");
			context.takeScreenshot("d59-toggle-font");

			// A second press while the first one's reload runs is dropped: one reload, one switch.
			generation = generation();
			context.getInput().pressKey(key);
			context.waitTicks(2);
			require(Fontsys.controller().isReloading() || context.computeOnClient(mc -> mc.gui.overlay() != null), "the first press started a reload");
			context.getInput().pressKey(key);
			int before = generation;
			context.waitFor(mc -> generation() > before, 20 * 60);
			context.waitFor(mc -> mc.gui.overlay() == null, 20 * 30);
			context.waitTicks(60);
			require(generation() == before + 1, "a press during a reload starts no second reload (" + (generation() - before) + " reloads)");
			require(!Fontsys.controller().appliedSettings().isActive(), "a press during a reload is dropped, not queued");
			toggle(context, key);
			require(Fontsys.controller().appliedSettings().equals(kept), "back on");

			// During the keep-or-revert countdown: the dialog is a screen, so vanilla doesn't click the
			// key, and a click already queued is dropped too.
			FontSettings bigger = kept.withLayout(14, kept.oversample(), kept.shiftX(), kept.shiftY());
			context.runOnClient(mc -> Fontsys.controller().applyWithConfirmation(bigger, null));
			context.waitForScreen(KeepOrRevertScreen.class);
			context.waitFor(mc -> mc.gui.overlay() == null);
			generation = generation();
			context.getInput().pressKey(key);
			context.runOnClient(mc -> KeyMapping.click(TEST_KEY));
			context.waitTicks(10);
			require(generation() == generation, "no reload during the countdown");
			require(Fontsys.controller().isAwaitingConfirmation() && Fontsys.controller().appliedSettings().equals(bigger),
				"the countdown and the change it asks about are untouched");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.overlay() == null && mc.gui.screen() == null, 20 * 30);
			context.waitTicks(20);
			require(Fontsys.controller().appliedSettings().equals(kept), "Esc reverts as before");

			// Typing into the chat box: the key goes to the text box, not the toggle.
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(5);
			generation = generation();
			context.getInput().pressKey(key);
			context.waitTicks(10);
			require(generation() == generation && Fontsys.controller().appliedSettings().equals(kept), "a press into a focused text box doesn't toggle");
			context.setScreen(() -> null);
			context.waitTicks(5);

			// A font that won't load: switching it on says so, and the game stays in Minecraft's font (AP-7).
			FontSettings missing = kept.withFont(face.get().path() + ".missing", 0, "No Such Family", "Regular").withEnabled(false);
			GameTestFonts.apply(context, missing);
			toggle(context, key);
			require(!PackContents.loaded().active(), "a missing font falls back to Minecraft's");
			requireMessage("fontsys.toggle.failed");

			GameTestFonts.apply(context, FontSettings.DEFAULTS);
		}
		context.waitForScreen(TitleScreen.class);
		context.runOnClient(mc -> {
			key.setKey(InputConstants.UNKNOWN);
			KeyMapping.resetMapping();
		});
	}

	/** Presses the toggle key and waits for its reload and message. */
	private static void toggle(ClientGameTestContext context, KeyMapping key) {
		int before = generation();
		context.getInput().pressKey(key);
		context.waitFor(mc -> generation() > before, 20 * 60);
		context.waitFor(mc -> mc.gui.overlay() == null, 20 * 30);
		context.waitTicks(20);
	}

	private static int generation() {
		return Fontsys.controller().fontGeneration();
	}

	/** The settings in {@code config/fontsys.json}, as the next start would read them. */
	private static FontSettings saved() {
		return new ConfigStore(Fontsys.platform().configDir().resolve(Fontsys.MOD_ID + ".json"), Fontsys.platform().gameDir())
			.load().config().settings();
	}

	private static void requireMessage(String key) {
		Component message = ClientHooks.quickToggle().lastMessage();
		String actual = message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
		require(key.equals(actual), "message " + key + ", not " + actual);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
