package jp.swingchat
import org.junit.Assert.*
import org.junit.Test
class BeatTimelineTest {
    @Test fun countInThenHeadMarkerAndFirstColumnCoincide() {
        val t=BeatTimeline(80,65,24)
        assertEquals(4,t.at(0).countdown)
        assertEquals(-1,t.at(0).column)
        assertEquals(1,t.at(t.beatNanos*4-1).countdown)
        val head=t.at(t.beatNanos*4)
        assertEquals(0,head.countdown);assertTrue(head.marker);assertEquals(0,head.column)
        assertFalse(t.at(t.beatNanos*4+70_000_000).marker)
    }
    @Test fun scansOnlyForwardAndBlacksOutForReturn() {
        val t=BeatTimeline(80,65,24);val start=t.beatNanos*4
        var previous=-1
        for(n in 0 until t.sweepNanos step 1_000_000L){val col=t.at(start+n).column;assertTrue(col>=previous);previous=col}
        assertEquals(23,t.at(start+t.sweepNanos-1).column)
        assertEquals(-1,t.at(start+t.sweepNanos).column)
        assertEquals(-1,t.at(start+t.beatNanos-1).column)
        assertEquals(0,t.at(start+t.beatNanos).column)
    }
    @Test fun longRunDoesNotAccumulateClockDrift() {
        for(bpm in listOf(40,80,160)){
            val t=BeatTimeline(bpm,65,24)
            val f=t.at(t.beatNanos*1_000_000L)
            assertTrue(f.marker);assertEquals(0,f.column)
        }
    }
}
