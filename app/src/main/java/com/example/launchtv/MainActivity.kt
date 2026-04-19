package com.example.launchtv

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.view.WindowManager
import android.net.Uri
import android.util.Log
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import com.example.launchtv.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LaunchTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MainScreen()
                }
            }
        }
    }
}

data class LaunchableApp(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap,
    val intent: Intent
)

data class TvChannel(
    val name: String,
    val url: String,
    val logoUrl: String? = null
)

enum class NavSection {
    TV, Apps, Settings
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember { 
        context.applicationContext.getSharedPreferences("LaunchTVPrefs", Context.MODE_PRIVATE) 
    }
    
    var m3uLink by rememberSaveable { 
        mutableStateOf(sharedPrefs.getString("m3u_link", "") ?: "") 
    }

    var selectedSection by rememberSaveable { mutableStateOf(NavSection.TV) }
    var playingUrl by rememberSaveable { 
        mutableStateOf(sharedPrefs.getString("last_played_url", null)) 
    }

    var isNavVisible by rememberSaveable { mutableStateOf(false) }
    var navHadFocus by remember { mutableStateOf(false) }
    val navFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    // Cache apps at MainScreen level for instant switching
    val apps by produceState<List<LaunchableApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                
                val launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
                    ?: pm.getLaunchIntentForPackage(packageName)
                
                if (launchIntent != null) {
                    val banner = try { resolveInfo.activityInfo.loadBanner(pm) } catch (e: Exception) { null }
                    val drawable = banner ?: resolveInfo.loadIcon(pm)
                    
                    LaunchableApp(
                        name = resolveInfo.loadLabel(pm).toString(),
                        packageName = packageName,
                        icon = drawable.toBitmap().asImageBitmap(),
                        intent = launchIntent
                    )
                } else {
                    null
                }
            }.sortedBy { it.name }
        }
    }

    if (isNavVisible) {
        BackHandler {
            isNavVisible = false
        }
    }

    val channelsState by produceState<Result<List<TvChannel>>?>(initialValue = null, key1 = m3uLink) {
        if (m3uLink.isNotEmpty()) {
            // Try loading from cache first for instant UI
            val cached = withContext(Dispatchers.IO) { getCachedM3U(context, m3uLink) }
            if (cached != null) {
                value = Result.success(cached)
                Log.d("LaunchTV", "Loaded ${cached.size} channels from cache")
            }
            
            // Still fetch in background to refresh if cache was old or to initially load
            value = withContext(Dispatchers.IO) {
                try {
                    val result = parseM3U(m3uLink)
                    if (result.isEmpty()) {
                        if (value?.isSuccess == true) value else Result.failure(Exception("No channels found in playlist"))
                    } else {
                        saveCachedM3U(context, m3uLink, result)
                        Result.success(result)
                    }
                } catch (e: Exception) {
                    value ?: Result.failure(e)
                }
            }
        }
    }

    LaunchedEffect(channelsState, selectedSection) {
        if (selectedSection == NavSection.TV && playingUrl == null) {
            val channels = channelsState?.getOrNull()
            if (!channels.isNullOrEmpty()) {
                playingUrl = channels.first().url
            }
        }
    }

    LaunchedEffect(isNavVisible, selectedSection) {
        if (isNavVisible) {
            navHadFocus = false
            delay(50) // Short delay to ensure menu is composed before requesting focus
            try {
                navFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("LaunchTV", "Failed to focus nav", e)
            }
        } else {
            navHadFocus = false
            try {
                contentFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("LaunchTV", "Failed to request content focus", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!isNavVisible) {
            try {
                contentFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Left Navigation Panel
        if (isNavVisible) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(AppleGlass) // Use translucency
                    .onFocusChanged {
                        if (it.hasFocus) navHadFocus = true
                    }
                    .padding(top = 48.dp, start = 32.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "LaunchTV",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 40.dp, start = 16.dp),
                    color = Color.White
                )

                NavSection.entries.forEach { section ->
                    NavigationItem(
                        section = section,
                        isSelected = selectedSection == section,
                        onSelect = {
                            selectedSection = section
                        },
                        modifier = (if (selectedSection == section) Modifier.focusRequester(navFocusRequester) else Modifier)
                            .onKeyEvent {
                                if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) {
                                    selectedSection = section
                                    isNavVisible = false
                                    true
                                } else false
                            }
                    )
                }
            }
        }

        // 2. Right Content Area
        val isTVFullScreen = selectedSection == NavSection.TV && !isNavVisible
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .focusRequester(contentFocusRequester)
                .onFocusChanged {
                    if (it.hasFocus && isNavVisible && navHadFocus) {
                        isNavVisible = false
                        navHadFocus = false
                    }
                }
                .padding(
                    top = if (isTVFullScreen) 0.dp else 32.dp,
                    bottom = if (isTVFullScreen) 0.dp else 32.dp,
                    end = if (isTVFullScreen) 0.dp else 48.dp,
                    start = if (isNavVisible) 16.dp else if (isTVFullScreen) 0.dp else 48.dp
                )
        ) {
            Column {
                if (!isTVFullScreen) {
                    SectionHeader(selectedSection.name)
                }
                when (selectedSection) {
                    NavSection.Apps -> AppLauncherGrid(apps = apps, onExpandNav = { isNavVisible = true })
                    NavSection.TV -> {
                        if (playingUrl != null) {
                            VideoPlayer(
                                url = playingUrl!!,
                                m3uLink = m3uLink,
                                channelsState = channelsState,
                                onChannelSelect = { newUrl ->
                                    playingUrl = newUrl
                                    sharedPrefs.edit(commit = true) {
                                        putString("last_played_url", newUrl)
                                    }
                                },
                                onBack = {
                                    playingUrl = null
                                },
                                onExpandNav = { isNavVisible = true },
                                isNavVisible = isNavVisible
                            )
                        } else {
                            TvSection(
                                m3uLink = m3uLink,
                                channelsState = channelsState,
                                onExpandNav = { isNavVisible = true }
                            ) { url ->
                                playingUrl = url
                                sharedPrefs.edit(commit = true) {
                                    putString("last_played_url", url)
                                }
                            }
                        }
                    }
                    NavSection.Settings -> SettingsSection(
                        m3uLink = m3uLink,
                        onExpandNav = { isNavVisible = true },
                        onSave = { newValue ->
                            m3uLink = newValue
                            sharedPrefs.edit(commit = true) { 
                                putString("m3u_link", newValue) 
                            }
                            Log.d("LaunchTV", "Saved new M3U link and triggering reload")
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavigationItem(
    section: NavSection,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        selected = isSelected,
        onClick = onSelect,
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.6f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            selectedContainerColor = Color.White.copy(alpha = 0.15f),
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = Color.White,
            focusedSelectedContentColor = Color.Black
        ),
        modifier = modifier.fillMaxWidth(),
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Text(
            text = section.name,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(bottom = 32.dp),
        color = Color.White
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppLauncherGrid(apps: List<LaunchableApp>, onExpandNav: () -> Unit) {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(4), // 4 columns looks more like Apple TV
        contentPadding = PaddingValues(bottom = 64.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = apps,
            key = { it.packageName }
        ) { app ->
            val index = apps.indexOf(app)
            AppItem(
                app = app,
                modifier = Modifier.onKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown && index % 4 == 0) {
                        onExpandNav()
                        true
                    } else false
                }
            ) {
                context.startActivity(app.intent)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSection(
    m3uLink: String, 
    channelsState: Result<List<TvChannel>>?,
    modifier: Modifier = Modifier,
    selectedUrl: String? = null,
    onExpandNav: () -> Unit = {},
    onChannelClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val channels = channelsState?.getOrNull() ?: emptyList()
    val selectedIndex = remember(channels, selectedUrl) {
        channels.indexOfFirst { it.url == selectedUrl }
    }
    val selectedFocusRequester = remember { FocusRequester() }

    // Scroll to the selected channel and request focus
    LaunchedEffect(selectedIndex, channels) {
        if (selectedIndex >= 0 && channels.isNotEmpty()) {
            // Scroll so the selected item is not at the very top (better for focus/UI)
            val scrollIndex = (selectedIndex - 2).coerceAtLeast(0)
            listState.scrollToItem(scrollIndex)
            
            // Give a bit more time for TV layout to stabilize
            delay(150)
            try {
                selectedFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("LaunchTV", "Failed to focus selected channel", e)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onKeyEvent {
                if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                    onExpandNav()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            m3uLink.isEmpty() -> {
                Surface(
                    onClick = { /* Could open settings */ },
                    modifier = Modifier.padding(32.dp),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium)
                ) {
                    Text(
                        text = "Please add an M3U link in Settings to start watching TV",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            channelsState == null -> {
                Surface(
                    onClick = { /* Keep focusable */ },
                    modifier = Modifier.padding(32.dp),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        AppleLoadingIndicator()
                        Text(
                            "Loading channels...",
                            modifier = Modifier.padding(top = 84.dp),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            channelsState.isFailure -> {
                Surface(
                    onClick = { /* Could retry */ },
                    modifier = Modifier.padding(32.dp),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                    shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium)
                ) {
                    Text(
                        text = "Error: ${channelsState.exceptionOrNull()?.message ?: "Unknown error"}\n\nTap to retry or check settings",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 128.dp, top = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = channels,
                        key = { _, channel -> channel.url },
                        contentType = { _, _ -> "channel" }
                    ) { _, channel ->
                        val isSelected = channel.url == selectedUrl
                        ChannelItem(
                            channel = channel,
                            modifier = if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier,
                            isSelected = isSelected
                        ) {
                            onChannelClick(channel.url)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String, 
    m3uLink: String, 
    channelsState: Result<List<TvChannel>>?,
    onChannelSelect: (String) -> Unit, 
    onBack: () -> Unit,
    onExpandNav: () -> Unit,
    isNavVisible: Boolean = false
) {
    val context = LocalContext.current
    
    // Keep screen on while video player is active
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showOverlay by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    val videoFocusRequester = remember { FocusRequester() }

    // Parse URL and headers (IPTV standard uses | to separate headers)
    val (baseUri, headers) = remember(url) {
        val parts = url.split("|")
        val base = parts[0].trim()
        val h = mutableMapOf<String, String>()
        parts.drop(1).forEach { part ->
            val kv = part.split("=", limit = 2)
            if (kv.size == 2) {
                h[kv[0].trim()] = kv[1].trim()
            }
        }
        base to h
    }

    // Use a ref to access latest URL info in the player listener without recreating it
    val urlInfoRef = remember { mutableStateOf(baseUri to headers) }
    urlInfoRef.value = baseUri to headers
    
    LaunchedEffect(showOverlay, lastInteractionTime) {
        if (showOverlay) {
            delay(3000)
            showOverlay = false
        }
    }

    LaunchedEffect(showOverlay) {
        if (!showOverlay) {
            if (!isNavVisible) {
                videoFocusRequester.requestFocus()
            }
        }
    }

    BackHandler {
        if (showOverlay) {
            showOverlay = false
        } else {
            onBack()
        }
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            ))
        
        // Optimize extractors for IPTV (especially TS streams)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
                                 DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(3)) // Retry up to 3 times

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build().apply {
                val player = this
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isLoading = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) {
                            errorMessage = null
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("LaunchTV", "ExoPlayer Error: ${error.message} (Code: ${error.errorCode})", error)
                        
                        // Retry logic for UnrecognizedInputFormatException
                        if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                            error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) {
                            
                            val currentMediaItem = player.currentMediaItem
                            
                            if (currentMediaItem?.localConfiguration?.mimeType != MimeTypes.APPLICATION_M3U8) {
                                Log.d("LaunchTV", "Retrying with HLS MIME type...")
                                val (retryBaseUri, _) = urlInfoRef.value
                                val retryMediaItem = MediaItem.Builder()
                                    .setUri(retryBaseUri)
                                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                                    .build()
                                player.setMediaItem(retryMediaItem)
                                player.prepare()
                                return
                            }
                        }

                        isLoading = false
                        errorMessage = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network Connection Error"
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Source blocked or 404"
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Unsupported Video Format"
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Invalid Stream Format"
                            else -> "Cannot play this channel"
                        }
                    }
                })
            }
    }

    LaunchedEffect(url) {
        errorMessage = null
        isLoading = true
        
        // Very aggressive MIME type detection to handle IPTV redirectors
        val isHls = baseUri.contains("m3u8", ignoreCase = true) || 
                    baseUri.contains("/live/") || baseUri.contains("/stream/") || 
                    baseUri.contains("get.php") || baseUri.contains("playlist") ||
                    !baseUri.contains(".")
                    
        val isTs = baseUri.contains(".ts", ignoreCase = true)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            ) + headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })

        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or 
                                 DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)

        val mediaSource: MediaSource = when {
            isHls -> {
                HlsMediaSource.Factory(httpDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.Builder()
                        .setUri(baseUri)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build())
            }
            isTs -> {
                ProgressiveMediaSource.Factory(httpDataSourceFactory, extractorsFactory)
                    .createMediaSource(MediaItem.Builder()
                        .setUri(baseUri)
                        .setMimeType(MimeTypes.VIDEO_MP2T)
                        .build())
            }
            else -> {
                DefaultMediaSourceFactory(context, extractorsFactory)
                    .setDataSourceFactory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.Builder()
                        .setUri(baseUri)
                        .build())
            }
        }
            
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Video player isolated in its own Composable to minimize recomposition impact
        VideoSurface(exoPlayer = exoPlayer, onInteraction = {
            lastInteractionTime = System.currentTimeMillis()
            if (!showOverlay) showOverlay = true
        }, focusRequester = videoFocusRequester)

        if (isLoading && !showOverlay) {
            AppleLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (errorMessage != null && !showOverlay) {
            PlaybackErrorDisplay(errorMessage!!, Modifier.align(Alignment.Center))
        }

        // Overlay is layered on top
        if (showOverlay) {
            ChannelListOverlay(
                m3uLink = m3uLink,
                channelsState = channelsState,
                selectedUrl = url,
                onExpandNav = onExpandNav,
                onInteraction = { lastInteractionTime = System.currentTimeMillis() },
                onDismiss = { showOverlay = false },
                onChannelSelect = onChannelSelect
            )
        }
    }
}

@Composable
fun VideoSurface(
    exoPlayer: Player,
    onInteraction: () -> Unit,
    focusRequester: FocusRequester
) {
    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                keepScreenOn = true
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.DirectionLeft -> {
                            onInteraction()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    )
}

@Composable
fun AppleLoadingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .size(40.dp)
            .rotate(angle),
        contentAlignment = Alignment.Center
    ) {
        for (i in 0 until 8) {
            Box(
                modifier = Modifier
                    .rotate(i * 45f)
                    .align(Alignment.TopCenter)
                    .width(3.dp)
                    .height(10.dp)
                    .background(
                        Color.White.copy(alpha = (i + 1) / 8f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
fun PlaybackErrorDisplay(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Press UP/DOWN to change channel",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelListOverlay(
    m3uLink: String,
    channelsState: Result<List<TvChannel>>?,
    selectedUrl: String?,
    onExpandNav: () -> Unit,
    onInteraction: () -> Unit,
    onDismiss: () -> Unit,
    onChannelSelect: (String) -> Unit
) {
    var overlayHadFocus by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(350.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.8f),
                        Color.Black.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                )
            )
            .onKeyEvent { 
                onInteraction()
                if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        TvSection(
            m3uLink = m3uLink,
            channelsState = channelsState,
            selectedUrl = selectedUrl,
            onExpandNav = onExpandNav,
            modifier = Modifier
                .onFocusChanged {
                    if (it.hasFocus) {
                        overlayHadFocus = true
                    } else if (overlayHadFocus) {
                        onDismiss()
                    }
                }
        ) { newUrl ->
            onChannelSelect(newUrl)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelItem(
    channel: TvChannel, 
    modifier: Modifier = Modifier,
    isSelected: Boolean = false, 
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Pre-calculate gradient to avoid object creation during draw
    val focusGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color.White,
                Color.White.copy(alpha = 0.5f)
            )
        )
    }

    Surface(
        onClick = onClick,
        selected = isSelected,
        modifier = modifier
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { isFocused = it.isFocused },
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.04f), // Slightly reduced scale for stability
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (isFocused) Color.Black else Color.White.copy(alpha = 0.7f),
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color.Black,
            selectedContainerColor = Color.White.copy(alpha = 0.1f),
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = Color.Transparent,
            focusedSelectedContentColor = Color.Black
        ),
        shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = if (isFocused) Modifier.background(focusGradient) else Modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("TV", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun getCachedM3U(context: Context, url: String): List<TvChannel>? {
    val sharedPrefs = context.getSharedPreferences("M3UCache", Context.MODE_PRIVATE)
    val cachedUrl = sharedPrefs.getString("cached_url", null)
    if (cachedUrl == url) {
        val data = sharedPrefs.getString("cached_data", null)
        if (data != null) {
            return data.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 2) {
                    TvChannel(parts[0], parts[1], parts.getOrNull(2)?.ifEmpty { null })
                } else null
            }
        }
    }
    return null
}

private fun saveCachedM3U(context: Context, url: String, channels: List<TvChannel>) {
    val sharedPrefs = context.getSharedPreferences("M3UCache", Context.MODE_PRIVATE)
    val data = channels.joinToString("\n") { "${it.name}|${it.url}|${it.logoUrl ?: ""}" }
    sharedPrefs.edit().putString("cached_url", url).putString("cached_data", data).apply()
}

private fun parseM3U(url: String): List<TvChannel> {
    if (url == "internal://demo") {
        return listOf(
            TvChannel("Demo: Big Buck Bunny", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_Buck_Bunny_Figure.png/220px-Big_Buck_Bunny_Figure.png"),
            TvChannel("Demo: Elephants Dream", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Elephants_Dream_logo.svg/200px-Elephants_Dream_logo.svg.png"),
            TvChannel("Demo: For Bigger Blazes", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
            TvChannel("Demo: For Bigger Escapes", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
        )
    }

    Log.d("LaunchTV", "Starting to parse M3U from: $url")
    try {
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        val channels = mutableListOf<TvChannel>()
        var currentName = ""
        var currentLogo = ""

        connection.getInputStream().bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("#EXTINF:")) {
                    currentName = trimmedLine.substringAfterLast(",").trim()
                    
                    // Simple string search instead of Regex for performance
                    if (trimmedLine.contains("tvg-logo=\"")) {
                        currentLogo = trimmedLine.substringAfter("tvg-logo=\"").substringBefore("\"")
                    }
                } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                    if (currentName.isNotEmpty()) {
                        channels.add(TvChannel(currentName, trimmedLine, currentLogo.ifEmpty { null }))
                        currentName = ""
                        currentLogo = ""
                    } else if (trimmedLine.startsWith("http")) {
                        channels.add(TvChannel("Unnamed Channel", trimmedLine))
                    }
                }
            }
        }

        Log.d("LaunchTV", "Successfully parsed ${channels.size} channels")
        return channels
    } catch (e: Exception) {
        Log.e("LaunchTV", "Error parsing M3U", e)
        throw e
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsSection(m3uLink: String, onExpandNav: () -> Unit, onSave: (String) -> Unit) {
    var textValue by rememberSaveable { mutableStateOf(m3uLink) }
    val focusRequester = remember { FocusRequester() }
    
    Column(modifier = Modifier.fillMaxWidth(0.8f)) {
        Text("M3U Playlist URL", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            onClick = { focusRequester.requestFocus() },
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                        onExpandNav()
                        true
                    } else false
                },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppleLightGray,
                focusedContainerColor = Color.White
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            var isFocused by remember { mutableStateOf(false) }
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused },
                textStyle = TextStyle(
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 18.sp
                ),
                cursorBrush = SolidColor(if (isFocused) Color.Black else Color.White),
                decorationBox = { innerTextField ->
                    Box {
                        if (textValue.isEmpty()) {
                            Text(
                                "Enter M3U link here...",
                                color = if (isFocused) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsButton("Load Offline Demo", modifier = Modifier.onKeyEvent {
                if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                    onExpandNav()
                    true
                } else false
            }) { 
                val demoUrl = "internal://demo"
                textValue = demoUrl
                onSave(demoUrl)
            }

            SettingsButton("Load Sample URL") { 
                val sampleUrl = "https://raw.githubusercontent.com/ankitnaik1/Dot_Files/main/channels.m3u"
                textValue = sampleUrl
                onSave(sampleUrl)
            }

            SettingsButton("Save and Load", isPrimary = true) { 
                onSave(textValue) 
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsButton(
    text: String, 
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = if (isPrimary) Color.White.copy(alpha = 0.2f) else AppleLightGray,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = ButtonDefaults.shape(RoundedCornerShape(12.dp))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppItem(app: LaunchableApp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(200.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .aspectRatio(16f / 9f)
                .onFocusChanged { isFocused = it.isFocused },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = AppleLightGray,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.5f),
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
