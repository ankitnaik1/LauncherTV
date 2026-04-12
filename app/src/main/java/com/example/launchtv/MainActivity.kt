package com.example.launchtv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
            }
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Left Navigation Panel
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
                        onSelect = { selectedSection = section }
                    )
                }
            }

            // 2. Right Content Area
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(top = 32.dp, bottom = 32.dp, end = 48.dp, start = 16.dp)
            ) {
                Column {
                    SectionHeader(selectedSection.name)
                    when (selectedSection) {
                        NavSection.Apps -> AppLauncherGrid()
                        NavSection.TV -> TvSection(
                            m3uLink = m3uLink,
                            channelsState = channelsState
                        ) { url ->
                            playingUrl = url
                            sharedPrefs.edit(commit = true) {
                                putString("last_played_url", url)
                            }
                        }
                        NavSection.Settings -> SettingsSection(
                            m3uLink = m3uLink,
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
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavigationItem(
    section: NavSection,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        selected = isSelected,
        onClick = onSelect,
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
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
fun AppLauncherGrid() {
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
                    LaunchableApp(
                        name = resolveInfo.loadLabel(pm).toString(),
                        packageName = packageName,
                        icon = resolveInfo.loadIcon(pm).toBitmap().asImageBitmap(),
                        intent = launchIntent
                    )
                } else {
                    null
                }
            }.sortedBy { it.name }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4), // Reduced from 6 for better visibility on non-4K screens
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(apps) { app ->
            AppItem(app) {
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
    onChannelClick: (String) -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            m3uLink.isEmpty() -> {
                Text("Please add an M3U link in Settings to start watching TV")
            }
            channelsState == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Loading channels...")
                }
            }
            channelsState.isFailure -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Error: ${channelsState.exceptionOrNull()?.message ?: "Unknown error"}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
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
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels) { channel ->
                        ChannelItem(channel, isSelected = channel.url == selectedUrl) {
                            onChannelClick(channel.url)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(
    url: String, 
    m3uLink: String, 
    channelsState: Result<List<TvChannel>>?,
    onChannelSelect: (String) -> Unit, 
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(false) }
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
        ExoPlayer.Builder(context).build().apply {
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(url) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
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
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter -> {
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

        if (showOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(350.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .align(Alignment.CenterStart)
            ) {
                TvSection(
                    m3uLink = m3uLink, 
                    channelsState = channelsState,
                    selectedUrl = url,
                    modifier = Modifier.focusRequester(listFocusRequester)
                ) { newUrl ->
                    onChannelSelect(newUrl)
                    showOverlay = false
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
            videoFocusRequester.requestFocus()
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
            .padding(vertical = 6.dp, horizontal = 12.dp)
            .fillMaxWidth()
            .height(90.dp),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer
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
                        .size(64.dp)
                        .padding(end = 16.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Placeholder for missing logo
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
                    // Extract name
                    currentName = trimmedLine.substringAfterLast(",").trim()
                    // Extract logo: tvg-logo="http://..."
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
fun SettingsSection(m3uLink: String, onSave: (String) -> Unit) {
    var textValue by rememberSaveable { mutableStateOf(m3uLink) }
    val focusRequester = remember { FocusRequester() }
    
    Column {
        Text("M3U Playlist URL", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            onClick = { focusRequester.requestFocus() },
            modifier = Modifier.fillMaxWidth(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                modifier = Modifier.padding(end = 16.dp),
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
fun AppItem(app: LaunchableApp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(12.dp)
            .aspectRatio(1f),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp).fillMaxSize()
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.name,
                modifier = Modifier.size(64.dp).weight(1f),
                contentScale = ContentScale.Fit
            )
            Text(
                text = app.name,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )
        }
    }
}
