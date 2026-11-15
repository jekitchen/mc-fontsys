package io.github.jekitchen.fontsys.apply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jekitchen.fontsys.apply.QuickToggle.Outcome;
import io.github.jekitchen.fontsys.config.ConfigStore;
import io.github.jekitchen.fontsys.config.FontConfig;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.config.Hinting;
import io.github.jekitchen.fontsys.config.SystemFingerprint;
import io.github.jekitchen.fontsys.discovery.OsFamily;
import io.github.jekitchen.fontsys.platform.Platform;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The quick toggle key's decisions (PRD D59). */
class QuickToggleTest {
	private static final FontSettings TUNED = FontSettings.DEFAULTS.withEnabled(true)
		.withFont("/f/Avenir.ttc", 3, "Avenir Next", "Medium")
		.withLayout(12, 3, 0.5F, -1)
		.withApplyToUniform(true)
		.withHinting(Hinting.LIGHT);

	@Test
	void flipsOnlyTheMasterSwitch() {
		FontSettings off = QuickToggle.toggled(TUNED);
		assertFalse(off.isActive());
		assertEquals(TUNED.withEnabled(false), off, "every other setting is kept");
		assertEquals(TUNED, QuickToggle.toggled(off), "the next press is the undo");
	}

	@Test
	void decidesFromTheAppliedSettings() {
		assertEquals(Outcome.TURN_OFF, QuickToggle.decide(TUNED, false, false));
		assertEquals(Outcome.TURN_ON, QuickToggle.decide(TUNED.withEnabled(false), false, false));
	}

	@Test
	void neverPickedAFont() {
		assertEquals(Outcome.NO_FONT, QuickToggle.decide(FontSettings.DEFAULTS, false, false));
		// The master switch on without a font (a hand-edited config) is still "no font".
		assertEquals(Outcome.NO_FONT, QuickToggle.decide(FontSettings.DEFAULTS.withEnabled(true), false, false));
		assertEquals(Outcome.NO_FONT, QuickToggle.decide(TUNED.withFontPath(" "), false, false));
	}

	@Test
	void busyWinsSoNoSecondReloadStarts() {
		assertEquals(Outcome.BUSY, QuickToggle.decide(TUNED, false, true));
		assertEquals(Outcome.BUSY, QuickToggle.decide(FontSettings.DEFAULTS, true, true));
	}

	@Test
	void anotherFontModWins() {
		assertEquals(Outcome.STEPPED_ASIDE, QuickToggle.decide(TUNED, true, false));
		assertEquals(Outcome.STEPPED_ASIDE, QuickToggle.decide(FontSettings.DEFAULTS, true, false));
	}

	@Test
	void heldKeyCountsOnce() {
		QuickToggle toggle = new QuickToggle();
		assertFalse(toggle.freshPress(false, false));
		assertTrue(toggle.freshPress(true, true), "a press");
		assertFalse(toggle.freshPress(true, true), "key repeat while held");
		assertFalse(toggle.freshPress(false, true), "still held");
		assertFalse(toggle.freshPress(true, true), "key repeat after the reload");
		assertFalse(toggle.freshPress(false, false), "released");
		assertTrue(toggle.freshPress(true, true), "a new press");
	}

	@Test
	void tapsWithinOneTickEachCount() {
		QuickToggle toggle = new QuickToggle();
		// Pressed and released between two ticks: the key is already up when the tick sees the click.
		assertTrue(toggle.freshPress(true, false));
		assertTrue(toggle.freshPress(true, false));
	}

	@Test
	void configFromAnotherComputerHasNoFontToToggle(@TempDir Path dir) throws IOException {
		String here = SystemFingerprint.of(OsFamily.WINDOWS, "C:\\Users\\alex");
		ConfigStore store = new ConfigStore(dir.resolve("fontsys.json"));
		FontConfig config = FontConfig.defaults();
		config.systemFingerprint = SystemFingerprint.of(OsFamily.MACOS, "/Users/alex");
		config.setSettings(TUNED);
		store.save(config);

		FontController controller = new FontController(store, new Platform() {
			@Override
			public Path configDir() {
				return dir;
			}

			@Override
			public boolean isModLoaded(String id) {
				return false;
			}
		}, () -> here);
		assertEquals(Outcome.NO_FONT, QuickToggle.decide(controller.appliedSettings(), controller.stepAside(), false),
			"the reset config (5.6) has no font, so a press only points to the settings");
	}
}
