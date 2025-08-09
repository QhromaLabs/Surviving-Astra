package co.qhroma.shooterlite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import co.qhroma.shooterlite.models.Bullet
import co.qhroma.shooterlite.models.Target
import co.qhroma.shooterlite.models.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * GameView draws and updates the game. 
 * Controls: single tap to shoot, two-finger tap to pause. 
 * Change spawn rates and speeds inside spawnTarget() and constants below.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val thread = GameThread(this)
    private val audio = AudioManager(context)
    private val haptics = Haptics(context)
    private val scores = HighScoreStore(context)

    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaled

    // Paints
    private val paintPlayer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val paintBullet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
    }
    private val paintTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(20f)
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }

    private var state = GameState.MENU

    // Entities
    private val bullets = mutableListOf<Bullet>()
    private val targets = mutableListOf<Target>()

    // Player
    private var playerPos = Vec2()
    private val playerRadius = dp(28f)

    // Spawn & difficulty
    private var spawnTimer = 0f
    private var spawnInterval = 1.2f
    private val spawnIntervalMin = 0.5f

    // Score
    private var score = 0
    private var bestScore = scores.getBest()

    // Cooldown
    private var shootCooldown = 0f
    private val shootDelay = 0.18f

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread.running = true
        thread.start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread.running = false
        try { thread.join() } catch (_: InterruptedException) {}
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        playerPos.set(width / 2f, height * 0.7f)
    }

    fun update(dt: Float) {
        if (state != GameState.RUNNING) return

        shootCooldown -= dt
        spawnTimer += dt
        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0f
            spawnTarget()
            spawnInterval = maxOf(spawnIntervalMin, spawnInterval * 0.98f)
        }

        // Update bullets
        val bw = width + 50f
        val bh = height + 50f
        val itB = bullets.iterator()
        while (itB.hasNext()) {
            val b = itB.next()
            b.pos.add(Vec2(b.dir.x * b.speed * dt, b.dir.y * b.speed * dt))
            if (b.pos.x < -50 || b.pos.x > bw || b.pos.y < -50 || b.pos.y > bh) {
                itB.remove()
            }
        }

        // Update targets
        val itT = targets.iterator()
        while (itT.hasNext()) {
            val t = itT.next()
            t.pos.add(Vec2(t.vel.x * dt, t.vel.y * dt))
            // Wrap
            if (t.pos.x < -t.radius) t.pos.x = width + t.radius
            if (t.pos.x > width + t.radius) t.pos.x = -t.radius
            if (t.pos.y < -t.radius) t.pos.y = height + t.radius
            if (t.pos.y > height + t.radius) t.pos.y = -t.radius

            // Collision with player
            val dxp = t.pos.x - playerPos.x
            val dyp = t.pos.y - playerPos.y
            if (dxp * dxp + dyp * dyp <= (t.radius + playerRadius) * (t.radius + playerRadius)) {
                state = GameState.GAME_OVER
                bestScore = scores.submit(score)
                break
            }
        }

        // Bullet vs target collisions
        val remB = mutableListOf<Bullet>()
        val remT = mutableListOf<Target>()
        for (b in bullets) {
            for (t in targets) {
                val dx = b.pos.x - t.pos.x
                val dy = b.pos.y - t.pos.y
                val rr = b.radius + t.radius
                if (dx * dx + dy * dy <= rr * rr) {
                    remB.add(b)
                    remT.add(t)
                    score += 1
                    audio.playPop()
                    haptics.buzz()
                    break
                }
            }
        }
        bullets.removeAll(remB)
        targets.removeAll(remT)
    }

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#121212"))

        // Draw player
        canvas.drawCircle(playerPos.x, playerPos.y, playerRadius, paintPlayer)

        // Draw bullets and targets
        for (b in bullets) canvas.drawCircle(b.pos.x, b.pos.y, b.radius, paintBullet)
        for (t in targets) canvas.drawCircle(t.pos.x, t.pos.y, t.radius, paintTarget)

        // HUD
        canvas.drawText("Score: $score", 20f, 40f, paintText)
        canvas.drawText("Tap to shoot", 20f, 70f, paintText)

        when (state) {
            GameState.MENU -> {
                drawCenteredText(canvas, resources.getString(R.string.title), height / 2f - 80f)
                val playY = height / 2f
                drawCenteredText(canvas, resources.getString(R.string.play), playY)
            }
            GameState.PAUSED -> {
                canvas.drawARGB(160, 0, 0, 0)
                drawCenteredText(canvas, resources.getString(R.string.paused), height / 2f)
            }
            GameState.GAME_OVER -> {
                canvas.drawARGB(160, 0, 0, 0)
                drawCenteredText(canvas, resources.getString(R.string.game_over), height / 2f - 60f)
                drawCenteredText(canvas, resources.getString(R.string.score) + " $score", height / 2f - 20f)
                drawCenteredText(canvas, resources.getString(R.string.best) + " $bestScore", height / 2f + 20f)
                drawCenteredText(canvas, resources.getString(R.string.play_again), height / 2f + 80f)
            }
            else -> {}
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, y: Float) {
        val x = width / 2f - paintText.measureText(text) / 2f
        canvas.drawText(text, x, y, paintText)
    }

    private fun spawnTarget() {
        val side = Random.nextInt(4)
        val r = dp(Random.nextFloat() * 24f + 28f) // 28-52dp
        val pos = Vec2()
        when (side) {
            0 -> { pos.set(Random.nextFloat() * width, -r) }
            1 -> { pos.set(Random.nextFloat() * width, height + r) }
            2 -> { pos.set(-r, Random.nextFloat() * height) }
            else -> { pos.set(width + r, Random.nextFloat() * height) }
        }
        var angle = atan2(playerPos.y - pos.y, playerPos.x - pos.x)
        angle += Random.nextFloat() * 0.6f - 0.3f
        val speed = 120f
        val vel = Vec2(cos(angle) * speed, sin(angle) * speed)
        targets.add(Target(pos, vel, r))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (state) {
            GameState.MENU -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startGame()
                }
            }
            GameState.RUNNING -> {
                if (event.pointerCount >= 2) {
                    state = GameState.PAUSED
                } else if (event.action == MotionEvent.ACTION_DOWN && shootCooldown <= 0f) {
                    shootCooldown = shootDelay
                    val dir = Vec2(event.x - playerPos.x, event.y - playerPos.y)
                    dir.normalize()
                    bullets.add(Bullet(Vec2(playerPos.x, playerPos.y), dir, 900f, dp(10f)))
                    audio.playShoot()
                }
            }
            GameState.PAUSED -> {
                if (event.action == MotionEvent.ACTION_DOWN) state = GameState.RUNNING
            }
            GameState.GAME_OVER -> {
                if (event.action == MotionEvent.ACTION_DOWN) startGame()
            }
        }
        return true
    }
    fun pauseFromActivity() { if (state == GameState.RUNNING) state = GameState.PAUSED }

    private fun startGame() {
        bullets.clear()
        targets.clear()
        score = 0
        spawnTimer = 0f
        spawnInterval = 1.2f
        state = GameState.RUNNING
    }
}
