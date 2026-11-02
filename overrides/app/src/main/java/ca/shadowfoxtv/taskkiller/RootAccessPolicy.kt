package ca.shadowfoxtv.taskkiller

internal fun isGoogleTvStreamer(manufacturer: String, model: String): Boolean =
    manufacturer.equals("Google", ignoreCase = true) && model.contains("TV Streamer", ignoreCase = true)

/** One capability probe per app process, shared by the optimizer and updater. */
internal class RootAvailabilityCache(private val probe: () -> Boolean) {
    @Volatile private var available: Boolean? = null

    fun isAvailable(): Boolean = available ?: synchronized(this) {
        available ?: probe().also { available = it }
    }
}
