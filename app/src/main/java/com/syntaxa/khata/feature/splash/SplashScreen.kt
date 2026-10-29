package com.syntaxa.khata.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syntaxa.khata.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern, simple, and fast branded Splash Screen for Syntaxa Khata.
 * Automatically transitions within ~1.1s or on tap.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = 1100L
) {
    val scaleAnim = remember { Animatable(0.78f) }
    val alphaAnim = remember { Animatable(0f) }
    val progressAnim = remember { Animatable(0f) }
    val contentAlphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(150)
            contentAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
        launch {
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis.toInt(), easing = LinearEasing)
            )
        }

        delay(durationMillis)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF041909), // Deep forest night
                        Color(0xFF0B2E13), // Kirana deep green
                        Color(0xFF14532D)  // Emerald ambient
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Instant tap-to-skip for busy shopkeepers
                onFinished()
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Main Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            // App Logo with scale & fade animation
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        alpha = alphaAnim.value
                    }
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), spotColor = Color(0xFF34D399))
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF0F3D18)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name with modern typography
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                ),
                modifier = Modifier.graphicsLayer { alpha = contentAlphaAnim.value }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Tagline in Hindi & English
            Text(
                text = stringResource(R.string.tagline),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA7F3D0),
                    fontSize = 15.sp
                ),
                modifier = Modifier.graphicsLayer { alpha = contentAlphaAnim.value }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Modern, minimalist progress indicator
            LinearProgressIndicator(
                progress = { progressAnim.value },
                modifier = Modifier
                    .width(130.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .graphicsLayer { alpha = contentAlphaAnim.value },
                color = Color(0xFF34D399),
                trackColor = Color(0xFF064E3B)
            )
        }

        // Bottom Trust & Privacy Badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .graphicsLayer { alpha = contentAlphaAnim.value }
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF064E3B).copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.splash_secure_badge),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD1FAE5)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.splash_tap_skip),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f)
                )
            )
        }
    }
}
