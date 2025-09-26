package co.qhroma.shooterlite.models

import android.graphics.Color

/**
 * Simple particle for hit effects.
 */
data class Particle(
    val pos: Vec2 = Vec2(),
    val vel: Vec2 = Vec2(),
    var radius: Float = 2f,
    var life: Float = 0.5f, // seconds
    var age: Float = 0f,
    var color: Int = Color.WHITE
)

