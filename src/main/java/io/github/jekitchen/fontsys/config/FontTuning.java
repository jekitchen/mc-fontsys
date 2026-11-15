package io.github.jekitchen.fontsys.config;

import org.jspecify.annotations.Nullable;

/**
 * Everything in {@link FontSettings} that tunes how a font is drawn, as opposed to which font:
 * size, sharpness, shifts, Replace Unicode font, Crisp edges, hinting and Classic shadow (D60). Each recent font
 * remembers the tuning it was kept with, and picking that font again restores it (PRD UI-17,
 * D55), since a shift or size that suits one font rarely suits another.
 */
public record FontTuning(
	float size,
	float oversample,
	float shiftX,
	float shiftY,
	boolean applyToUniform,
	boolean crispEdges,
	@Nullable Hinting hinting,
	boolean classicShadow) {

	/** The tuning of a font picked for the first time. */
	public static final FontTuning DEFAULTS = of(FontSettings.DEFAULTS);

	/** Tuning with the thinner shadow, as before Classic shadow was a setting; a tuning saved by 1.2.0 reads the same way. */
	public FontTuning(float size, float oversample, float shiftX, float shiftY, boolean applyToUniform, boolean crispEdges, @Nullable Hinting hinting) {
		this(size, oversample, shiftX, shiftY, applyToUniform, crispEdges, hinting, false);
	}

	public static FontTuning of(FontSettings settings) {
		return new FontTuning(settings.size(), settings.oversample(), settings.shiftX(), settings.shiftY(),
			settings.applyToUniform(), settings.crispEdges(), settings.hinting(), settings.classicShadow());
	}

	/** {@code settings} with this tuning: the same font and on/off state, clamped (D16). */
	public FontSettings applyTo(FontSettings settings) {
		return new FontSettings(settings.enabled(), settings.fontPath(), settings.faceIndex(), settings.familyName(), settings.styleName(),
			size, oversample, shiftX, shiftY, applyToUniform, crispEdges, Hinting.orDefault(hinting), classicShadow).sanitized();
	}

	/** Clamped and defaulted, as read from a config file that may be hand-edited or old (D16). */
	public FontTuning sanitized() {
		return of(applyTo(FontSettings.DEFAULTS));
	}
}
