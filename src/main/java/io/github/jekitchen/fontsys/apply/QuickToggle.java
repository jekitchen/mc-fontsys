package io.github.jekitchen.fontsys.apply;

import io.github.jekitchen.fontsys.Fontsys;
import io.github.jekitchen.fontsys.compat.KnownFontMods;
import io.github.jekitchen.fontsys.config.FontSettings;
import io.github.jekitchen.fontsys.pack.PackContents;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The quick toggle key (PRD D59): one press switches between the player's font and Minecraft's
 * own, and the next press switches back. Only the master switch ({@link FontSettings#enabled()})
 * changes, through {@link FontController#apply}, so the choice is saved like any other change
 * and survives a restart. It skips the keep-or-revert dialog (5.7): both fonts are ones the
 * player has already seen and kept, and the next press is the undo.
 */
public final class QuickToggle {
	/** What a press does. */
	public enum Outcome {
		/** Switches to the player's font. */
		TURN_ON,
		/** Switches to Minecraft's font. */
		TURN_OFF,
		/** No font was ever picked (or the config was reset, 5.6): nothing to switch to. */
		NO_FONT,
		/** Another font mod is loaded, so Fontsys leaves the font alone (AP-12). */
		STEPPED_ASIDE,
		/** A reload or a keep-or-revert countdown is running: the press is dropped. */
		BUSY
	}

	private boolean held;
	private volatile @Nullable Component lastMessage;

	/**
	 * What a press does, given the applied settings. Busy wins, so a press during a reload or
	 * the keep-or-revert countdown never starts a second reload or overrides the dialog.
	 */
	public static Outcome decide(FontSettings applied, boolean stepAside, boolean busy) {
		if (busy) {
			return Outcome.BUSY;
		}
		if (stepAside) {
			return Outcome.STEPPED_ASIDE;
		}
		if (applied.fontPath() == null || applied.fontPath().isBlank()) {
			return Outcome.NO_FONT;
		}
		return applied.isActive() ? Outcome.TURN_OFF : Outcome.TURN_ON;
	}

	/** The settings a press applies: the master switch flipped, every other setting kept. */
	public static FontSettings toggled(FontSettings applied) {
		return applied.withEnabled(!applied.isActive());
	}

	/**
	 * Whether this tick's clicks are a new press. SDL3 key-repeat events click the mapping too
	 * (26.3 {@code KeyboardHandler.keyPress} clicks for any action but release), so a held key
	 * would toggle again after every reload; it counts once until it is let go.
	 *
	 * @param clicked the mapping was clicked since the last tick
	 * @param down the mapping's key is down now
	 */
	public boolean freshPress(boolean clicked, boolean down) {
		boolean fresh = clicked && !held;
		held = fresh ? down : held && down;
		return fresh;
	}

	/**
	 * Handles this tick's clicks of the toggle key: switches the font, or says why not. Vanilla
	 * clicks key mappings only while no screen is open, so a press typed into a text box never
	 * gets here; a click queued just before a screen opened is dropped too, for the same reason.
	 *
	 * @param openKey the settings key, named in the message when no font has been picked
	 */
	public void tick(Minecraft mc, KeyMapping toggleKey, KeyMapping openKey) {
		boolean clicked = false;
		while (toggleKey.consumeClick()) {
			clicked = true;
		}
		if (!freshPress(clicked, toggleKey.isDown()) || mc.gui.screen() != null) {
			return;
		}
		FontController controller = Fontsys.controller();
		FontSettings applied = controller.appliedSettings();
		boolean busy = controller.isReloading() || mc.gui.overlay() != null
			|| controller.isAwaitingConfirmation() || controller.pendingSettings() != null;
		switch (decide(applied, controller.stepAside(), busy)) {
			// The loading screen or the keep-or-revert dialog already shows what's going on.
			case BUSY -> { }
			case STEPPED_ASIDE -> show(mc, Component.translatable("fontsys.toggle.conflict",
				controller.conflict().map(KnownFontMods.KnownMod::name).orElse("")));
			case NO_FONT -> show(mc, openKey.isUnbound()
				? Component.translatable("fontsys.toggle.no_font.unbound")
				: Component.translatable("fontsys.toggle.no_font", openKey.getTranslatedKeyMessage()));
			case TURN_ON, TURN_OFF -> {
				FontSettings next = toggled(applied);
				CompletableFuture<Void> reload = controller.apply(next);
				reload.whenComplete((ok, error) -> mc.execute(() -> show(mc, result(next, error == null))));
			}
		}
	}

	/** After the reload: which font is now drawn, checked against what really loaded (AP-7 fallback). */
	private static Component result(FontSettings next, boolean reloaded) {
		if (!next.isActive()) {
			return Component.translatable("fontsys.toggle.off");
		}
		if (reloaded && PackContents.loaded().active() && PackContents.providerLoaded()) {
			return Component.translatable("fontsys.toggle.on", next.familyName());
		}
		return Component.translatable("fontsys.toggle.failed", next.familyName());
	}

	/** Above the hotbar, where vanilla shows "Now playing", and read out when the narrator is on. */
	private void show(Minecraft mc, Component message) {
		lastMessage = message;
		mc.gui.hud.setOverlayMessage(message, false);
		mc.getNarrator().saySystemNow(message);
	}

	/** The last message shown; for the game tests. */
	public @Nullable Component lastMessage() {
		return lastMessage;
	}
}
