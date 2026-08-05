package com.feraxhp.gallery.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Representa una pequeña pieza (cuadrado) de la imagen que vuela.
 */
private data class Particle(
    val initialX: Float,
    val initialY: Float,
    val width: Float,
    val height: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotationSpeed: Float,
    val delay: Float,
    val srcOffset: IntOffset,
    val srcSize: IntSize
)

@Composable
fun ShatterEffect(
    bitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    onAnimationEnd: () -> Unit = {}
) {
    val particleCountPerSide = 30
    val progress = remember { Animatable(0f) }
    
    // Necesitamos saber el tamaño del Canvas para escalar las partículas
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    LaunchedEffect(bitmap) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2500, easing = LinearEasing)
        )
        onAnimationEnd()
    }

    val particles = remember(bitmap, canvasSize) {
        if (canvasSize == androidx.compose.ui.geometry.Size.Zero) return@remember emptyList<Particle>()
        
        val list = mutableListOf<Particle>()
        val pSizeW = canvasSize.width / particleCountPerSide
        val pSizeH = canvasSize.height / particleCountPerSide
        
        val bmpScaleX = bitmap.width.toFloat() / canvasSize.width
        val bmpScaleY = bitmap.height.toFloat() / canvasSize.height

        for (i in 0 until particleCountPerSide) {
            for (j in 0 until particleCountPerSide) {
                val angle = Random.nextFloat() * 2 * kotlin.math.PI
                val speed = Random.nextFloat() * 300f + 100f
                
                list.add(
                    Particle(
                        initialX = i * pSizeW,
                        initialY = j * pSizeH,
                        width = pSizeW,
                        height = pSizeH,
                        velocityX = (cos(angle) * speed).toFloat(),
                        velocityY = (sin(angle) * speed).toFloat() - 300f, // Explosión hacia arriba reducida
                        rotationSpeed = Random.nextFloat() * 1080f - 540f,
                        delay = Random.nextFloat() * 0.05f,
                        srcOffset = IntOffset((i * pSizeW * bmpScaleX).toInt(), (j * pSizeH * bmpScaleY).toInt()),
                        srcSize = IntSize((pSizeW * bmpScaleX).toInt(), (pSizeH * bmpScaleY).toInt())
                    )
                )
            }
        }
        list
    }

    // Pre-calculamos el path de redondeo para todas las partículas (tienen el mismo tamaño)
    val roundPath = remember(canvasSize) {
        if (canvasSize == androidx.compose.ui.geometry.Size.Zero) return@remember Path()
        val pSizeW = canvasSize.width / particleCountPerSide
        val pSizeH = canvasSize.height / particleCountPerSide
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = pSizeW,
                    bottom = pSizeH,
                    cornerRadius = CornerRadius(pSizeW * 0.4f) // Bordes muy redondeados
                )
            )
        }
    }

    Canvas(modifier = modifier.onGloballyPositioned { canvasSize = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()) }) {
        if (particles.isEmpty()) return@Canvas
        val t = progress.value
        
        particles.forEach { p ->
            val et = (t - p.delay).coerceIn(0f, 1f)
            if (et <= 0f) return@forEach

            val x = p.initialX + p.velocityX * et
            // Gravedad aumentada significativamente para que salgan de la pantalla
            val y = p.initialY + p.velocityY * et + 0.5f * 4000f * et * et
            val rotation = p.rotationSpeed * et
            val alpha = (1f - et * et).coerceIn(0f, 1f) // Desvanecimiento más lento al principio

            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(x + p.width / 2, y + p.height / 2)
                canvas.rotate(rotation)
                canvas.translate(-p.width / 2, -p.height / 2)
                
                // Aplicamos el redondeo usando clipPath
                clipPath(roundPath) {
                    drawImage(
                        image = bitmap,
                        srcOffset = p.srcOffset,
                        srcSize = p.srcSize,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(p.width.toInt(), p.height.toInt()),
                        alpha = alpha
                    )
                }
                
                canvas.restore()
            }
        }
    }
}
