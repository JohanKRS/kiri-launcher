package io.github.johankrs.kiri.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.johankrs.kiri.core.tv.TvInputKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One of the TV's own inputs: its antenna, an HDMI port, AV. [ownName] is what the TV calls it,
 * or what the person named it in the TV's settings.
 */
data class TvInput(val id: String, val ownName: String, val kind: TvInputKind)

/** Reads the TV's inputs and switches to one. Where the device is no TV there are none. */
@Stable
class TvInputs private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val manager: TvInputManager? = runCatching { appContext.getSystemService(TvInputManager::class.java) }.getOrNull()

    /** Goes up whenever an input comes, goes or changes. */
    var changes by mutableIntStateOf(0)
        private set

    init {
        runCatching {
            manager?.registerCallback(
                object : TvInputManager.TvInputCallback() {
                    override fun onInputAdded(inputId: String) {
                        changes++
                    }

                    override fun onInputRemoved(inputId: String) {
                        changes++
                    }

                    override fun onInputUpdated(inputId: String) {
                        changes++
                    }

                    override fun onTvInputInfoUpdated(inputInfo: TvInputInfo) {
                        changes++
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /**
     * The inputs the TV has, in its own order: its tuner (only the TV's own, not the channels of
     * apps) and its ports. A device plugged into a port is that port, so it is not there twice.
     * Inputs the person hid in the TV's settings are left out.
     */
    suspend fun load(): List<TvInput> = withContext(Dispatchers.Default) {
        val all = runCatching { manager?.tvInputList.orEmpty() }.getOrDefault(emptyList())
        all.filter { info ->
            info.parentId == null &&
                !runCatching { info.isHidden(appContext) }.getOrDefault(false) &&
                (info.isPassthroughInput || info.isSystemTuner())
        }.map { info ->
            val custom = runCatching { info.loadCustomLabel(appContext)?.toString() }.getOrNull()
            val label = runCatching { info.loadLabel(appContext).toString() }.getOrNull()
            TvInput(
                id = info.id,
                ownName = custom?.takeIf { it.isNotBlank() } ?: label?.takeIf { it.isNotBlank() } ?: info.id,
                kind = kindOf(info.type),
            )
        }
    }

    /**
     * Ways to have the TV show [input], best first: the first one that opens is enough. The TV's
     * own app for live TV answers them; a TV without one cannot switch from a launcher.
     */
    fun watchIntents(input: TvInput): List<Intent> {
        val intents = if (input.kind == TvInputKind.TUNER) {
            listOf(
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(input.id)),
                Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
            )
        } else {
            listOf(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))
        }
        return intents.onEach { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun TvInputInfo.isSystemTuner(): Boolean =
        type == TvInputInfo.TYPE_TUNER &&
            (serviceInfo?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

    private fun kindOf(type: Int): TvInputKind = when (type) {
        TvInputInfo.TYPE_TUNER -> TvInputKind.TUNER
        TvInputInfo.TYPE_HDMI -> TvInputKind.HDMI
        TvInputInfo.TYPE_COMPOSITE -> TvInputKind.COMPOSITE
        TvInputInfo.TYPE_COMPONENT -> TvInputKind.COMPONENT
        TvInputInfo.TYPE_SCART -> TvInputKind.SCART
        TvInputInfo.TYPE_SVIDEO -> TvInputKind.SVIDEO
        TvInputInfo.TYPE_DISPLAY_PORT -> TvInputKind.DISPLAY_PORT
        TvInputInfo.TYPE_DVI -> TvInputKind.DVI
        TvInputInfo.TYPE_VGA -> TvInputKind.VGA
        else -> TvInputKind.OTHER
    }

    companion object {
        @Volatile
        private var instance: TvInputs? = null

        fun of(context: Context): TvInputs = instance ?: synchronized(this) {
            instance ?: TvInputs(context.applicationContext).also { instance = it }
        }
    }
}
