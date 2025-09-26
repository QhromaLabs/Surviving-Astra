package co.qhroma.shooterlite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import co.qhroma.shooterlite.models.Bullet
import co.qhroma.shooterlite.models.Target
import co.qhroma.shooterlite.models.Particle
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
    private val paintBitmap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private var state = GameState.MENU

    // Entities
    private val bullets = mutableListOf<Bullet>()
    private val targets = mutableListOf<Target>()
    private val particles = mutableListOf<Particle>()

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

    // Sprites
    private var playerBmp: Bitmap? = null
    private var bulletBmp: Bitmap? = null
    private var backgroundSrc: Bitmap? = null
    private var backgroundSrcRect: Rect? = null
    private var backgroundDstRect: Rect? = null
    private val asteroidSrc = mutableListOf<Bitmap>()
    private val asteroidCache = HashMap<Pair<Int, Int>, Bitmap>()

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
        initGraphics(width, height)
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
            t.angle += t.spin * dt
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
        val itB = bullets.iterator()
        while (itB.hasNext()) {
            val b = itB.next()
            val itT = targets.iterator()
            while (itT.hasNext()) {
                val t = itT.next()
                val dx = b.pos.x - t.pos.x
                val dy = b.pos.y - t.pos.y
                val rr = b.radius + t.radius
                if (dx * dx + dy * dy <= rr * rr) {
                    itB.remove()
                    itT.remove()
                    score += 1
                    audio.playPop()
                    haptics.buzz()
                    spawnExplosion(t.pos)
                    break
                }
            }
        }
        // Update particles
        val itP = particles.iterator()
        while (itP.hasNext()) {
            val p = itP.next()
            p.age += dt
            p.pos.add(Vec2(p.vel.x * dt, p.vel.y * dt))
            if (p.age >= p.life) itP.remove()
        }
    }

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#121212"))
        val bg = backgroundSrc
        val sRect = backgroundSrcRect
        val dRect = backgroundDstRect
        if (bg != null && sRect != null && dRect != null) {
            canvas.drawBitmap(bg, sRect, dRect, paintBitmap)
        }

        // Draw player
        playerBmp?.let {
            val x = playerPos.x - it.width / 2f
            val y = playerPos.y - it.height / 2f
            canvas.drawBitmap(it, x, y, paintBitmap)
        } ?: run {
            canvas.drawCircle(playerPos.x, playerPos.y, playerRadius, paintPlayer)
        }

        // Draw bullets
        for (b in bullets) {
            val bmp = bulletBmp
            if (bmp != null) {
                val x = b.pos.x - bmp.width / 2f
                val y = b.pos.y - bmp.height / 2f
                canvas.drawBitmap(bmp, x, y, paintBitmap)
            } else {
                canvas.drawCircle(b.pos.x, b.pos.y, b.radius, paintBullet)
            }
        }
        // Draw targets with rotation if available
        for (t in targets) {
            val bmp = t.bmp
            if (bmp != null) {
                canvas.save()
                canvas.rotate(t.angle, t.pos.x, t.pos.y)
                val x = t.pos.x - bmp.width / 2f
                val y = t.pos.y - bmp.height / 2f
                canvas.drawBitmap(bmp, x, y, paintBitmap)
                canvas.restore()
            } else {
                canvas.drawCircle(t.pos.x, t.pos.y, t.radius, paintTarget)
            }
        }

        // Draw particles last (alpha fades)
        for (p in particles) {
            val t = (p.age / p.life).coerceIn(0f, 1f)
            val alpha = ((1f - t) * 255f).toInt().coerceIn(0, 255)
            paintBullet.color = p.color
            paintBullet.alpha = alpha
            val pr = p.radius * (1f - 0.3f * t)
            canvas.drawCircle(p.pos.x, p.pos.y, pr, paintBullet)
        }
        // Reset paint alpha
        paintBullet.alpha = 255

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
        val spriteIndex = if (asteroidSrc.isNotEmpty()) Random.nextInt(asteroidSrc.size) else 0
        val sizePx = (r * 2f).toInt().coerceAtLeast(8)
        val bmp = if (asteroidSrc.isNotEmpty()) getAsteroidScaled(spriteIndex, sizePx) else null
        val spin = (Random.nextFloat() * 120f) - 60f // -60..+60 deg/sec
        val t = Target(pos, vel, r, true, spriteIndex, 0f, spin, bmp)
        targets.add(t)
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
                    val br = bulletBmp?.let { it.width / 2f } ?: dp(10f)
                    bullets.add(Bullet(Vec2(playerPos.x, playerPos.y), dir, 900f, br))
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
        particles.clear()
        score = 0
        spawnTimer = 0f
        spawnInterval = 1.2f
        state = GameState.RUNNING
    }

    private fun initGraphics(w: Int, h: Int) {
        if (playerBmp == null) playerBmp = decodeAndScaleDrawable(R.drawable.player, dp(56f))
        if (bulletBmp == null) bulletBmp = decodeAndScaleDrawable(R.drawable.bullet, dp(20f))
        // Load asteroid sources once (unscaled)
        if (asteroidSrc.isEmpty()) {
            val ids = listOf(
                R.drawable.asteroid1, R.drawable.asteroid2, R.drawable.asteroid3, R.drawable.asteroid4,
                R.drawable.asteroid5, R.drawable.asteroid6, R.drawable.asteroid7, R.drawable.asteroid8
            )
            for (id in ids) {
                decodeDrawable(id)?.let { asteroidSrc.add(it) }
            }
        }
        // Background with center-crop (preserve aspect ratio)
        decodeDrawable(R.drawable.background)?.let { bg ->
            backgroundSrc = bg
            backgroundSrcRect = computeCenterCropSrcRect(bg.width, bg.height, w, h)
            backgroundDstRect = Rect(0, 0, w, h)
        }
    }

    private fun decodeDrawable(id: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            BitmapFactory.decodeResource(resources, id, opts)
        } catch (_: Exception) { null }
    }

    private fun decodeAndScaleDrawable(id: Int, sizePx: Float): Bitmap? {
        val base = decodeDrawable(id) ?: return null
        val s = sizePx.toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(base, s, s, true)
    }

    private fun computeCenterCropSrcRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val viewAspect = dstW.toFloat() / dstH.toFloat()
        val imgAspect = srcW.toFloat() / srcH.toFloat()
        return if (imgAspect > viewAspect) {
            // Image is wider than view: crop width
            val newW = (srcH * viewAspect).toInt()
            val left = ((srcW - newW) / 2f).toInt()
            Rect(left, 0, left + newW, srcH)
        } else {
            // Image is taller than view: crop height
            val newH = (srcW / viewAspect).toInt()
            val top = ((srcH - newH) / 2f).toInt()
            Rect(0, top, srcW, top + newH)
        }
    }

    private fun getAsteroidScaled(index: Int, sizePx: Int): Bitmap {
        val key = Pair(index, sizePx)
        asteroidCache[key]?.let { return it }
        val src = asteroidSrc[index]
        val scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
        asteroidCache[key] = scaled
        return scaled
    }

    private fun spawnExplosion(at: Vec2) {
        val count = 14
        val color = Color.MAGENTA
        for (i in 0 until count) {
            val ang = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = 80f + Random.nextFloat() * 220f
            val vel = Vec2(cos(ang) * speed, sin(ang) * speed)
            val r = dp(2f + Random.nextFloat() * 3f)
            val life = 0.35f + Random.nextFloat() * 0.3f
            particles.add(Particle(Vec2(at.x, at.y), vel, r, life, 0f, color))
        }
    }
}
