package org.example.project.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.hypot

class FloatingBubbleManager(private val context: Context, private val onClose: () -> Unit) {
    private var windowManager: WindowManager? = null

    // --- Ventana de la burbuja ---
    private var composeView: ComposeView? = null
    private var containerView: FrameLayout? = null
    private var isAdded = false
    private var bubbleParams: WindowManager.LayoutParams? = null

    // --- Ventana de la zona de cierre (fija, solo visible al arrastrar) ---
    private var closeComposeView: ComposeView? = null
    private var closeContainerView: FrameLayout? = null
    private var isCloseTargetAdded = false
    private var closeParams: WindowManager.LayoutParams? = null

    var isMicActive by mutableStateOf(false)
    private var isNearCloseTarget by mutableStateOf(false)

    private val density: Float
        get() = context.resources.displayMetrics.density

    private fun dpToPx(dp: Int): Int = (dp * density).toInt()

    private val bubbleSizePx
        get() = dpToPx(62)

    private val closeSizePx
        get() = dpToPx(64)

    // Distancia (centro a centro) por debajo de la cual consideramos "sobre la X"
    private val closeThresholdPx
        get() = dpToPx(60)

    private val lifecycleOwner =
        object : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
            private val lifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController = SavedStateRegistryController.create(this)
            private val store = ViewModelStore()

            init {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            override val lifecycle: Lifecycle
                get() = lifecycleRegistry

            override val viewModelStore: ViewModelStore
                get() = store

            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry

            fun destroy() {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                store.clear()
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isAdded) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(context)
        ) {
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        containerView = FrameLayout(context)

        val params =
            WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 80
                    y = 300
                }
        bubbleParams = params

        composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent {
                    BubbleUI(
                        isActive = isMicActive,
                        isNearCloseTarget = isNearCloseTarget,
                        onDragStart = { showCloseTarget() },
                        onDrag = { dx, dy -> onBubbleDrag(dx, dy) },
                        onDragEnd = { onBubbleDragEnd() },
                    )
                }
            }

        containerView?.addView(composeView)

        try {
            windowManager?.addView(containerView, params)
            isAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------------------------------------------------
    // Lógica de arrastre + detección de "soltar sobre la X"
    // ------------------------------------------------------------------

    private fun onBubbleDrag(dx: Float, dy: Float) {
        val lp = bubbleParams ?: return
        lp.x += dx.toInt()
        lp.y += dy.toInt()
        try {
            windowManager?.updateViewLayout(containerView, lp)
        } catch (_: Exception) {}

        updateProximityToCloseTarget()
    }

    private fun updateProximityToCloseTarget() {
        val bp = bubbleParams ?: return
        val cp = closeParams ?: return

        val bubbleCenterX = bp.x + bubbleSizePx / 2f
        val bubbleCenterY = bp.y + bubbleSizePx / 2f
        val closeCenterX = cp.x + closeSizePx / 2f
        val closeCenterY = cp.y + closeSizePx / 2f

        val distance =
            hypot(
                (bubbleCenterX - closeCenterX).toDouble(),
                (bubbleCenterY - closeCenterY).toDouble(),
            )
        isNearCloseTarget = distance < closeThresholdPx
    }

    private fun onBubbleDragEnd() {
        val shouldClose = isNearCloseTarget
        hideCloseTarget()
        if (shouldClose) {
            hide()
            onClose()
        }
    }

    // ------------------------------------------------------------------
    // Ventana fija de la zona de cierre (X)
    // ------------------------------------------------------------------

    private fun showCloseTarget() {
        if (isCloseTargetAdded) return

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        closeContainerView = FrameLayout(context)

        val params =
            WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = (screenWidth - closeSizePx) / 2
                    y = screenHeight - dpToPx(160) // un poco por encima del borde inferior
                }
        closeParams = params

        closeComposeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent { CloseTargetUI(isNear = isNearCloseTarget) }
            }

        closeContainerView?.addView(closeComposeView)

        try {
            windowManager?.addView(closeContainerView, params)
            isCloseTargetAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideCloseTarget() {
        if (!isCloseTargetAdded) return
        try {
            windowManager?.removeView(closeContainerView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isCloseTargetAdded = false
        closeComposeView = null
        closeContainerView = null
        closeParams = null
        isNearCloseTarget = false
    }

    fun hide() {
        if (!isAdded) return
        hideCloseTarget()
        try {
            windowManager?.removeView(containerView)
            lifecycleOwner.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isAdded = false
        composeView = null
        containerView = null
        bubbleParams = null
    }
}

// ------------------------------------------------------------------
// Composables
// ------------------------------------------------------------------

@Composable
fun BubbleUI(
    isActive: Boolean,
    isNearCloseTarget: Boolean,
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val neonBlue = Color(0xFF00C3FF)
    val neonGreen = Color(0xFF00FF88)
    val bubbleColor = if (isActive) neonGreen else neonBlue

    val infiniteTransition = rememberInfiniteTransition(label = "bubble_pulse")
    val pulseScale by
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isActive) 1.18f else 1.06f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = if (isActive) 500 else 2000,
                            easing = FastOutSlowInEasing,
                        ),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulseScale",
        )

    val glowAlpha by
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = if (isActive) 0.8f else 0.5f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = if (isActive) 500 else 2000,
                            easing = FastOutSlowInEasing,
                        ),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "glowAlpha",
        )

    // Cuando estamos cerca de la X, encogemos un poco la burbuja como feedback visual
    val shrinkScale by
        animateFloatAsState(
            targetValue = if (isNearCloseTarget) 0.7f else 1f,
            label = "shrinkOnNearClose",
        )

    Box(
        modifier =
            Modifier.size(62.dp).scale(pulseScale * shrinkScale).pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .clip(CircleShape)
                    .background(bubbleColor.copy(alpha = glowAlpha * 0.4f))
        )
        Box(
            modifier =
                Modifier.size(50.dp)
                    .clip(CircleShape)
                    .background(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        bubbleColor.copy(alpha = 0.95f),
                                        bubbleColor.copy(alpha = 0.65f),
                                    )
                            )
                    )
                    .border(1.5.dp, bubbleColor.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isActive) "🎙" else "●",
                fontSize = if (isActive) 20.sp else 16.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
fun CloseTargetUI(isNear: Boolean) {
    val scale by
        animateFloatAsState(targetValue = if (isNear) 1.3f else 1f, label = "closeTargetScale")
    val bgColor = if (isNear) Color(0xFFFF3B3B) else Color(0xFFFF3B3B).copy(alpha = 0.6f)

    Box(
        modifier =
            Modifier.size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color.White, fontSize = 20.sp)
    }
}
