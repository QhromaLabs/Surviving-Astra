package co.qhroma.shooterlite.models

/** Simple 2D vector with basic operations. */
data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float) { x = nx; y = ny }
    fun add(other: Vec2) { x += other.x; y += other.y }
    fun sub(other: Vec2) { x -= other.x; y -= other.y }
    fun mul(s: Float) { x *= s; y *= s }
    fun len(): Float = kotlin.math.sqrt(x * x + y * y)
    fun normalize() {
        val l = len()
        if (l != 0f) { x /= l; y /= l }
    }
    fun copy() = Vec2(x, y)
    companion object {
        fun from(other: Vec2) = Vec2(other.x, other.y)
    }
}
