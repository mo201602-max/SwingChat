package jp.swingchat

import kotlin.math.*

/** Twist relative to the captured device frame. Quaternion order is w,x,y,z. */
class RelativeAngleTracker(private val axis: Int) {
    init { require(axis in 1..3) }
    private var center: DoubleArray? = null
    fun recenter() { center = null }
    fun sample(w: Double, x: Double, y: Double, z: Double): Double? {
        val values=doubleArrayOf(w,x,y,z)
        if(values.any { !it.isFinite() }) return null
        val norm=sqrt(values.sumOf { it*it })
        if(norm<1e-8) return null
        val q=DoubleArray(4) { values[it]/norm }
        if(center==null) { center=q;return 0.0 }
        val c=center!!
        // inverse(center) * current; project onto the selected local axis.
        val rw=c[0]*q[0]+c[1]*q[1]+c[2]*q[2]+c[3]*q[3]
        val component=when(axis) {
            1 -> c[0]*q[1]-c[1]*q[0]-c[2]*q[3]+c[3]*q[2]
            2 -> c[0]*q[2]+c[1]*q[3]-c[2]*q[0]-c[3]*q[1]
            else -> c[0]*q[3]-c[1]*q[2]+c[2]*q[1]-c[3]*q[0]
        }
        if(hypot(rw,component)<1e-8) return null
        return wrapDegrees(Math.toDegrees(2*atan2(component,rw)))
    }
}

internal fun wrapDegrees(value: Double): Double = ((value+180)%360+360)%360-180

/** Both travel directions address the same angle bins; no timing assumption. */
class AngleSweep(val spanDegrees: Int, val columns: Int, private val reversed: Boolean=false) {
    init { require(spanDegrees in 30..160); require(columns in 8..64) }
    private var previousAngle: Double?=null
    private var previousTime=0L
    private var lastMotion=Long.MIN_VALUE
    private var valid=false
    var angle=0.0;private set
    fun recenter() { previousAngle=null;previousTime=0;lastMotion=Long.MIN_VALUE;valid=false;angle=0.0 }
    fun sample(degrees: Double, timestamp: Long) {
        if(!degrees.isFinite()){valid=false;return}
        if(previousAngle!=null && timestamp<=previousTime) return
        val previous=previousAngle
        if(previous!=null && timestamp-previousTime<=150_000_000L && abs(wrapDegrees(degrees-previous))>=0.08) lastMotion=timestamp
        else if(previous==null || timestamp-previousTime>150_000_000L) lastMotion=Long.MIN_VALUE
        angle=degrees;previousAngle=degrees;previousTime=timestamp;valid=true
    }
    fun column(now: Long): Int {
        if(!valid || now<previousTime || now-previousTime>150_000_000L || lastMotion==Long.MIN_VALUE || now-lastMotion>100_000_000L) return -1
        val half=spanDegrees/2.0
        if(angle < -half || angle > half) return -1
        val col=floor((angle+half)/spanDegrees*columns).toInt().coerceIn(0,columns-1)
        return if(reversed) columns-1-col else col
    }
}
