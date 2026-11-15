package io.github.jekitchen.fontsys.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.font.GlyphInfo;
import io.github.jekitchen.fontsys.config.Hinting;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.freetype.FT_Face;

/** Linear coverage (PRD D36), the thinner drop shadow (D37) and Classic shadow (D60). */
class TextQualityTest {
	@Test
	void coverageIsUploadedAsWhiteWithAlpha() {
		assertEquals(0x00FFFFFF, ColorAtlasGlyph.texel(0), "fully outside: transparent");
		assertEquals(0x80FFFFFF, ColorAtlasGlyph.texel(128), "half covered: white at half alpha, not gray");
		assertEquals(0xFFFFFFFF, ColorAtlasGlyph.texel(255));
	}

	@Test
	void shadowIsHalfAGuiUnitInWholeScreenPixels() {
		assertEquals(1.0F, TextShadow.offset(1), "one pixel at GUI scale 1, as vanilla");
		assertEquals(1 / 2.0F, TextShadow.offset(2), 1e-6, "1 px");
		assertEquals(2 / 3.0F, TextShadow.offset(3), 1e-6, "2 px");
		assertEquals(2 / 4.0F, TextShadow.offset(4), 1e-6, "2 px");
		assertEquals(3 / 5.0F, TextShadow.offset(5), 1e-6, "3 px");
		assertEquals(3 / 6.0F, TextShadow.offset(6), 1e-6, "3 px");
		assertEquals(1.0F, TextShadow.offset(0), "an unset scale falls back to vanilla");
	}

	/** Which faces' glyphs get the thinner shadow; the addresses are only keys, never read. */
	@Test
	void classicShadowAndCrispFacesKeepVanillasShadow() {
		FT_Face smooth = FT_Face.create(0x7F00_0010L);
		FT_Face classic = FT_Face.create(0x7F00_0020L);
		FT_Face crisp = FT_Face.create(0x7F00_0030L);
		try {
			SystemFaces.add(smooth, false, false, Hinting.DEFAULT, false);
			SystemFaces.add(classic, false, false, Hinting.DEFAULT, true);
			SystemFaces.add(crisp, false, true, Hinting.DEFAULT, false);
			assertTrue(SystemFaces.thinShadow(smooth), "smooth glyphs get the thinner shadow (D37)");
			assertFalse(SystemFaces.thinShadow(classic), "Classic shadow keeps vanilla's full unit (D60)");
			assertFalse(SystemFaces.thinShadow(crisp), "crisp glyphs keep vanilla's full unit");
			SystemFaces.remove(classic.address());
			SystemFaces.add(classic, false, false, Hinting.DEFAULT, false);
			assertTrue(SystemFaces.thinShadow(classic), "a reopened face takes the new setting");
		} finally {
			SystemFaces.remove(smooth.address());
			SystemFaces.remove(classic.address());
			SystemFaces.remove(crisp.address());
		}
	}

	@Test
	void thinShadowKeepsTheOtherMetrics() {
		GlyphInfo base = GlyphInfo.simple(7.5F);
		GlyphInfo thin = TextShadow.thin(base);
		assertEquals(7.5F, thin.getAdvance());
		assertEquals(base.getBoldOffset(), thin.getBoldOffset());
		assertEquals(base.getAdvance(true), thin.getAdvance(true));
	}
}
