package ir.pelc.core.model

/**
 * Platform-neutral glyph outline encoding.
 *
 * A glyph outline is a FloatArray of commands. Each command starts with one
 * opcode followed by a fixed number of coordinates:
 *
 *  - [MOVE]  : 2 floats (x, y)
 *  - [LINE]  : 2 floats (x, y)
 *  - [QUAD]  : 4 floats (controlX, controlY, x, y)
 *  - [CUBIC] : 6 floats (c1x, c1y, c2x, c2y, x, y)
 *  - [CLOSE] : no floats
 *
 * Coordinates are in "outline base units": the outline of a font at an
 * em size of [BASE_EM] pixels, relative to the glyph origin. Renderers and
 * exporters scale coordinates by fontSize / BASE_EM.
 */
object OutlineOps {
    const val MOVE = 0
    const val LINE = 1
    const val QUAD = 2
    const val CUBIC = 3
    const val CLOSE = 4

    /** Em size used when extracting outlines from the font (pixels). */
    const val BASE_EM = 100f
}
