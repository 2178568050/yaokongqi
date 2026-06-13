package com.yaokongqi.remote.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private const val DURATION_MS = 280

fun AnimatedContentTransitionScope<MainRoute>.padTransition(): ContentTransform =
    fadeIn(tween(DURATION_MS)) + scaleIn(initialScale = 0.96f, animationSpec = tween(DURATION_MS)) togetherWith
        fadeOut(tween(DURATION_MS)) + scaleOut(targetScale = 1.02f, animationSpec = tween(DURATION_MS))

fun AnimatedContentTransitionScope<MainRoute>.forwardTransition(): ContentTransform {
    val enter = slideInHorizontally(tween(DURATION_MS)) { it / 3 } + fadeIn(tween(DURATION_MS))
    val exit = slideOutHorizontally(tween(DURATION_MS)) { -it / 4 } + fadeOut(tween(DURATION_MS))
    return enter togetherWith exit
}

fun AnimatedContentTransitionScope<MainRoute>.backwardTransition(): ContentTransform {
    val enter = slideInHorizontally(tween(DURATION_MS)) { -it / 3 } + fadeIn(tween(DURATION_MS))
    val exit = slideOutHorizontally(tween(DURATION_MS)) { it / 4 } + fadeOut(tween(DURATION_MS))
    return enter togetherWith exit
}

fun AnimatedContentTransitionScope<MainRoute>.minimalEnterTransition(): ContentTransform =
    fadeIn(tween(DURATION_MS)) + scaleIn(initialScale = 1.08f, animationSpec = tween(DURATION_MS)) togetherWith
        fadeOut(tween(DURATION_MS)) + scaleOut(targetScale = 0.92f, animationSpec = tween(DURATION_MS))

fun AnimatedContentTransitionScope<MainRoute>.minimalExitTransition(): ContentTransform =
    fadeIn(tween(DURATION_MS)) + scaleIn(initialScale = 0.92f, animationSpec = tween(DURATION_MS)) togetherWith
        fadeOut(tween(DURATION_MS)) + scaleOut(targetScale = 1.08f, animationSpec = tween(DURATION_MS))

enum class MainRoute {
    Pad,
    Settings,
    MinimalScroll,
}
