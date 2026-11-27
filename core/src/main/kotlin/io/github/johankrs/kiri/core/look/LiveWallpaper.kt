package io.github.johankrs.kiri.core.look

/**
 * The animated wallpapers Kiri draws itself: behind the TV's home screen, and on a phone or
 * tablet as Kiri's live wallpaper. None of them is a video or a picture: they are drawn from
 * a few shapes, small, and the screen stretches them, which is what keeps them light on a
 * weak TV.
 *
 * [share] is how large a scene is drawn, as a share of the screen's width and height. Soft
 * scenes lose nothing drawn smaller; ones with fine lines need more.
 */
enum class LiveScene(val key: String, val share: Float) {
    /** Large soft lights that change places slowly. */
    AURORA("aurora", 1f / 6f),

    /** Soft mist crossing the screen, thicker low down. */
    BRUMA("bruma", 1f / 4f),

    /** The threads of Kiri's icon in the air; where they cross a hidden cluster, they light it. */
    FIOS("fios", 1f / 2f),

    /** Faint glass tiles that a band of light lights up now and then. */
    PASTILHAS("pastilhas", 1f / 2f),

    /** Hills that stay put, with mist running between them and a low moon. */
    SERRA("serra", 1f / 3f),

    /** Paulownia flowers falling slowly, near ones large and soft, far ones small and faint. */
    FLORES("flores", 1f / 2f),
    ;

    companion object {
        fun fromKey(key: String?): LiveScene? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The six colors of Kiri's icon, which every live wallpaper comes in. [ground] and [deep] are
 * the background from its middle to its edges; [top] is the lilac of the flower, [mist] the
 * pale of its threads, [fog] the color of mist. A [light] one is pale: what is written over it
 * is dark.
 */
enum class LiveColor(
    val key: String,
    val ground: Int,
    val deep: Int,
    val top: Int,
    val mist: Int,
    val fog: Int,
    val light: Boolean = false,
) {
    LILAC("lilac", 0xFF3B2D66.toInt(), 0xFF1A1430.toInt(), 0xFFC4B0EE.toInt(), 0xFFF6F2FF.toInt(), 0xFFEFE8FF.toInt()),
    MIST("mist", 0xFFFFFFFF.toInt(), 0xFFE2DAF2.toInt(), 0xFF6A4DB4.toInt(), 0xFFA792DC.toInt(), 0xFF8C76CB.toInt(), light = true),
    SMOKE("smoke", 0xFF26464F.toInt(), 0xFF0C1C22.toInt(), 0xFFBFA9EC.toInt(), 0xFFE6F1EF.toInt(), 0xFFDCEFEB.toInt()),
    NIGHT("night", 0xFF16111F.toInt(), 0xFF000000.toInt(), 0xFFB9A3EA.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9E4F7.toInt()),
    ROSE("rose", 0xFF4D2552.toInt(), 0xFF1E0F24.toInt(), 0xFFF0B6D6.toInt(), 0xFFFDF0F7.toInt(), 0xFFFBE6F1.toInt()),
    BLUE("blue", 0xFF22305E.toInt(), 0xFF0C1230.toInt(), 0xFFAFC0F5.toInt(), 0xFFF2F5FF.toInt(), 0xFFE6ECFF.toInt()),
    ;

    companion object {
        fun fromKey(key: String?): LiveColor? = entries.firstOrNull { it.key == key }
    }
}

/** How fast a live wallpaper moves. [STILL] draws one picture and then nothing at all. */
enum class LiveMotion(val key: String, val speed: Float) {
    STILL("still", 0f),
    SLOW("slow", 0.5f),
    NORMAL("normal", 1f),
    ;

    companion object {
        fun fromKey(key: String?): LiveMotion? = entries.firstOrNull { it.key == key }
    }
}

/** One live wallpaper as chosen: which scene, in which color, how fast. */
data class LiveLook(
    val scene: LiveScene = LiveScene.AURORA,
    val color: LiveColor = LiveColor.LILAC,
    val motion: LiveMotion = LiveMotion.NORMAL,
) {
    /** How it is kept in the settings. */
    val stored: String get() = "${scene.key},${color.key},${motion.key}"

    companion object {
        /** The live wallpaper of a phone or tablet. The TV keeps its own in [io.github.johankrs.kiri.core.tv.TvLook]. */
        const val KEY = "wallpaper.live"

        fun fromStored(text: String?): LiveLook {
            val parts = text?.split(',') ?: return LiveLook()
            val default = LiveLook()
            return LiveLook(
                scene = LiveScene.fromKey(parts.getOrNull(0)) ?: default.scene,
                color = LiveColor.fromKey(parts.getOrNull(1)) ?: default.color,
                motion = LiveMotion.fromKey(parts.getOrNull(2)) ?: default.motion,
            )
        }
    }
}
