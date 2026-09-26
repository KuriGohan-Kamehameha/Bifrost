package com.moonbench.bifrost.capture

/** Owns the single virtual display permitted per Android 14+ capture session. */
class CaptureDisplay<S : Any, D : Any>(
    private val create: (S, Int, Int, Int) -> D,
    private val update: (D, S, Int, Int, Int) -> Unit,
    private val detach: (D) -> Unit,
    private val release: (D) -> Unit
) {
    private var display: D? = null
    private var surface: S? = null
    private var creationAttempted = false
    private var closed = false

    @Synchronized fun attach(next: S, width: Int, height: Int, density: Int) {
        check(!closed) { "Capture session has ended" }
        require(width > 0 && height > 0 && density > 0)
        val existing = display
        if (existing == null) {
            check(!creationAttempted) { "Capture consent has already been consumed" }
            creationAttempted = true
            display = create(next, width, height, density)
        } else {
            update(existing, next, width, height, density)
        }
        surface = next
    }

    @Synchronized fun detach(previous: S) {
        if (surface !== previous) return
        surface = null
        display?.let(detach)
    }

    @Synchronized fun close() {
        if (closed) return
        closed = true
        surface = null
        val previous = display
        display = null
        previous?.let(release)
    }
}
