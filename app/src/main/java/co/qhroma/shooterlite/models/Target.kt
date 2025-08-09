package co.qhroma.shooterlite.models

/**
 * Slow moving target drifting toward the player.
 */
data class Target(
    val pos: Vec2 = Vec2(),
    val vel: Vec2 = Vec2(),
    var radius: Float = 0f,
    var alive: Boolean = true,
    var type: Int = 0
)
