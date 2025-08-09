package co.qhroma.shooterlite.models

/**
 * Fast projectile fired by the player.
 */
data class Bullet(
    val pos: Vec2 = Vec2(),
    val dir: Vec2 = Vec2(),
    var speed: Float = 0f,
    var radius: Float = 0f,
    var alive: Boolean = true
)
