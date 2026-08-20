package dev.joseph.hyperbounce.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.screens.AboutScreen
import dev.joseph.hyperbounce.ui.screens.AppDetailScreen
import dev.joseph.hyperbounce.ui.screens.EngineScreen
import dev.joseph.hyperbounce.ui.screens.HomeScreen
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceWallpaper
import dev.joseph.hyperbounce.ui.theme.hyperBounceColors

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val repository = remember { SettingsRepository(this) }
            HyperBounceApp(repository)
        }
    }
}

@Composable
private fun HyperBounceApp(repository: SettingsRepository) {
    val colors = hyperBounceColors()
    val backdrop = rememberLayerBackdrop()

    val backStack = remember { mutableStateListOf<Destination>(Destination.Home) }
    var isPush by remember { mutableStateOf(true) }
    val current = backStack.last()

    val push: (Destination) -> Unit = { destination ->
        isPush = true
        backStack.add(destination)
    }
    val pop: () -> Unit = {
        if (backStack.size > 1) {
            isPush = false
            backStack.removeAt(backStack.lastIndex)
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    CompositionLocalProvider(LocalGlassSettings provides repository.globalGlass.snapshot()) {
    Box(
        Modifier.fillMaxSize()
    ) {
        HyperBounceWallpaper(
            colors,
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        )

        AnimatedContent(
            targetState = current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forwardSpring = spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.88f,
                    stiffness = 340f
                )
                if (isPush) {
                    (slideInHorizontally(forwardSpring) { fullWidth -> fullWidth } + fadeIn())
                        .togetherWith(
                            slideOutHorizontally(forwardSpring) { fullWidth -> -fullWidth / 5 } + fadeOut()
                        )
                } else {
                    (slideInHorizontally(forwardSpring) { fullWidth -> -fullWidth / 5 } + fadeIn())
                        .togetherWith(
                            slideOutHorizontally(forwardSpring) { fullWidth -> fullWidth } + fadeOut()
                        )
                }
            },
            label = "screenTransition"
        ) { destination ->
            when (destination) {
                Destination.Home -> HomeScreen(
                    repository = repository,
                    colors = colors,
                    backdrop = backdrop,
                    onNavigate = push
                )

                is Destination.AppDetail -> AppDetailScreen(
                    app = destination.app,
                    repository = repository,
                    colors = colors,
                    backdrop = backdrop,
                    onBack = pop
                )

                Destination.Engine -> EngineScreen(
                    repository = repository,
                    colors = colors,
                    backdrop = backdrop,
                    onBack = pop
                )

                Destination.About -> AboutScreen(
                    colors = colors,
                    backdrop = backdrop,
                    onBack = pop
                )
            }
        }
    }
    }
}
