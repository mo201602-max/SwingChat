package jp.swingchat

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class AngleSweepTest {
    private fun q(degrees: Double, axis: Int=2): DoubleArray {val half=Math.toRadians(degrees)/2;return DoubleArray(4){if(it==0)cos(half) else if(it==axis)sin(half) else 0.0} }
    @Test fun capturesStartingOrientationAndSupportsBothAxes() {
        for(axis in 1..3){val tracker=RelativeAngleTracker(axis)
            val center=q(45.0,axis);assertEquals(0.0,tracker.sample(center[0],center[1],center[2],center[3])!!,1e-8)
            val rotated=q(75.0,axis);assertEquals(30.0,tracker.sample(rotated[0],rotated[1],rotated[2],rotated[3])!!,1e-8)
        }
    }
    @Test fun quaternionSignAndWrapDoNotChangeTheAngle() {
        val tracker=RelativeAngleTracker(2);val c=q(170.0);tracker.sample(c[0],c[1],c[2],c[3])
        val r=q(-170.0);assertEquals(20.0,tracker.sample(r[0],r[1],r[2],r[3])!!,1e-8)
        assertEquals(20.0,tracker.sample(-r[0],-r[1],-r[2],-r[3])!!,1e-8)
        assertNull(tracker.sample(Double.NaN,0.0,0.0,0.0));assertNull(tracker.sample(0.0,0.0,0.0,0.0))
    }
    @Test fun capturesAnewAfterRecenter() {
        val tracker=RelativeAngleTracker(1);tracker.sample(1.0,0.0,0.0,0.0)
        tracker.recenter();val r=q(50.0,1);assertEquals(0.0,tracker.sample(r[0],r[1],r[2],r[3])!!,1e-8)
    }
    @Test fun measuresInCapturedDeviceFrameEvenWhenInitiallyTilted() {
        // center is a 90-degree rotation about z; center * local-y(30 degrees).
        val tracker=RelativeAngleTracker(2);val c=sqrt(.5);tracker.sample(c,0.0,0.0,c)
        val h=Math.toRadians(15.0)
        assertEquals(30.0,tracker.sample(c*cos(h),-c*sin(h),c*sin(h),c*cos(h))!!,1e-8)
    }
    @Test fun bothAxesRecenterAndQuaternionSignAreStable() {
        for(axis in 1..3){
            val tracker=RelativeAngleTracker(axis)
            fun sample(d:Double,sign:Double=1.0):Double? {val v=q(d,axis);return tracker.sample(v[0]*sign,v[1]*sign,v[2]*sign,v[3]*sign)}
            assertNull(tracker.sample(0.0,0.0,0.0,0.0))
            assertEquals(0.0,sample(170.0)!!,1e-6)
            assertEquals(20.0,sample(-170.0)!!,1e-6)
            assertEquals(20.0,sample(-170.0,-1.0)!!,1e-6)
            tracker.recenter()
            assertEquals(0.0,sample(-170.0)!!,1e-6)
            assertEquals(-20.0,sample(170.0)!!,1e-6)
        }
    }
    @Test fun horizontalRollRespondsToScreenNormalAndIgnoresYaw() {
        val roll=RelativeAngleTracker(3)
        assertEquals(0.0,roll.sample(1.0,0.0,0.0,0.0)!!,1e-6)
        val yaw=q(45.0,2)
        assertEquals(0.0,roll.sample(yaw[0],yaw[1],yaw[2],yaw[3])!!,1e-6)
        for(degrees in listOf(-60.0,-30.0,0.0,30.0,60.0,30.0,0.0,-30.0,-60.0)) {
            val v=q(degrees,3)
            assertEquals(degrees,roll.sample(v[0],v[1],v[2],v[3])!!,1e-6)
        }
    }
    @Test fun sameAngleGivesSameColumnOnForwardAndReturnPasses() {
        val sweep=AngleSweep(120,16);var time=1_000_000_000L
        val forward=mutableMapOf<Int,Int>()
        for(angle in -65..65){time+=10_000_000;sweep.sample(angle.toDouble(),time);forward[angle]=sweep.column(time)}
        for(angle in 65 downTo -65){time+=10_000_000;sweep.sample(angle.toDouble(),time);assertEquals(forward[angle],sweep.column(time))}
        assertEquals(-1,forward[-61]);assertEquals(0,forward[-60]);assertEquals(15,forward[60]);assertEquals(-1,forward[61])
    }
    @Test fun stopsAndStaleSamplesBlackOutAndResumeSafely() {
        val s=AngleSweep(120,16);val t=1_000_000_000L
        s.sample(0.0,t);assertEquals(-1,s.column(t))
        s.sample(1.0,t+10_000_000);assertTrue(s.column(t+10_000_000)>=0)
        s.sample(1.0,t+120_000_000);assertEquals(-1,s.column(t+120_000_000))
        s.sample(2.0,t+130_000_000);assertTrue(s.column(t+130_000_000)>=0)
        assertEquals(-1,s.column(t+300_000_000))
        s.sample(3.0,t+500_000_000);assertEquals(-1,s.column(t+500_000_000))
        s.sample(4.0,t+510_000_000);assertTrue(s.column(t+510_000_000)>=0)
        s.recenter();assertEquals(-1,s.column(t+510_000_000))
    }
    @Test fun reverseOutOfOrderAndInvalidSamplesAreHandled() {
        val s=AngleSweep(120,16,true);val t=1_000_000_000L
        s.sample(-61.0,t);s.sample(-60.0,t+10_000_000);assertEquals(15,s.column(t+10_000_000))
        s.sample(60.0,t+5_000_000);assertEquals(15,s.column(t+10_000_000))
        s.sample(Double.NaN,t+20_000_000);assertEquals(-1,s.column(t+20_000_000))
    }
}
