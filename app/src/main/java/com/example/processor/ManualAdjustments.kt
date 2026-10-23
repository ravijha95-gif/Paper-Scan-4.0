package com.example.processor

/**
 * Advanced image enhancement parameters providing fine-grained manual control
 * over color balance, tonal range, detail sharpness, and paper aesthetics.
 */
data class ManualAdjustments(
    val lightness: Float = 0f,       // -1.0 to 1.0 (default 0.0)
    val contrast: Float = 1.0f,      // 0.5 to 2.0 (default 1.0)
    val saturation: Float = 1.0f,    // 0.0 to 2.5 (default 1.0)
    val temperature: Float = 0f,     // -1.0 (cool/blue) to 1.0 (warm/amber)
    val warmth: Float = 0f,          // -1.0 to 1.0
    val tint: Float = 0f,            // -1.0 (green) to 1.0 (magenta)
    val hue: Float = 0f,             // -180° to 180°
    val highlights: Float = 0f,      // -1.0 to 1.0 (paper highlight control)
    val shadows: Float = 0f,         // -1.0 to 1.0 (shadow lift/darken)
    val sharpness: Float = 0.5f,     // 0.0 to 2.0 (0.5 default optimal unsharp mask)
    val fade: Float = 0f,            // 0.0 to 1.0 (vintage/matte fade)
    val vignette: Float = 0f         // -1.0 to 1.0 (0 neutral, negative brightens corners, positive darkens)
) {
    fun isDefault(): Boolean {
        return lightness == 0f &&
                contrast == 1.0f &&
                saturation == 1.0f &&
                temperature == 0f &&
                warmth == 0f &&
                tint == 0f &&
                hue == 0f &&
                highlights == 0f &&
                shadows == 0f &&
                sharpness == 0.5f &&
                fade == 0f &&
                vignette == 0f
    }
}
