package io.github.johankrs.kiri.core.tv

/** Who makes a console, as the ready-made banners are grouped. */
enum class ConsoleFamily(val key: String) {
    NINTENDO("nintendo"),
    PLAYSTATION("playstation"),
    XBOX("xbox"),
    SEGA("sega"),
    OTHERS("others"),
    PC("pc"),
}

/** The kind of line drawing a console gets when it has none of its own yet. */
enum class ConsoleDrawing { HYBRID, TOWER, FLAT, CUBE, CARTRIDGE, DISCBOX, HANDHELD, PC, NOTEBOOK, MINI }

/**
 * The styles of the ready-made banners. Each one has room for the big name: [room] is how wide
 * it may be, as a share of the banner's width in percent; [letter] how wide one letter is, as
 * a share of the letters' size; [caps] the largest size of a name of up to 2, up to 5 and more
 * letters, in percent of the banner's width.
 */
enum class ConsoleStyle(val key: String, private val room: Float, private val letter: Float, private val caps: FloatArray) {
    /** Dark, with the drawing lit in the console's color beside the name. */
    NOITE("noite", 50f, 0.56f, floatArrayOf(26f, 18f, 14f)),

    /** The console's colors, the name in the middle and the drawing large behind it. */
    COR("cor", 84f, 0.58f, floatArrayOf(28f, 20f, 16f)),

    /** The name on a pane of glass over soft lights. */
    VIDRO("vidro", 58f, 0.58f, floatArrayOf(22f, 16f, 13f)),

    /** A sun and a grid going into the distance, the name in slanted capitals. */
    RETRO("retro", 82f, 0.74f, floatArrayOf(24f, 17f, 14f)),

    /** Slanted stripes of the console's colors beside the name. */
    FAIXAS("faixas", 50f, 0.6f, floatArrayOf(26f, 17f, 13.5f)),
    ;

    /**
     * Whether the name is too long to stand beside the drawing at its full size: in [NOITE] it
     * then takes more room and the drawing gets smaller.
     */
    fun isLong(name: String): Boolean = this == NOITE && name.length > LONG_NAME

    /**
     * The size of the big [name], in percent of the banner's width: the largest that fits.
     * [beside] is the drawing next to it in [NOITE], which takes some of the name's room.
     */
    fun nameSize(name: String, beside: BesideDrawing = BesideDrawing.GENERIC): Float {
        val length = name.length.coerceAtLeast(1)
        val space = when {
            isLong(name) -> LONG_ROOM
            this == NOITE && beside == BesideDrawing.TALL -> TALL_ROOM
            this == NOITE && beside == BesideDrawing.WIDE -> WIDE_ROOM
            else -> room
        }
        val cap = when {
            length <= 2 -> caps[0]
            length <= 5 -> caps[1]
            else -> caps[2]
        }
        return minOf(cap, space / (length * letter))
    }

    companion object {
        private const val LONG_NAME = 8
        private const val LONG_ROOM = 62f
        private const val TALL_ROOM = 46f
        private const val WIDE_ROOM = 40f

        fun fromKey(key: String?): ConsoleStyle? = entries.firstOrNull { it.key == key }
    }
}

/** The shape of the drawing beside the name, which decides how much room the name keeps. */
enum class BesideDrawing { GENERIC, TALL, WIDE }

/**
 * A console or computer with a ready-made banner. [brandLine] is the small line above the big
 * [nameLine]; [pickerName] is how it is named where it is chosen. Colors are 0xRRGGBB: two for
 * the ground, two lights, and [lightGround] when the ground is light (the text is then dark in
 * the styles that sit on it).
 */
data class Console(
    val id: String,
    val family: ConsoleFamily,
    val fullName: String,
    val brandLine: String,
    val nameLine: String,
    val drawing: ConsoleDrawing,
    val ground: Int,
    val groundDeep: Int,
    val light: Int,
    val lightSecond: Int,
    val lightGround: Boolean,
    val pickerName: String = nameLine,
)

/** Every console and computer there is a ready-made banner for, in the order they are offered. */
object TvConsoles {
    /** In place of a brand line or a name that is said in the person's language. */
    const val WORD_COMPUTER = "@computer"
    const val WORD_CLASSIC = "@classic"

    val ALL: List<Console> = listOf(
        Console("sw2", ConsoleFamily.NINTENDO, "Switch 2", "Nintendo", "Switch 2", ConsoleDrawing.HYBRID, 0x2A2D34, 0x131519, 0xFF4B5C, 0x22C3EE, false),
        Console("swo", ConsoleFamily.NINTENDO, "Switch OLED", "Nintendo", "Switch OLED", ConsoleDrawing.HYBRID, 0xF5F6F8, 0xD6DAE1, 0xFF4B5C, 0x22C3EE, true),
        Console("sw", ConsoleFamily.NINTENDO, "Switch", "Nintendo", "Switch", ConsoleDrawing.HYBRID, 0xE4323F, 0xA3101C, 0xFF5D69, 0x22C1EE, false),
        Console("wiiu", ConsoleFamily.NINTENDO, "Wii U", "Nintendo", "Wii U", ConsoleDrawing.FLAT, 0x1A9CD4, 0x0A6AA3, 0x5FD0FF, 0xFFFFFF, false),
        Console("wii", ConsoleFamily.NINTENDO, "Wii", "Nintendo", "Wii", ConsoleDrawing.TOWER, 0xF6F7F8, 0xD9DEE3, 0x30B5E8, 0x8F99A4, true),
        Console("gc", ConsoleFamily.NINTENDO, "GameCube", "Nintendo", "GameCube", ConsoleDrawing.CUBE, 0x5B44B2, 0x2C206C, 0x9C86FF, 0xFFB03B, false),
        Console("n64", ConsoleFamily.NINTENDO, "Nintendo 64", "Nintendo", "64", ConsoleDrawing.CARTRIDGE, 0x2E3033, 0x151619, 0x2FB34A, 0xF2C230, false),
        Console("snes", ConsoleFamily.NINTENDO, "Super Nintendo", "Super", "Nintendo", ConsoleDrawing.CARTRIDGE, 0xE2E2E8, 0xC2C2CD, 0x6B56C6, 0xA99BEA, true, "Super Nintendo"),
        Console("nes", ConsoleFamily.NINTENDO, "NES", "Nintendo", "NES", ConsoleDrawing.CARTRIDGE, 0xD9D9D9, 0xB5B5B5, 0xD1262E, 0x3A3A3A, true),
        Console("ps5p", ConsoleFamily.PLAYSTATION, "PlayStation 5 Pro", "PlayStation", "5 Pro", ConsoleDrawing.TOWER, 0x1B2440, 0x0A0E1B, 0x5B8CFF, 0xD9E4FF, false),
        Console("ps5", ConsoleFamily.PLAYSTATION, "PlayStation 5", "PlayStation", "5", ConsoleDrawing.TOWER, 0x2A5ED8, 0x0F2E86, 0x7FB0FF, 0xFFFFFF, false),
        Console("ps5s", ConsoleFamily.PLAYSTATION, "PlayStation 5 Slim", "PlayStation", "5 Slim", ConsoleDrawing.TOWER, 0xF3F5F9, 0xD3DAE6, 0x2F6AF0, 0x101A33, true),
        Console("ps5d", ConsoleFamily.PLAYSTATION, "PlayStation 5 Digital", "PlayStation", "5 Digital", ConsoleDrawing.TOWER, 0x1F3F9C, 0x0B1C55, 0x6AA2FF, 0xC9D8FF, false),
        Console("ps4p", ConsoleFamily.PLAYSTATION, "PlayStation 4 Pro", "PlayStation", "4 Pro", ConsoleDrawing.FLAT, 0x161A24, 0x05070A, 0x3F7BFF, 0x9AA9C4, false),
        Console("ps4s", ConsoleFamily.PLAYSTATION, "PlayStation 4 Slim", "PlayStation", "4 Slim", ConsoleDrawing.FLAT, 0x1D2A52, 0x0C1229, 0x4C86FF, 0xE8EDF7, false),
        Console("ps4", ConsoleFamily.PLAYSTATION, "PlayStation 4", "PlayStation", "4", ConsoleDrawing.FLAT, 0x123A8F, 0x071A48, 0x3D7DFF, 0xFFFFFF, false),
        Console("ps3", ConsoleFamily.PLAYSTATION, "PlayStation 3", "PlayStation", "3", ConsoleDrawing.FLAT, 0x2E2E34, 0x0B0B0D, 0xC7CBD4, 0x3B6FE0, false),
        Console("ps2", ConsoleFamily.PLAYSTATION, "PlayStation 2", "PlayStation", "2", ConsoleDrawing.TOWER, 0x1A2050, 0x090B1E, 0x4A6AF0, 0xC0C6DC, false),
        Console("ps1", ConsoleFamily.PLAYSTATION, "PlayStation 1", "PlayStation", "1", ConsoleDrawing.DISCBOX, 0xD6D4CE, 0xB3B0A8, 0x5A6070, 0xD8313A, true),
        Console("xsx", ConsoleFamily.XBOX, "Xbox Series X", "Xbox", "Series X", ConsoleDrawing.TOWER, 0x167A2C, 0x0A4518, 0x43E06A, 0xE8FFE8, false),
        Console("xss", ConsoleFamily.XBOX, "Xbox Series S", "Xbox", "Series S", ConsoleDrawing.FLAT, 0xF3F4F3, 0xD4D9D4, 0x1F9E46, 0x2A2E2A, true),
        Console("xox", ConsoleFamily.XBOX, "Xbox One X", "Xbox", "One X", ConsoleDrawing.FLAT, 0x222524, 0x0A0B0B, 0x3BD65E, 0xA7B0AA, false),
        Console("xos", ConsoleFamily.XBOX, "Xbox One S", "Xbox", "One S", ConsoleDrawing.FLAT, 0xEEF0EE, 0xCDD3CD, 0x2AA34D, 0x6C746E, true),
        Console("xo", ConsoleFamily.XBOX, "Xbox One", "Xbox", "One", ConsoleDrawing.FLAT, 0x2A2D2B, 0x121413, 0x4CC35C, 0xD0D6D1, false),
        Console("x360", ConsoleFamily.XBOX, "Xbox 360", "Xbox", "360", ConsoleDrawing.FLAT, 0xEEF1EC, 0xD1D8CC, 0x7CC243, 0x3F8F2A, true),
        Console("xc", ConsoleFamily.XBOX, "Xbox $WORD_CLASSIC", "Xbox", WORD_CLASSIC, ConsoleDrawing.FLAT, 0x1D3A1C, 0x0B170B, 0x69D43C, 0xC8FF9A, false),
        Console("md", ConsoleFamily.SEGA, "Mega Drive", "Sega", "Mega Drive", ConsoleDrawing.CARTRIDGE, 0x222225, 0x0A0A0B, 0xE0262D, 0xF3C13A, false),
        Console("ms", ConsoleFamily.SEGA, "Master System", "Sega", "Master System", ConsoleDrawing.CARTRIDGE, 0x28282C, 0x0E0E10, 0xE33A3A, 0xE8E8E8, false),
        Console("sat", ConsoleFamily.SEGA, "Saturn", "Sega", "Saturn", ConsoleDrawing.DISCBOX, 0x2C2C3C, 0x12121B, 0x8C7CFF, 0xF2B33A, false),
        Console("dc", ConsoleFamily.SEGA, "Dreamcast", "Sega", "Dreamcast", ConsoleDrawing.DISCBOX, 0xF4F4F1, 0xD6D6CF, 0xF0661C, 0x2D6BD8, true),
        Console("a26", ConsoleFamily.OTHERS, "Atari 2600", "Atari", "2600", ConsoleDrawing.CARTRIDGE, 0x6A4426, 0x2C1A0D, 0xFF8A2A, 0xE8463A, false, "Atari 2600"),
        Console("neo", ConsoleFamily.OTHERS, "Neo Geo", "SNK", "Neo Geo", ConsoleDrawing.CARTRIDGE, 0x1D1D1D, 0x090909, 0xE3B13A, 0xE33A2E, false, "Neo Geo"),
        Console("deck", ConsoleFamily.OTHERS, "Steam Deck", "Valve", "Steam Deck", ConsoleDrawing.HANDHELD, 0x1C2738, 0x0B111A, 0x5FB8F0, 0x1A9FFF, false, "Steam Deck"),
        Console("smach", ConsoleFamily.OTHERS, "Steam Machine", "Valve", "Steam Machine", ConsoleDrawing.MINI, 0x1A1D22, 0x0A0B0E, 0x7FC4FF, 0xC9D2DC, false, "Steam Machine"),
        Console("pc", ConsoleFamily.PC, "PC", WORD_COMPUTER, "PC", ConsoleDrawing.PC, 0x2A3A52, 0x121B29, 0x7FA8FF, 0xCFD8E6, false),
        Console("pcg", ConsoleFamily.PC, "PC Gamer", WORD_COMPUTER, "PC Gamer", ConsoleDrawing.PC, 0x2A0F55, 0x0D0620, 0xFF3DE0, 0x33F0FF, false),
        Console("nb", ConsoleFamily.PC, "Notebook", WORD_COMPUTER, "Notebook", ConsoleDrawing.NOTEBOOK, 0x0F4B52, 0x06262B, 0x3FD6C4, 0xE0FFFB, false),
        Console("mpc", ConsoleFamily.PC, "Mini PC", WORD_COMPUTER, "Mini PC", ConsoleDrawing.MINI, 0x3A3F47, 0x1A1D22, 0xF2A541, 0xE0E4EA, false),
    )

    private val byId: Map<String, Console> = ALL.associateBy { it.id }

    fun byId(id: String): Console? = byId[id]

    fun of(family: ConsoleFamily): List<Console> = ALL.filter { it.family == family }

    /**
     * The console [step]s away from [from] within its family, going round; or, with [from]
     * null, the first of [family].
     */
    fun stepModel(from: Console, step: Int): Console {
        val list = of(from.family)
        val at = list.indexOf(from)
        return list[Math.floorMod(at + step, list.size)]
    }

    /**
     * The family [step]s away from that of [from], going round through "none" (null): the
     * first console of it, or null for no ready-made banner.
     */
    fun stepFamily(from: Console?, step: Int): Console? {
        val families = listOf<ConsoleFamily?>(null) + ConsoleFamily.entries
        val at = families.indexOf(from?.family)
        val next = families[Math.floorMod(at + step, families.size)] ?: return null
        return of(next).first()
    }
}
