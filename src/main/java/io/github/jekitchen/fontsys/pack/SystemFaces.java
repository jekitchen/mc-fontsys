package io.github.jekitchen.fontsys.pack;

import io.github.jekitchen.fontsys.config.Hinting;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.freetype.FT_Face;

/**
 * FreeType faces opened for system fonts (applied or preview), so render-time fixes stay
 * scoped to ours, and the subset FreeType emboldens because the family has no heavier face
 * (PRD AP-8), and the subset rendered with hard edges in crisp mode (AP-10, D34), and each
 * face's hinting (D53), and the smooth faces drawn with vanilla's full-unit shadow (D60).
 * Preview faces carry the draft's settings, applied faces the applied ones.
 */
public final class SystemFaces {
	private static final Set<Long> FACES = ConcurrentHashMap.newKeySet();
	private static final Set<Long> SYNTHETIC_BOLD = ConcurrentHashMap.newKeySet();
	private static final Set<Long> CRISP = ConcurrentHashMap.newKeySet();
	private static final Map<Long, Hinting> HINTING = new ConcurrentHashMap<>();
	private static final Set<Long> CLASSIC_SHADOW = ConcurrentHashMap.newKeySet();

	private SystemFaces() {
	}

	public static void add(FT_Face face, boolean syntheticBold, boolean crisp) {
		add(face, syntheticBold, crisp, Hinting.DEFAULT);
	}

	public static void add(FT_Face face, boolean syntheticBold, boolean crisp, Hinting hinting) {
		add(face, syntheticBold, crisp, hinting, false);
	}

	/** @param classicShadow draw smooth glyphs with vanilla's full-unit shadow (D60) */
	public static void add(FT_Face face, boolean syntheticBold, boolean crisp, Hinting hinting, boolean classicShadow) {
		if (face != null) {
			FACES.add(face.address());
			if (syntheticBold) {
				SYNTHETIC_BOLD.add(face.address());
			}
			if (crisp) {
				CRISP.add(face.address());
			}
			if (Hinting.orDefault(hinting) != Hinting.DEFAULT) {
				HINTING.put(face.address(), hinting);
			}
			if (classicShadow) {
				CLASSIC_SHADOW.add(face.address());
			}
		}
	}

	public static void remove(long address) {
		FACES.remove(address);
		SYNTHETIC_BOLD.remove(address);
		CRISP.remove(address);
		HINTING.remove(address);
		CLASSIC_SHADOW.remove(address);
	}

	public static boolean contains(FT_Face face) {
		return face != null && FACES.contains(face.address());
	}

	public static boolean isSyntheticBold(FT_Face face) {
		return face != null && SYNTHETIC_BOLD.contains(face.address());
	}

	public static boolean isCrisp(FT_Face face) {
		return face != null && CRISP.contains(face.address());
	}

	/**
	 * Whether one of our faces draws with the thinner shadow ({@code TextShadow}, D37): when it is
	 * smooth, unless Classic shadow is on (D60). Crisp glyphs keep vanilla's full unit.
	 */
	public static boolean thinShadow(FT_Face face) {
		return !isCrisp(face) && !(face != null && CLASSIC_SHADOW.contains(face.address()));
	}

	/** The face's hinting; Full for faces that aren't ours, which vanilla loads unchanged. */
	public static Hinting hinting(FT_Face face) {
		return face == null ? Hinting.DEFAULT : HINTING.getOrDefault(face.address(), Hinting.DEFAULT);
	}

	/**
	 * Flags to add to every {@code FT_Load_Glyph} for this face, measuring and rendering alike:
	 * the monochrome target for a crisp face (which ignores the hinting setting), the hinting
	 * mode's flags for another of ours, and none for a face that isn't ours.
	 */
	public static int loadFlags(FT_Face face) {
		return isCrisp(face) ? CrispGlyphs.LOAD_TARGET_MONO : HintingFlags.loadFlags(hinting(face));
	}
}
