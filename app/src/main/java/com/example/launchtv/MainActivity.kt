package com.example.launchtv

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import com.example.launchtv.ui.theme.LaunchTVTheme
import kotlinx.coroutines.Dispatchers
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

    if (isNavVisible) {
        BackHandler {
            isNavVisible = false
        }
    }

    val channelsState by produceState<Result<List<TvChannel>>?>(initialValue = null, key1 = m3uLink) {
        if (m3uLink.isNotEmpty()) {
            value = null // Set to loading
            value = withContext(Dispatchers.IO) {
                try {
                    val result = parseM3U(m3uLink)
                    if (result.isEmpty()) {
                        Result.failure(Exception("No channels found in playlist"))
                    } else {
                        Result.success(result)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
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
            navFocusRequester.requestFocus()
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
                    .width(240.dp)
                    .padding(top = 32.dp, start = 32.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "LaunchTV",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp, start = 16.dp)
                )

                NavSection.entries.forEach { section ->
                    NavigationItem(
                        section = section,
                        isSelected = selectedSection == section,
                        onSelect = {
                            selectedSection = section
                        },
                        modifier = if (selectedSection == section) Modifier.focusRequester(navFocusRequester) else Modifier
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
                    if (it.hasFocus && isNavVisible) {
                        isNavVisible = false
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
                    NavSection.Apps -> AppLauncherGrid(onExpandNav = { isNavVisible = true })
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
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            selectedContentColor = MaterialTheme.colorScheme.secondary,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primary,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.fillMaxWidth(),
        shape = SelectableSurfaceDefaults.shape(MaterialTheme.shapes.small)
    ) {
        Text(
            text = section.name,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 24.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppLauncherGrid(onExpandNav: () -> Unit) {
    val context = LocalContext.current
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(bottom = 64.dp, top = 16.dp),
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
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown && index % 3 == 0) {
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
                        CircularProgressIndicator()
                        Text(
                            "Loading channels...",
                            modifier = Modifier.padding(top = 64.dp)
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
                val channels = channelsState.getOrNull() ?: emptyList()
                val listState = rememberLazyListState()
                val selectedIndex = remember(channels, selectedUrl) {
                    channels.indexOfFirst { it.url == selectedUrl }.coerceAtLeast(0)
                }

                LaunchedEffect(selectedIndex) {
                    if (selectedIndex >= 0) {
                        listState.scrollToItem(selectedIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 128.dp, top = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = channels,
                        key = { it.url }
                    ) { channel ->
                        ChannelItem(channel, isSelected = channel.url == selectedUrl) {
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
    var showOverlay by remember { mutableStateOf(false) }
    var overlayHadFocus by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val videoFocusRequester = remember { FocusRequester() }
    val listFocusRequester = remember { FocusRequester() }
    
    BackHandler {
        if (showOverlay) {
            showOverlay = false
        } else {
            onBack()
        }
    }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isLoading = (state == Player.STATE_BUFFERING)
                        if (state == Player.STATE_READY) {
                            errorMessage = null
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("LaunchTV", "ExoPlayer Error: ${error.message}", error)
                        isLoading = false
                        errorMessage = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network Connection Error"
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Source blocked or 404"
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Unsupported Video Format"
                            else -> "Cannot play this channel"
                        }
                    }
                })
            }
    }

    LaunchedEffect(url) {
        errorMessage = null
        isLoading = true
        
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(
                if (url.contains("m3u8", ignoreCase = true)) {
                    MimeTypes.APPLICATION_M3U8
                } else if (url.contains(".ts", ignoreCase = true)) {
                    "video/mp2t"
                } else {
                    null
                }
            )
            .build()
            
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(350.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                ) {
                    TvSection(
                        m3uLink = m3uLink, 
                        channelsState = channelsState,
                        selectedUrl = url,
                        onExpandNav = onExpandNav,
                        modifier = Modifier
                            .focusRequester(listFocusRequester)
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    overlayHadFocus = true
                                } else {
                                    if (overlayHadFocus) {
                                        showOverlay = false
                                    }
                                }
                            }
                    ) { newUrl ->
                        onChannelSelect(newUrl)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.DirectionLeft -> {
                                    if (!showOverlay) {
                                        showOverlay = true
                                        true
                                    } else {
                                        false
                                    }
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .focusRequester(videoFocusRequester)
                    .focusable()
            ) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading && !showOverlay) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (errorMessage != null && !showOverlay) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Press UP/DOWN to change channel",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            try {
                listFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.e("LaunchTV", "Failed to request list focus", e)
            }
        } else {
            overlayHadFocus = false
            if (!isNavVisible) {
                videoFocusRequester.requestFocus()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelItem(channel: TvChannel, isSelected: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        selected = isSelected,
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .fillMaxWidth()
            .height(80.dp),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            selectedContentColor = MaterialTheme.colorScheme.secondary,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primary,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = SelectableSurfaceDefaults.shape(MaterialTheme.shapes.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!channel.logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 16.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("TV", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        val content = connection.getInputStream().bufferedReader().use { it.readText() }
        Log.d("LaunchTV", "Fetched content length: ${content.length}")
        
        val channels = mutableListOf<TvChannel>()
        var currentName = ""
        var currentLogo = ""

        content.lines().forEach { line ->
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("#EXTINF:") -> {
                    currentName = trimmedLine.substringAfterLast(",").trim()
                    val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmedLine)
                    currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                }
                trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
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
    
    Column {
        Text("M3U Playlist URL", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = { focusRequester.requestFocus() },
            modifier = Modifier
                .fillMaxWidth()
                .onKeyEvent {
                    if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                        onExpandNav()
                        true
                    } else false
                },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            )
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (textValue.isEmpty()) {
                            Text(
                                "Enter M3U link here...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { 
                    val demoUrl = "internal://demo"
                    textValue = demoUrl
                    onSave(demoUrl)
                },
                modifier = Modifier
                    .padding(end = 16.dp)
                    .onKeyEvent {
                        if (it.key == Key.DirectionLeft && it.type == KeyEventType.KeyDown) {
                            onExpandNav()
                            true
                        } else false
                    },
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text("Load Offline Demo")
            }

            Button(
                onClick = { 
                    val sampleUrl = "https://raw.githubusercontent.com/ankitnaik1/Dot_Files/main/channels.m3u"
                    textValue = sampleUrl
                    onSave(sampleUrl)
                },
                modifier = Modifier.padding(end = 16.dp),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text("Load Sample URL")
            }

            Button(
                onClick = { onSave(textValue) },
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text("Save and Load")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppItem(app: LaunchableApp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(12.dp)
            .aspectRatio(16f / 9f),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium)
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
}
