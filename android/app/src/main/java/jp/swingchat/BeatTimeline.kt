package jp.swingchat

/** Monotonic clock; no timers, sensor input, or cumulative beat drift. */
class BeatTimeline(val bpm: Int, val sweepPercent: Int, val columns: Int) {
    init { require(bpm in 40..160); require(sweepPercent in 35..80); require(columns in 8..64) }
    val beatNanos = 60_000_000_000L / bpm
    val sweepNanos = beatNanos * sweepPercent / 100
    data class Frame(val countdown: Int, val marker: Boolean, val column: Int)
    fun at(elapsedNanos: Long): Frame {
        val elapsed = elapsedNanos.coerceAtLeast(0)
        val beat = elapsed / beatNanos
        val phase = elapsed % beatNanos
        return Frame(
            if (beat < 4) 4 - beat.toInt() else 0,
            phase < 70_000_000L,
            if (beat < 4 || phase >= sweepNanos) -1 else (phase * columns / sweepNanos).toInt().coerceAtMost(columns - 1)
        )
    }
}
