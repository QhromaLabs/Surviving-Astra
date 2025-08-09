package co.qhroma.shooterlite

import android.graphics.Canvas

/**
 * Thread running the fixed-timestep game loop at ~60 FPS.
 */
class GameThread(private val view: GameView) : Thread() {
    @Volatile var running = false
    private val dt = 1f / 60f

    override fun run() {
        var previous = System.nanoTime()
        var lag = 0.0
        while (running) {
            val current = System.nanoTime()
            val elapsed = (current - previous) / 1_000_000_000.0
            previous = current
            lag += elapsed

            while (lag >= dt) {
                view.update(dt)
                lag -= dt
            }

            var canvas: Canvas? = null
            try {
                canvas = view.holder.lockCanvas()
                if (canvas != null) view.render(canvas)
            } finally {
                if (canvas != null) view.holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
