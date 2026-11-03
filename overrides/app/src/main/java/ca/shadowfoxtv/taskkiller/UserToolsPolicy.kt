package ca.shadowfoxtv.taskkiller

import kotlin.math.abs

data class StabilityResult(
    val attempts: Int,
    val failed: Int,
    val medianMs: Int?,
    val variationMs: Int?,
    val maximumMs: Int?,
    val networkChanged: Boolean,
    val verdict: String
)

internal object StabilityAnalysis {
    fun summarize(samples: List<Int>, networkChanged: Boolean): StabilityResult {
        val successful = samples.filter { it >= 0 }.sorted()
        val differences = samples.zipWithNext().filter { it.first >= 0 && it.second >= 0 }
            .map { abs(it.first - it.second) }
        val median = if (successful.isEmpty()) null else {
            val middle = successful.size / 2
            if (successful.size % 2 == 0) ((successful[middle - 1].toLong() + successful[middle]) / 2).toInt()
            else successful[middle]
        }
        val variation = differences.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val failed = samples.size - successful.size
        val verdict = when {
            samples.isEmpty() -> "NOT TESTED"
            networkChanged -> "NETWORK CHANGED"
            successful.isEmpty() -> "TEST ENDPOINT UNREACHABLE"
            failed > 0 -> "CONNECTION INTERRUPTIONS"
            (variation ?: 0) > 40 -> "VARIABLE RESPONSE TIMES"
            median != null && median > 150 -> "SLOW RESPONSE TIMES"
            else -> "CONSISTENT RESPONSE TIMES"
        }
        return StabilityResult(samples.size, failed, median, variation, successful.lastOrNull(), networkChanged, verdict)
    }
}

internal object HealthAlertPolicy {
    const val REPEAT_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    fun lowStorage(free: Long, threshold: Long, previouslyLow: Boolean): Boolean =
        free >= 0 && threshold > 0 && free < threshold + if (previouslyLow) threshold / 10 else 0

    fun hot(thermalStatus: Int?, previouslyHot: Boolean): Boolean =
        thermalStatus != null && (thermalStatus >= 3 || (previouslyHot && thermalStatus >= 2))

    fun notify(active: Boolean, previous: Boolean, now: Long, lastSent: Long): Boolean =
        active && !previous && (lastSent == 0L || now < lastSent || now - lastSent >= REPEAT_COOLDOWN_MS)
}

internal object SupportRedaction {
    fun clean(value: String): String = value
        .replace(Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"), "[email removed]")
        .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "[address removed]")
        .replace(Regex("/data/[^\\s]+"), "[private path removed]")
        .take(12000)
}
