@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.valid7996.narcicgeteway.ui.home

import org.jetbrains.compose.resources.DrawableResource

import org.jetbrains.compose.resources.StringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.valid7996.narcicgeteway.ui.components.CyberRadarIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.mutableFloatStateOf
import dev.valid7996.narcicgeteway.ui.theme.MotionBudget
import dev.valid7996.narcicgeteway.ui.skin.LocalSkinBackdrop
import dev.valid7996.narcicgeteway.ui.skin.LocalSkinHero
import dev.valid7996.narcicgeteway.ui.theme.LocalMotionBudget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.domain.model.ConnectButtonStyle
import dev.valid7996.narcicgeteway.domain.model.ConnectionState
import dev.valid7996.narcicgeteway.ui.components.MorphingBlob
import dev.valid7996.narcicgeteway.ui.components.NoteText
import dev.valid7996.narcicgeteway.data.net.NetworkInfo
import dev.valid7996.narcicgeteway.ui.components.FlagBadge
import dev.valid7996.narcicgeteway.ui.connection.ConnectionViewModel
import dev.valid7996.narcicgeteway.ui.format.formatBytes
import dev.valid7996.narcicgeteway.ui.format.formatElapsed
import dev.valid7996.narcicgeteway.ui.format.formatRate
import dev.valid7996.narcicgeteway.ui.theme.readableOn
import dev.valid7996.narcicgeteway.domain.model.TrafficCardStyle
import dev.valid7996.narcicgeteway.domain.model.TrafficTileSize
import dev.valid7996.narcicgeteway.ui.theme.Personalization
import dev.valid7996.narcicgeteway.ui.theme.NrgGradients
import dev.valid7996.narcicgeteway.ui.theme.NrgCyan
import dev.valid7996.narcicgeteway.ui.theme.NrgHotPink
import dev.valid7996.narcicgeteway.ui.theme.NrgLime
import dev.valid7996.narcicgeteway.ui.theme.NrgDeepViolet
import dev.valid7996.narcicgeteway.ui.theme.NrgViolet
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(
    connectionVm: ConnectionViewModel,
    activeConfigName: String?,
    contentPadding: PaddingValues,

    activeConfigDetail: String? = null,

    exitGeneration: Int = 0,
    onToggleConnection: () -> Unit,
    onBrowseConfigs: () -> Unit,
    onSecretUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    lockedNote: String? = null,
    activeLocked: Boolean = false,
    activeExpiresAt: Long? = null,
    activeCountryCode: String? = null,
    reduceMotion: Boolean = false,

    repository: dev.valid7996.narcicgeteway.data.config.ConfigRepository? = null,
    realPingConcurrency: Int = 4,
    autoSortAfterTest: Boolean = false,
    autoTestAfterUpdate: Boolean = false,
    autoRemoveInvalidAfterTest: Boolean = false,

    showConnectionInfo: Boolean = true,

    ipApiUrl: String = "",

    delayTestUrl: String = "",

    personalization: Personalization = Personalization.Default,

    connectStyle: ConnectButtonStyle = ConnectButtonStyle.Pill,
    settings: dev.valid7996.narcicgeteway.domain.model.AppSettings? = null,
    onUpdateSettings: (((dev.valid7996.narcicgeteway.domain.model.AppSettings) -> dev.valid7996.narcicgeteway.domain.model.AppSettings) -> Unit)? = null,
    homeVm: HomeViewModel = viewModel { HomeViewModel() },
) {
    val ui by connectionVm.ui.collectAsStateWithLifecycle()
    val live = ui.state == ConnectionState.Connected
    val info by homeVm.info.collectAsStateWithLifecycle()
    val infoExpanded by homeVm.expanded.collectAsStateWithLifecycle()

    LaunchedEffect(live, ui.sessionId, exitGeneration, showConnectionInfo, ipApiUrl, delayTestUrl) {
        homeVm.preferredIpApiUrl = ipApiUrl.ifBlank { null }
        homeVm.preferredDelayUrl = delayTestUrl.ifBlank { null }
        if (live && showConnectionInfo) homeVm.refresh(settleDelayMs = 300) else homeVm.clear()
    }

    var burst by remember { mutableIntStateOf(0) }

    var laser by remember { mutableIntStateOf(0) }

    var statusTaps by remember { mutableIntStateOf(0) }
    var statusLastTap by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()

    val isAzkaban = personalization.themeProfileId == "azkaban"
    val isNoir = personalization.themeProfileId == "noir"
    val isBlossom = personalization.themeProfileId == "blossom"
    val isAzkabanLike = isAzkaban || isNoir || isBlossom
    // پوسته‌ی Hellboy: اگر اپ Android آن را فراهم کرده، پس‌زمینه و چهره‌ی هلبوی جای ظاهر پیش‌فرض را می‌گیرند
    val skinHero = LocalSkinHero.current
    val skinBackdrop = LocalSkinBackdrop.current
    val isHellboy = personalization.themeProfileId == "hellboy" && skinHero != null && skinBackdrop != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (isHellboy) {
            skinBackdrop!!(Modifier.matchParentSize())
        } else DecorativeBackdrop(
            reduceMotion = reduceMotion,
            isAzkaban = isAzkaban,
            isNoir = isNoir,
            isBlossom = isBlossom,
            amoledBlack = personalization.amoledBlack,
            dynamicColor = personalization.dynamicColor,
        )

        ConnectingBackdrop(
            state = ui.state,
            sessionId = ui.sessionId,
            reduceMotion = reduceMotion,
            isAzkaban = isAzkaban,
            isNoir = isNoir,
            isBlossom = isBlossom,
            amoledBlack = personalization.amoledBlack,
            dynamicColor = personalization.dynamicColor,
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 22.dp),
        ) {
            val heroSize = minOf(302.dp, maxHeight * 0.41f)

            val compact = maxHeight < 620.dp

            val upperScroll = rememberScrollState()
            // One scrollable page: header, tiles, the blob, the controls and the server list all
            // travel together, so nothing is pinned mid-screen or clipped by the status bar.
            StageColumn(
                viewport = if (isAzkabanLike) maxHeight * 0.65f else maxHeight * 0.58f,
                stageMin = if (isAzkabanLike) 280.dp else if (compact) 110.dp else STAGE_MIN,
                modifier = Modifier.fillMaxWidth().scrollFade(upperScroll).verticalScroll(upperScroll),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandHeader(
                    state = ui.state,
                    onSecretUnlocked = onSecretUnlocked,
                )

                val fault = when {
                    ui.state == ConnectionState.Error ->
                        ui.error?.takeIf { it.isNotBlank() }
                            ?.let { dev.valid7996.narcicgeteway.core.ConnectionFault.classify(it) }
                    ui.state == ConnectionState.Connected -> info.fault
                    else -> null
                }
                AnimatedVisibility(visible = fault != null) {
                    fault?.let {
                        FaultBanner(
                            fault = it,
                            connected = ui.state == ConnectionState.Connected,
                        )
                    }
                }

                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth().layoutId(STAGE_ID)) {
                    val stage = maxHeight
                    val tight = stage < 260.dp
                    val stageHero = minOf(heroSize, stage * 0.58f)
                    val azkabanHeroSize = minOf(260.dp, maxOf(220.dp, stage * 0.70f))
                    Column(
                      modifier = Modifier.fillMaxSize(),
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.Center,
                    ) {
                      if (isHellboy) {
                        skinHero!!(
                            ui.state, ui.downloadBps, ui.uploadBps, azkabanHeroSize,
                            if (connectStyle == ConnectButtonStyle.Hero) {
                                {
                                    if (ui.state != ConnectionState.Disconnecting) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleConnection()
                                    }
                                }
                            } else null,
                        )
                      } else if (isBlossom) {
                        GirlHero(
                            state = ui.state,
                            size = azkabanHeroSize,
                            onTapHero = if (connectStyle == ConnectButtonStyle.Hero) {
                                {
                                    if (ui.state != ConnectionState.Disconnecting) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleConnection()
                                    }
                                }
                            } else null,
                        )
                      } else if (isNoir) {
                        MontaaHero(
                            state = ui.state,
                            size = azkabanHeroSize,
                            onTapHero = if (connectStyle == ConnectButtonStyle.Hero) {
                                {
                                    if (ui.state != ConnectionState.Disconnecting) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleConnection()
                                    }
                                }
                            } else null,
                        )
                      } else if (isAzkaban) {
                        AzkabanHero(
                            state = ui.state,
                            size = azkabanHeroSize,
                            onTapHero = if (connectStyle == ConnectButtonStyle.Hero) {
                                {
                                    if (ui.state != ConnectionState.Disconnecting) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleConnection()
                                    }
                                }
                            } else null,
                        )
                      } else {
                        Hero(
                            state = ui.state,
                            sessionId = ui.sessionId,
                            size = stageHero,
                            onLongPress = {
                                burst++
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },

                            onTap = if (connectStyle == ConnectButtonStyle.Hero) {
                                {
                                    if (ui.state != ConnectionState.Disconnecting) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleConnection()
                                    }
                                }
                            } else null,
                        )
                      }

                      Spacer(Modifier.height(if (tight) 8.dp else 20.dp))

                      AnimatedContent(
                          targetState = ui.state,
                          transitionSpec = {
                              (fadeIn(animationSpec = tween(220)) +
                                  scaleIn(initialScale = 0.92f, animationSpec = tween(220))) togetherWith
                                  fadeOut(animationSpec = tween(140))
                          },
                          label = "status-text",
                          modifier = Modifier.clickable(
                              interactionSource = remember { MutableInteractionSource() },
                              indication = null,
                          ) {
                              val now = System.currentTimeMillis()
                              statusTaps = if (now - statusLastTap < TAP_WINDOW_MS) statusTaps + 1 else 1
                              statusLastTap = now
                              if (statusTaps >= 3) {
                                  statusTaps = 0
                                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                  laser++
                              }
                          },
                      ) { s ->

                          Text(
                              text = stringResource(s.labelRes()),
                              style = if (tight) {
                                  MaterialTheme.typography.headlineMediumEmphasized
                              } else {
                                  MaterialTheme.typography.displaySmallEmphasized
                              },
                              maxLines = 1,
                              textAlign = TextAlign.Center,
                              color = when {
                                  s == ConnectionState.Error -> MaterialTheme.colorScheme.error

                                  s.isActive || s == ConnectionState.Disconnecting -> Color.White
                                  else -> MaterialTheme.colorScheme.onSurface
                              },
                          )
                      }

                      val deviceId = remember(repository) { repository?.nrgDeviceId().orEmpty() }
                      if (deviceId.isNotBlank()) {
                          Spacer(Modifier.height(10.dp))
                          Surface(
                              onClick = {
                                  platform.copyToClipboard(deviceId)
                                  scope.launch { platform.toast("کد دستگاه کپی شد") }
                              },
                              shape = RoundedCornerShape(12.dp),
                              color = if (personalization.amoledBlack) Color(0xFF141416) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                          ) {
                              Row(
                                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                  verticalAlignment = Alignment.CenterVertically,
                                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                              ) {
                                  Icon(
                                      painter = painterResource(Res.drawable.ic_key),
                                      contentDescription = null,
                                      modifier = Modifier.size(15.dp),
                                      tint = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else (personalization.connectColor ?: MaterialTheme.colorScheme.primary),
                                  )
                                  Text(
                                      text = "کد دستگاه من:",
                                      style = MaterialTheme.typography.labelMedium,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  )
                                  Text(
                                      text = deviceId,
                                      fontFamily = FontFamily.Monospace,
                                      fontWeight = FontWeight.Bold,
                                      style = MaterialTheme.typography.labelLarge,
                                      color = MaterialTheme.colorScheme.onSurface,
                                  )
                                  Spacer(Modifier.width(2.dp))
                                  Icon(
                                      painter = painterResource(Res.drawable.ic_check_circle),
                                      contentDescription = "کپی",
                                      modifier = Modifier.size(14.dp),
                                      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                  )
                              }
                          }
                      }

                      if (lockedNote != null) {
                          val noteScroll = rememberScrollState()
                          Spacer(Modifier.height(16.dp))
                          Surface(
                              shape = MaterialTheme.shapes.large,
                              color = MaterialTheme.colorScheme.surfaceContainerHigh,
                              modifier = Modifier.fillMaxWidth(),
                          ) {
                              NoteText(
                                  text = lockedNote,
                                  style = MaterialTheme.typography.bodyMedium,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  modifier = Modifier
                                      .fillMaxWidth()
                                      .heightIn(max = 140.dp)
                                      .verticalScroll(noteScroll)
                                      .padding(horizontal = 16.dp, vertical = 12.dp),
                              )
                          }
                      }
                    }
                }

                if (dev.valid7996.narcicgeteway.ui.navigation.NavConfig.SHOW_ACTIVE_CONFIG) {
                    ActiveConfigCard(
                        name = activeConfigName,
                        detail = activeConfigDetail,
                        locked = activeLocked,
                        expiresAt = activeExpiresAt,
                        countryCode = activeCountryCode,
                        onClick = onBrowseConfigs,
                        personalization = personalization,
                    )
                }

                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                when (connectStyle) {
                    ConnectButtonStyle.Hero -> Unit
                    ConnectButtonStyle.Pill, ConnectButtonStyle.Switch -> {
                        if (isAzkabanLike) {
                            AzkabanConnectButton(
                                state = ui.state,
                                onClick = onToggleConnection,
                                isNoir = isNoir,
                                isBlossom = isBlossom,
                                amoledBlack = personalization.amoledBlack,
                                dynamicColor = personalization.dynamicColor,
                            )
                        } else {
                            ConnectButton(
                                state = ui.state,
                                onToggle = onToggleConnection,
                                customColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else personalization.connectColor,
                                customActiveColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else personalization.connectedColor,
                                compact = compact,
                            )
                        }
                    }
                    ConnectButtonStyle.Slide -> SlideToConnect(
                        state = ui.state,
                        onToggle = onToggleConnection,
                        customColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else personalization.connectColor,
                        customActiveColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else personalization.connectedColor,
                        compact = compact,
                    )
                    else -> AltConnectControl(
                        style = connectStyle,
                        state = ui.state,
                        onToggle = onToggleConnection,
                        customColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else personalization.connectColor,
                        customActiveColor = if (personalization.dynamicColor) MaterialTheme.colorScheme.primary else personalization.connectedColor,
                        compact = compact,
                    )
                }
                Spacer(Modifier.height(12.dp))

                // The one merged dashboard: location, address and live traffic in a single
                // green panel. It never collapses; it simply exists while the tunnel is up.
                AnimatedVisibility(visible = live && showConnectionInfo) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ConnectionMapTriggerButton(
                            expanded = infoExpanded,
                            destCountryCode = info.countryCode?.takeIf { it.isNotBlank() } ?: activeCountryCode,
                            onClick = homeVm::toggleExpanded,
                            personalization = personalization,
                            isAzkaban = isAzkaban,
                            isNoir = isNoir,
                            isBlossom = isBlossom,
                        )
                        AnimatedVisibility(visible = infoExpanded) {
                            ConnectionRouteMapCard(
                                info = info,
                                activeConfigName = activeConfigName,
                                fallbackCountryCode = activeCountryCode,
                                onClose = homeVm::toggleExpanded,
                                personalization = personalization,
                                isAzkaban = isAzkaban,
                                isNoir = isNoir,
                                isBlossom = isBlossom,
                            )
                        }
                        MergedDashboardCard(
                            info = info,
                            downloadBps = ui.downloadBps,
                            uploadBps = ui.uploadBps,
                            totalDownload = ui.totalDownload,
                            totalUpload = ui.totalUpload,
                            onRefresh = homeVm::refresh,
                            onToggleMap = homeVm::toggleExpanded,
                            connectedColor = personalization.connectedColor ?: ConnectedGreen,
                            personalization = personalization,
                            isAzkaban = isAzkaban,
                            isNoir = isNoir,
                            isBlossom = isBlossom,
                        )
                    }
                }


                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.height(96.dp))
            }
        }

        ParticleBurst(trigger = if (reduceMotion) 0 else burst)

        dev.valid7996.narcicgeteway.ui.easteregg.LaserBounce(
            trigger = laser,
            color = personalization.connectColor ?: MaterialTheme.colorScheme.primary,
            reduceMotion = reduceMotion,
        )
    }
}

private const val STAGE_ID = "home-stage"

private val STAGE_MIN = 148.dp

private fun Modifier.scrollFade(state: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (state.canScrollForward || state.canScrollBackward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        val fade = length.toPx()
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private const val MAX_STAGE_PX = 16_384

@Composable
private fun StageColumn(
    viewport: Dp,
    stageMin: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth

        val natural = Constraints(maxWidth = width)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var stageIndex = -1
        var fixed = 0
        measurables.forEachIndexed { i, child ->
            if (child.layoutId == STAGE_ID) {
                stageIndex = i
            } else {
                placeables[i] = child.measure(natural).also { fixed += it.height }
            }
        }

        val viewportPx = if (viewport.isFinite) viewport.roundToPx().coerceIn(0, MAX_STAGE_PX) else 0
        val stagePx = if (stageIndex < 0) 0 else maxOf(stageMin.roundToPx(), viewportPx - fixed).coerceIn(0, MAX_STAGE_PX)
        if (stageIndex >= 0) {
            placeables[stageIndex] = measurables[stageIndex].measure(Constraints.fixed(width, stagePx))
        }
        val total = placeables.fold(0) { sum, placeable -> sum + (placeable?.height ?: 0) }
        layout(width, maxOf(viewportPx, fixed + stagePx, total)) {
            var y = 0
            placeables.forEach { placeable ->
                if (placeable == null) return@forEach
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun DecorativeBackdrop(
    reduceMotion: Boolean,
    isAzkaban: Boolean = false,
    isNoir: Boolean = false,
    isBlossom: Boolean = false,
    amoledBlack: Boolean = false,
    dynamicColor: Boolean = false,
) {
    val budget = LocalMotionBudget.current
    val spin by if (budget == MotionBudget.Full) {
        rememberInfiniteTransition(label = "backdrop").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(120_000), RepeatMode.Restart),
            label = "spin",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val rotation = if (reduceMotion || budget != MotionBudget.Full) 0f else spin
    val splash = if (amoledBlack) {
        val accent = when {
            dynamicColor -> MaterialTheme.colorScheme.primary
            isBlossom -> Color(0xFFF472B6)
            isNoir -> Color(0xFFFFFFFF)
            isAzkaban -> Color(0xFF38BDF8)
            else -> NrgCyan
        }
        listOf(Color(0xFF000000), Color(0xFF000000), accent.copy(alpha = 0.35f))
    } else if (dynamicColor) {
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
    } else if (isBlossom) {
        listOf(Color(0xFF150510), Color(0xFF3B1229), Color(0xFFF472B6))
    } else if (isNoir) {
        listOf(Color(0xFF000000), Color(0xFF18181B), Color(0xFF3F3F46))
    } else if (isAzkaban) {
        listOf(Color(0xFF040A16), Color(0xFF0C1D38), Color(0xFF38BDF8))
    } else {
        listOf(NrgDeepViolet, NrgViolet, NrgCyan)
    }
    // A quiet splash: one large disc with two droplets thrown off it.
    Box(
        Modifier
            .size(320.dp)
            .offset(x = 190.dp, y = (-140).dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        Box(
            Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Brush.linearGradient(splash)),
        )
        Box(
            Modifier
                .size(46.dp)
                .align(Alignment.BottomStart)
                .clip(CircleShape)
                .background(Brush.linearGradient(splash.map { it.copy(alpha = 0.75f) })),
        )
        Box(
            Modifier
                .size(22.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Brush.linearGradient(splash.map { it.copy(alpha = 0.5f) })),
        )
    }
}

/**
 * The home hero: a ringed planet — one large glossy sphere with a moonlet hugging it, a thin
 * orbit ring and a few droplets, all tinted by the connection state.
 */
@Composable
private fun PlanetBody(colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.47f
        val r = size.minDimension * 0.335f

        val ringW = r * 3.0f
        val ringH = r * 1.12f
        val ringTop = Offset(cx - ringW / 2f, cy - ringH / 2f)
        val ringSize = Size(ringW, ringH)

        // the orbit ring behind the sphere
        rotate(-16f, pivot = Offset(cx, cy)) {
            drawOval(
                color = colors[1].copy(alpha = 0.5f),
                topLeft = ringTop,
                size = ringSize,
                style = Stroke(width = r * 0.045f),
            )
        }

        // the planet
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors[0], colors[1], colors[2].copy(alpha = 0.85f)),
                center = Offset(cx - r * 0.35f, cy - r * 0.45f),
                radius = r * 1.9f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
        // shading toward the far side
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(cx + r * 0.55f, cy + r * 0.6f),
                radius = r * 1.25f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )

        // the moonlet hugging the lower-left
        val mr = r * 0.40f
        val mc = Offset(cx - r * 0.80f, cy + r * 0.60f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors[2], colors[0]),
                center = Offset(mc.x - mr * 0.3f, mc.y - mr * 0.35f),
                radius = mr * 1.6f,
            ),
            radius = mr,
            center = mc,
        )

        // droplets thrown off the planet
        drawCircle(colors[2].copy(alpha = 0.9f), radius = r * 0.055f, center = Offset(cx - r * 1.28f, cy - r * 0.92f))
        drawCircle(colors[1].copy(alpha = 0.8f), radius = r * 0.04f, center = Offset(cx + r * 1.32f, cy + r * 0.38f))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = r * 0.03f, center = Offset(cx + r * 0.92f, cy - r * 1.06f))

        // specular highlight
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(cx - r * 0.38f, cy - r * 0.5f),
                radius = r * 0.7f,
            ),
            radius = r,
            center = Offset(cx, cy),
        )

        // the orbit ring's front half, passing over the planet
        rotate(-16f, pivot = Offset(cx, cy)) {
            clipRect(left = 0f, top = cy, right = size.width, bottom = size.height) {
                drawOval(
                    color = colors[1].copy(alpha = 0.85f),
                    topLeft = ringTop,
                    size = ringSize,
                    style = Stroke(width = r * 0.05f),
                )
            }
        }
    }
}

@Composable
private fun Hero(
    state: ConnectionState,
    sessionId: Int,
    size: Dp,
    onLongPress: () -> Unit,

    onTap: (() -> Unit)? = null,
) {
    val budget = LocalMotionBudget.current
    val transitional = state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
    val breatheState = if (budget == MotionBudget.Full || budget == MotionBudget.Throttled && transitional) {
        rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 0.97f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(2800), RepeatMode.Reverse),
            label = "breathe",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val targetColors = when (state) {
        ConnectionState.Connected -> listOf(NrgViolet, NrgCyan, NrgLime)
        ConnectionState.Connecting, ConnectionState.Reconnecting -> listOf(Color(0xFF1E4A9E), NrgCyan, NrgLime)
        else -> listOf(NrgDeepViolet, Color(0xFF173B7C), NrgViolet)
    }
    val liquidColors = targetColors.mapIndexed { index, color ->
        animateColorAsState(color, tween(900), label = "liquid$index").value
    }

    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val s = if (state.isActive) breatheState.value else 1f
                scaleX = s
                scaleY = s
            }

            .pointerInput(onTap) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = onTap?.let { handler -> { _ -> handler() } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PlanetBody(colors = liquidColors, modifier = Modifier.fillMaxSize())
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "hero-center",
        ) { s ->
            if (s == ConnectionState.Error) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock_open),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(size * 0.32f),
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(
    state: ConnectionState,
    onSecretUnlocked: () -> Unit,
) {
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f, fill = false)) { StatusChip(state) }
        }
        Text(
            text = run {
                val name = stringResource(Res.string.app_name)
                buildAnnotatedString {
                    val cut = name.lastIndexOf(' ')
                    if (cut > 0) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                            append(name.substring(0, cut))
                        }
                        append(" ")
                        withStyle(SpanStyle(color = ConnectedGreen)) {
                            append(name.substring(cut + 1))
                        }
                    } else {
                        withStyle(SpanStyle(color = ConnectedGreen)) { append(name) }
                    }
                }
            },

            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < TAP_WINDOW_MS) taps + 1 else 1
                    lastTap = now
                    if (taps >= TAPS_TO_UNLOCK) {
                        taps = 0
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSecretUnlocked()
                    } else if (taps > 1) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FaultBanner(
    fault: dev.valid7996.narcicgeteway.core.ConnectionFault,
    connected: Boolean,
) {
    var expanded by remember(fault.raw) { mutableStateOf(false) }
    val known = fault.cause != dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Unknown
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(
                    if (connected) Res.string.fault_heading_health else Res.string.fault_heading_connect,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            if (known) {
                Text(
                    stringResource(faultTitle(fault.cause)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(faultHint(fault.cause)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (fault.raw.isNotBlank()) {
                Text(
                    fault.raw,
                    style = if (known) MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ) else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = if (known) 0.75f else 1f),

                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun faultTitle(c: dev.valid7996.narcicgeteway.core.ConnectionFault.Cause) = when (c) {
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Timeout -> Res.string.fault_timeout
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.ServerClosed -> Res.string.fault_server_closed
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Refused -> Res.string.fault_refused
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Reset -> Res.string.fault_reset
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Unreachable -> Res.string.fault_unreachable
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.DnsFailed -> Res.string.fault_dns
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.TlsHandshake -> Res.string.fault_tls
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Certificate -> Res.string.fault_certificate
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Reality -> Res.string.fault_reality
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Auth -> Res.string.fault_auth
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Transport -> Res.string.fault_transport
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Unknown -> Res.string.fault_unknown
}

private fun faultHint(c: dev.valid7996.narcicgeteway.core.ConnectionFault.Cause) = when (c) {
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Timeout -> Res.string.fault_timeout_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.ServerClosed -> Res.string.fault_server_closed_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Refused -> Res.string.fault_refused_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Reset -> Res.string.fault_reset_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Unreachable -> Res.string.fault_unreachable_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.DnsFailed -> Res.string.fault_dns_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.TlsHandshake -> Res.string.fault_tls_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Certificate -> Res.string.fault_certificate_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Reality -> Res.string.fault_reality_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Auth -> Res.string.fault_auth_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Transport -> Res.string.fault_transport_hint
    dev.valid7996.narcicgeteway.core.ConnectionFault.Cause.Unknown -> Res.string.fault_unknown
}

private const val TAPS_TO_UNLOCK = 5

private const val TAP_WINDOW_MS = 1_200L

@Composable
private fun StatusChip(state: ConnectionState) {
    val label = when (state) {
        ConnectionState.Connected -> stringResource(Res.string.state_connected)
        ConnectionState.Connecting, ConnectionState.Reconnecting ->
            stringResource(Res.string.state_connecting)
        ConnectionState.Error -> stringResource(Res.string.state_error)
        else -> stringResource(Res.string.state_idle)
    }

    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    val stretchX = (abs(dragX.value) / 90f).coerceAtMost(0.30f)
    val stretchY = (abs(dragY.value) / 90f).coerceAtMost(0.30f)

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chipBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val chipBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    Surface(
        shape = CircleShape,
        color = chipBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, chipBorder),
        modifier = Modifier
            .graphicsLayer {
                translationX = dragX.value
                translationY = dragY.value
                scaleX = 1f + stretchX - stretchY * 0.5f
                scaleY = 1f + stretchY - stretchX * 0.5f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch {
                            val pull = 1f / (1f + hypot(dragX.value, dragY.value) / 240f)
                            dragX.snapTo(dragX.value + drag.x * pull)
                            dragY.snapTo(dragY.value + drag.y * pull)
                        }
                    },
                    onDragEnd = {
                        val spec = spring<Float>(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow)
                        scope.launch { dragX.animateTo(0f, spec) }
                        scope.launch { dragY.animateTo(0f, spec) }
                    },
                )
            },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dotColor = when (state) {
                ConnectionState.Connected -> Color(0xFF34C759)
                ConnectionState.Connecting, ConnectionState.Reconnecting -> Color(0xFFFF9500)
                ConnectionState.Error -> Color(0xFFFF3B30)
                else -> if (isDark) Color(0xFF636366) else Color(0xFFAEAEB2)
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveConfigCard(
    name: String?,
    detail: String? = null,
    locked: Boolean,
    expiresAt: Long? = null,
    countryCode: String? = null,
    onClick: () -> Unit,
    personalization: Personalization = Personalization.Default,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "config-scale",
    )
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAmoled = personalization.amoledBlack
    val isAzkaban = personalization.themeProfileId == "azkaban"
    val isNoir = personalization.themeProfileId == "noir"
    val isBlossom = personalization.themeProfileId == "blossom"

    val shape = RoundedCornerShape(16.dp)
    val cardBg = when {
        isAmoled -> Color(0xFF000000)
        personalization.configCardColor != null -> personalization.configCardColor
        isBlossom -> Color(0xFF1F0B16)
        isNoir -> Color(0xFF121214)
        isAzkaban -> Color(0xFF091424)
        isDark -> Color(0xFF1C1C1E)
        else -> Color(0xFFFFFFFF)
    }
    val cardBorder = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = if (isAmoled) 0.50f else 0.35f)
        isBlossom -> Color(0xFFF472B6).copy(alpha = if (isAmoled) 0.50f else 0.35f)
        isNoir -> Color(0xFFFFFFFF).copy(alpha = if (isAmoled) 0.35f else 0.20f)
        isAzkaban -> Color(0xFF38BDF8).copy(alpha = if (isAmoled) 0.50f else 0.35f)
        isAmoled -> Color(0xFF10B981).copy(alpha = 0.45f)
        isDark -> Color(0xFF2C2C2E)
        else -> Color(0xFFE5E5EA)
    }
    val titleColor = when {
        personalization.configCardTextColor != null -> personalization.configCardTextColor
        isBlossom -> Color(0xFFFFF1F5)
        isNoir -> Color(0xFFFFFFFF)
        isAzkaban -> Color(0xFFF0F9FF)
        isDark -> Color(0xFFF2F2F7)
        else -> Color(0xFF000000)
    }
    val subtitleColor = when {
        personalization.tapHintColor != null -> personalization.tapHintColor
        isBlossom -> Color(0xFFFFD1DC)
        isNoir -> Color(0xFF8E8E93)
        isAzkaban -> Color(0xFF94A3B8)
        isDark -> Color(0xFF8E8E93)
        else -> Color(0xFF6E6E73)
    }
    val tileBg = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary
        isBlossom -> Color(0xFFFF85A1)
        isNoir -> Color(0xFF2C2C2E)
        isAzkaban -> Color(0xFF0284C7)
        personalization.configCardTextColor != null -> personalization.configCardTextColor
        else -> Color(0xFF007AFF)
    }

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        color = cardBg,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .border(0.5.dp, cardBorder, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (locked) Res.drawable.ic_encrypted else Res.drawable.ic_public),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (countryCode != null && !locked) {
                        dev.valid7996.narcicgeteway.ui.components.FlagBadge(
                            countryCode = countryCode,
                            size = 18.dp,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        text = name ?: stringResource(Res.string.home_select_config),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (locked && expiresAt != null) {
                        Spacer(Modifier.width(8.dp))
                        dev.valid7996.narcicgeteway.ui.servers.ExpiryCountdownBadge(
                            expiresAt = expiresAt,
                            isDark = isDark,
                            fontSize = 11.sp,
                        )
                    }
                }
                Text(
                    text = detail ?: stringResource(
                        when {
                            name == null -> Res.string.home_no_config_body
                            locked -> Res.string.home_tap_to_change_vault
                            else -> Res.string.home_tap_to_change
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = if (isDark) Color(0xFF545458) else Color(0xFFC7C7CC),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        if (direction == LayoutDirection.Rtl) {
                            rotationY = 180f
                        }
                    },
            )
        }
    }
}

@Composable
private fun ConnectButton(
    state: ConnectionState,
    onToggle: () -> Unit,
    customColor: Color? = null,
    customActiveColor: Color? = null,

    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "connect-scale",
    )

    val glowTarget = when {
        state == ConnectionState.Error -> 0f
        state.isActive -> 1f
        else -> 0.45f
    }
    val glow by animateFloatAsState(
        targetValue = if (LocalMotionBudget.current == MotionBudget.Paused) 0f else glowTarget,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "connect-glow",
    )

    val idleContent = if (customColor == null) MaterialTheme.colorScheme.onPrimary
    else if (customColor.luminance() > 0.5f) Color(0xFF10131A) else Color.White
    val activeContent = if (customActiveColor == null) MaterialTheme.colorScheme.onPrimaryContainer
    else if (customActiveColor.luminance() > 0.5f) Color(0xFF10131A) else Color.White
    val content = when {
        state == ConnectionState.Error -> MaterialTheme.colorScheme.onErrorContainer
        state.isActive -> activeContent
        else -> idleContent
    }

    Box(Modifier.fillMaxWidth()) {
        // Aurora glow behind the button — the signature of the new design.
        if (glow > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(if (compact) 56.dp else 72.dp)
                    .graphicsLayer {
                        alpha = glow * 0.5f
                        val spread = 1f + 16.dp.toPx() * glow / size.height.coerceAtLeast(1f)
                        scaleX = spread
                        scaleY = spread
                    }
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(NrgViolet.copy(alpha = 0.55f), NrgCyan.copy(alpha = 0.55f)),
                        ),
                    ),
            )
        }

        Surface(
            onClick = onToggle,

            enabled = state != ConnectionState.Disconnecting,
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            contentColor = content,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 56.dp else 72.dp)
                .scale(scale)
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        state == ConnectionState.Error -> Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.errorContainer,
                            ),
                        )
                        state.isActive && customActiveColor != null -> Brush.horizontalGradient(
                            listOf(customActiveColor, customActiveColor.copy(alpha = 0.82f)),
                        )
                        state.isActive -> Brush.horizontalGradient(
                            listOf(NrgViolet, NrgCyan),
                        )
                        customColor != null -> Brush.horizontalGradient(
                            listOf(customColor, customColor.copy(alpha = 0.82f)),
                        )
                        else -> Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                            ),
                        )
                    },
                ),
        ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = state.isTransitioning,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) +
                        scaleIn(initialScale = 0.9f, animationSpec = tween(200))) togetherWith
                        fadeOut(animationSpec = tween(120))
                },
                label = "connect-label",
            ) { busy ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CyberRadarIndicator(
                            modifier = Modifier.size(26.dp),
                        )
                    } else {
                        Icon(
                            painterResource(
                                if (state.isActive) Res.drawable.ic_lock_open else Res.drawable.ic_bolt
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            when {
                                state == ConnectionState.Disconnecting -> Res.string.state_disconnecting

                                busy -> Res.string.action_cancel
                                state.isActive -> Res.string.action_disconnect
                                else -> Res.string.action_connect
                            }
                        ),
                        style = if (compact) {
                            MaterialTheme.typography.titleMediumEmphasized
                        } else {
                            MaterialTheme.typography.titleLargeEmphasized
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ConnectingBackdrop(
    state: ConnectionState,
    sessionId: Int,
    reduceMotion: Boolean,
    isAzkaban: Boolean = false,
    isNoir: Boolean = false,
    isBlossom: Boolean = false,
    amoledBlack: Boolean = false,
    dynamicColor: Boolean = false,
) {
    val intensity by animateFloatAsState(
        targetValue = when (state) {
            ConnectionState.Connecting, ConnectionState.Reconnecting -> 0.72f
            ConnectionState.Connected -> 1f
            ConnectionState.Disconnecting -> 0.35f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "backdrop-intensity",
    )

    val budget = LocalMotionBudget.current
    val pulseRuns = !reduceMotion && when (budget) {
        MotionBudget.Full -> true
        MotionBudget.Throttled -> state == ConnectionState.Connecting || state == ConnectionState.Reconnecting
        MotionBudget.Paused -> false
    }
    val pulse by if (!pulseRuns) {
        remember { mutableStateOf(0f) }
    } else {
        rememberInfiniteTransition(label = "backdrop-life").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(8500), RepeatMode.Restart),
            label = "pulse",
        )
    }

    val stops = if (amoledBlack) {
        if (state == ConnectionState.Connected) {
            val primary = when {
                dynamicColor -> MaterialTheme.colorScheme.primary
                isBlossom -> Color(0xFFDB2777)
                isNoir -> Color(0xFF3F3F46)
                isAzkaban -> Color(0xFF0284C7)
                else -> dev.valid7996.narcicgeteway.ui.theme.NrgGreen
            }
            val accent = when {
                dynamicColor -> MaterialTheme.colorScheme.tertiary
                isBlossom -> Color(0xFFF472B6)
                isNoir -> Color(0xFFFFFFFF)
                isAzkaban -> Color(0xFF38BDF8)
                else -> dev.valid7996.narcicgeteway.ui.theme.NrgLime
            }
            listOf(Color(0xFF000000), Color(0xFF000000), primary.copy(alpha = 0.30f), accent.copy(alpha = 0.18f))
        } else {
            listOf(Color(0xFF000000), Color(0xFF000000), Color(0xFF000000))
        }
    } else if (dynamicColor) {
        if (state == ConnectionState.Connected) {
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
            )
        } else {
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.surfaceContainer,
            )
        }
    } else if (isBlossom) {
        if (state == ConnectionState.Connected) {
            listOf(Color(0xFF150510), Color(0xFF260D1A), Color(0xFFDB2777).copy(alpha = 0.35f), Color(0xFFF472B6).copy(alpha = 0.2f))
        } else {
            listOf(Color(0xFF0D0209), Color(0xFF1A0812), Color(0xFF2A0E1E))
        }
    } else if (isNoir) {
        if (state == ConnectionState.Connected) {
            listOf(Color(0xFF000000), Color(0xFF0C0C0E), Color(0xFF18181B).copy(alpha = 0.5f), Color(0xFF27272A).copy(alpha = 0.25f))
        } else {
            listOf(Color(0xFF000000), Color(0xFF09090B), Color(0xFF121214))
        }
    } else if (isAzkaban) {
        if (state == ConnectionState.Connected) {
            listOf(Color(0xFF040A16), Color(0xFF071224), Color(0xFF0284C7).copy(alpha = 0.4f), Color(0xFF38BDF8).copy(alpha = 0.2f))
        } else {
            listOf(Color(0xFF02050A), Color(0xFF071224), Color(0xFF0A1830))
        }
    } else if (state == ConnectionState.Connected) {
        NrgGradients.connectedStops(sessionId)
    } else {
        NrgGradients.connectingStops
    }

    // Circle.json floating particles in background for Default, Azkaban, Noir, and Blossom
    val particleColors = when {
        dynamicColor -> listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primaryContainer,
        )
        isBlossom -> listOf(
            Color(0xFFFF85A1), // Candy Rose
            Color(0xFFF472B6), // Sakura Pink
            Color(0xFFFFD1DC), // Soft Petal
            Color(0xFFC084FC), // Lavender Orchid
        )
        isNoir -> listOf(
            Color(0xFFFFFFFF), // Crisp White
            Color(0xFFE4E4E7), // Silver
            Color(0xFFA1A1AA), // Slate Gray
            Color(0xFF71717A), // Charcoal
        )
        isAzkaban -> listOf(
            Color(0xFF7DD3FC), // Soft Sky Cyan
            Color(0xFF38BDF8), // Ice Blue
            Color(0xFFBAE6FD), // Light Frost
            Color(0xFF94A3B8), // Mellow Slate
        )
        else -> listOf(
            Color(0xFF10B981), // Emerald
            Color(0xFF38BDF8), // Cyan
            Color(0xFFA78BFA), // Soft Violet
            Color(0xFF34D399), // Mint Green
        )
    }

    Canvas(Modifier.fillMaxSize()) {
        if (intensity <= 0.002f) return@Canvas
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(colors = stops, startY = 0f, endY = h),
            alpha = 0.9f * intensity,
        )

        // Particle definitions from Circle.json: (relativeX, radiusDp, isStroke)
        val particles = listOf(
            Triple(0.825f, 9f, false),
            Triple(0.704f, 6.5f, false),
            Triple(0.185f, 10f, false),
            Triple(0.492f, 10f, false),
            Triple(0.496f, 15f, true),
            Triple(0.235f, 8f, true),
            Triple(0.308f, 8f, true),
            Triple(0.937f, 14f, true),
            Triple(0.114f, 14f, true),
        )

        val strokeStyle = Stroke(width = 1.8f)

        // Draw Wave 1 and Wave 2 (offset by 0.5f)
        for (wave in 0..1) {
            val waveOffset = wave * 0.5f
            particles.forEachIndexed { index, (relX, baseRadius, isStroke) ->
                val p = (pulse + waveOffset + (index * 0.04f)) % 1f
                val y = h * (1.08f - p * 1.18f)
                val drift = sin((p * PI * 2).toFloat()) * (12f * (index % 3 + 1))
                val x = (w * relX + drift).coerceIn(0f, w)

                // Smooth sinusoidal alpha: gentle fade-in at bottom, gentle fade-out at top
                val alphaCurve = sin((p * PI).toFloat()).coerceIn(0f, 1f)
                val particleAlpha = (0.22f * alphaCurve * intensity).coerceIn(0f, 1f)
                val color = particleColors[index % particleColors.size].copy(alpha = particleAlpha)

                if (isStroke) {
                    drawCircle(
                        color = color,
                        radius = baseRadius * density,
                        center = Offset(x, y),
                        style = strokeStyle,
                    )
                } else {
                    drawCircle(
                        color = color,
                        radius = baseRadius * density,
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}

private class BurstParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float,
    val square: Boolean,
    var life: Float = 1f,
)

@Composable
private fun ParticleBurst(trigger: Int) {
    val colors = listOf(NrgViolet, NrgHotPink, NrgLime, NrgCyan)
    val particles = remember { mutableStateListOf<BurstParticle>() }
    var tick by remember { mutableLongStateOf(0L) }
    var canvas by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        var guard = 0
        while (canvas == Size.Zero && guard < 5) {
            withInfiniteAnimationFrameNanos { }
            guard++
        }
        if (canvas == Size.Zero) return@LaunchedEffect
        val cx = canvas.width / 2f
        val cy = canvas.height * 0.42f
        repeat(56) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 8f + Random.nextFloat() * 20f
            particles += BurstParticle(
                x = cx,
                y = cy,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 8f,
                color = colors[Random.nextInt(colors.size)],
                radius = 7f + Random.nextFloat() * 11f,
                square = Random.nextBoolean(),
            )
        }
        var last = 0L
        while (particles.isNotEmpty()) {
            withInfiniteAnimationFrameNanos { now ->
                val step = if (last == 0L) 1f else ((now - last) / 16_666_666f).coerceIn(0f, 3f)
                last = now
                particles.forEach { p ->
                    p.vy += 0.9f * step
                    p.vx *= 1f - 0.02f * step
                    p.x += p.vx * step
                    p.y += p.vy * step
                    p.life -= 0.014f * step
                }
                particles.removeAll { it.life <= 0f || it.y > canvas.height + 60f }
                tick = now
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        tick
        particles.forEach { p ->
            val color = p.color.copy(alpha = p.life.coerceIn(0f, 1f))
            if (p.square) {
                drawRect(
                    color = color,
                    topLeft = Offset(p.x - p.radius / 2f, p.y - p.radius / 2f),
                    size = Size(p.radius, p.radius),
                )
            } else {
                drawCircle(color = color, radius = p.radius / 2f, center = Offset(p.x, p.y))
            }
        }
    }
}

private fun ConnectionState.labelRes(): StringResource = when (this) {
    ConnectionState.Idle -> Res.string.state_idle
    ConnectionState.Connecting -> Res.string.state_connecting
    ConnectionState.Connected -> Res.string.state_connected
    ConnectionState.Reconnecting -> Res.string.state_reconnecting
    ConnectionState.Disconnecting -> Res.string.state_disconnecting
    ConnectionState.Error -> Res.string.state_error
}

private val ConnectedGreen = dev.valid7996.narcicgeteway.ui.theme.NrgGreen

/**
 * The one merged dashboard: location, address and live traffic in a single always-open panel.
 * The green wash is the "connected" colour of the app at its 60% focus strength.
 */
@Composable
private fun MergedDashboardCard(
    info: NetworkInfo,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    onRefresh: () -> Unit,
    onToggleMap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    connectedColor: Color = ConnectedGreen,
    personalization: Personalization = Personalization.Default,
    isAzkaban: Boolean = false,
    isNoir: Boolean = false,
    isBlossom: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAmoled = personalization.amoledBlack
    val cardBg = when {
        isAmoled -> Color(0xFF000000)
        isBlossom -> Color(0xFF260D1A)
        isNoir -> Color(0xFF141416)
        isAzkaban -> Color(0xFF0C192E)
        isDark -> Color(0xFF1C1C1E)
        else -> Color(0xFFFFFFFF)
    }
    val cardBorder = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = if (isAmoled) 0.60f else 0.40f)
        isBlossom -> Color(0xFFF472B6).copy(alpha = if (isAmoled) 0.60f else 0.40f)
        isNoir -> Color(0xFFFFFFFF).copy(alpha = if (isAmoled) 0.45f else 0.25f)
        isAzkaban -> Color(0xFF38BDF8).copy(alpha = if (isAmoled) 0.55f else 0.35f)
        isAmoled -> (personalization.connectedColor ?: ConnectedGreen).copy(alpha = 0.50f)
        isDark -> Color(0xFF2C2C2E)
        else -> Color(0xFFE5E5EA)
    }

    val primaryTextColor = when {
        isBlossom -> Color(0xFFFFF1F5)
        isNoir -> Color(0xFFFFFFFF)
        isAzkaban -> Color(0xFFF0F9FF)
        isDark -> Color(0xFFF2F2F7)
        else -> Color(0xFF000000)
    }

    val secondaryTextColor = when {
        isBlossom -> Color(0xFFFFD1DC)
        isNoir -> Color(0xFF8E8E93)
        isAzkaban -> Color(0xFF94A3B8)
        isDark -> Color(0xFF8E8E93)
        else -> Color(0xFF6E6E73)
    }

    val accentColor = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary
        isBlossom -> Color(0xFFFF85A1)
        isNoir -> Color(0xFFFFFFFF)
        isAzkaban -> Color(0xFF38BDF8)
        else -> Color(0xFF007AFF)
    }

    val dividerColor = when {
        personalization.dynamicColor -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        isBlossom -> Color(0xFFF472B6).copy(alpha = 0.22f)
        isNoir -> Color(0xFFFFFFFF).copy(alpha = 0.18f)
        isAzkaban -> Color(0xFF38BDF8).copy(alpha = 0.20f)
        isDark -> Color(0xFF2C2C2E)
        else -> Color(0xFFE5E5EA)
    }

    Surface(color = Color.Transparent, shape = shape, modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cardBg)
                .border(0.5.dp, cardBorder, shape),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagBadge(countryCode = info.countryCode, size = 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = listOfNotNull(info.city, info.country).joinToString(", ")
                                .ifBlank { null }
                                ?: stringResource(Res.string.info_unknown_location),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = info.ipv4 ?: stringResource(Res.string.info_no_ip),
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            maxLines = 1,
                        )
                    }
                    info.pingMs?.let { ping ->
                        Text(
                            text = "$ping ms",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isBlossom -> Color(0xFFFF85A1)
                                isNoir -> Color(0xFFFFFFFF)
                                isAzkaban -> Color(0xFF38BDF8)
                                ping < 250 -> primaryTextColor
                                else -> MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    if (onToggleMap != null) {
                        IconButton(onClick = onToggleMap) {
                            Icon(
                                painterResource(Res.drawable.ic_map),
                                contentDescription = "Map",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !info.loading) {
                        if (info.loading) {
                            CyberRadarIndicator(
                                modifier = Modifier.size(18.dp),
                            )
                        } else {
                            Icon(
                                painterResource(Res.drawable.ic_sync),
                                contentDescription = stringResource(Res.string.info_refresh),
                                tint = secondaryTextColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashboardReading(
                        icon = Res.drawable.ic_download,
                        label = stringResource(Res.string.home_download),
                        bytesPerSecond = downloadBps,
                        total = totalDownload,
                        modifier = Modifier.weight(1f),
                        primaryColor = primaryTextColor,
                        secondaryColor = secondaryTextColor,
                        accentColor = Color(0xFF007AFF),
                    )
                    Box(
                        Modifier
                            .width(0.5.dp)
                            .height(44.dp)
                            .background(dividerColor),
                    )
                    DashboardReading(
                        icon = Res.drawable.ic_upload,
                        label = stringResource(Res.string.home_upload),
                        bytesPerSecond = uploadBps,
                        total = totalUpload,
                        modifier = Modifier.weight(1f),
                        primaryColor = primaryTextColor,
                        secondaryColor = secondaryTextColor,
                        accentColor = Color(0xFF34C759),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardReading(
    icon: DrawableResource,
    label: String,
    bytesPerSecond: Long,
    total: Long,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accentColor: Color? = null,
) {
    val (value, unit) = formatRate(bytesPerSecond)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = if (bytesPerSecond > 0) (accentColor ?: primaryColor) else secondaryColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = primaryColor,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(
                formatBytes(total),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1,
            )
        }
    }
}

