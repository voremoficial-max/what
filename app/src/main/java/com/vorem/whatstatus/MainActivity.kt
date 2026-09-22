package com.vorem.whatstatus

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.vorem.whatstatus.domain.*
import com.vorem.whatstatus.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF090909)
private val Card = Color(0xFF171717)
private val Orange = Color(0xFFFF7A00)

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WhatStatusApp(vm) }
    }
}

@Composable
fun WhatStatusApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val statuses by vm.statuses.collectAsState()
    val saved by vm.saved.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var intro by remember { mutableStateOf(!vm.hasIntro()) }
    var viewer by remember { mutableStateOf<Uri?>(null) }
    var viewerKind by remember { mutableStateOf(MediaKind.IMAGE) }
    var viewerName by remember { mutableStateOf("") }

    // Back del sistema (botón o gesto) cierra el visor antes de salir de la app.
    BackHandler(enabled = viewer != null) { viewer = null }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.scan() }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setTreeUri(uri)
        }
    }

    LaunchedEffect(Unit) { vm.scan() }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Card,
            primary = Orange,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (intro) {
                Welcome(onStart = {
                    intro = false
                    vm.markIntro()
                    requestPermissions(permissionLauncher)
                })
            } else if (viewer != null) {
                MediaViewer(
                    uri = viewer!!,
                    kind = viewerKind,
                    title = viewerName,
                    onBack = { viewer = null },
                    onSave = {
                        statuses.firstOrNull { it.uri == viewer }?.let(vm::save)
                    }
                )
            } else {
                Scaffold(
                    bottomBar = { BottomBar(nav) },
                    containerColor = Bg
                ) { pad ->
                    NavHost(
                        navController = nav,
                        startDestination = "statuses",
                        modifier = Modifier.padding(pad)
                    ) {
                        composable("statuses") {
                            StatusScreen(
                                items = statuses,
                                busy = busy,
                                onRefresh = vm::scan,
                                onOpen = {
                                    viewer = it.uri
                                    viewerKind = it.kind
                                    viewerName = it.name
                                },
                                onSave = vm::save,
                                onGrant = { treeLauncher.launch(null) }
                            )
                        }
                        composable("saved") {
                            SavedScreen(
                                items = saved,
                                onOpen = {
                                    viewer = it.uri
                                    viewerKind = it.kind
                                    viewerName = it.name
                                },
                                onDelete = vm::deleteSaved
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                vm = vm,
                                onGrant = { treeLauncher.launch(null) },
                                onRequest = { requestPermissions(permissionLauncher) }
                            )
                        }
                    }
                }
            }

            LaunchedEffect(message) {
                if (message != null) {
                    delay(2200)
                    vm.clearMessage()
                }
            }
        }
    }
}

private fun requestPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    val permissions = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    launcher.launch(permissions)
}

@Composable
fun Welcome(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WhatStatus", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("By Vørem", color = Orange, fontSize = 16.sp)
        Spacer(Modifier.height(18.dp))
        Text("Guarda tus estados de WhatsApp fácilmente.", color = Color.LightGray)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Comenzar") }
    }
}

@Composable
fun BottomBar(nav: NavHostController) {
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = Color(0xFF101010)) {
        listOf(
            "statuses" to (Icons.Default.PhotoLibrary to "Estados"),
            "saved" to (Icons.Default.Bookmark to "Guardados"),
            "settings" to (Icons.Default.Settings to "Ajustes")
        ).forEach { (route, pair) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { nav.navigate(route) { launchSingleTop = true } },
                icon = { Icon(pair.first, null) },
                label = { Text(pair.second) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusScreen(
    items: List<StatusItem>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpen: (StatusItem) -> Unit,
    onSave: (StatusItem) -> Unit,
    onGrant: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("WhatStatus", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("By Vørem", fontSize = 13.sp, color = Orange)
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, "Actualizar")
            }
        }
        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(bottom = 8.dp),
                color = Orange,
                trackColor = Color(0xFF2A2A2A)
            )
        }
        if (items.isEmpty() && !busy) EmptyState(onGrant)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp)
        ) {
            items(items, key = { it.uri.toString() }) { item ->
                MediaCard(item, onOpen, onSave)
            }
        }
    }
}

@Composable
fun EmptyState(onGrant: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 45.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PhotoLibrary, null, tint = Orange, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text("No encontramos estados disponibles.", fontWeight = FontWeight.SemiBold)
            Text(
                "Si Android bloquea el acceso automático, concede acceso a la carpeta de estados.",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(15.dp))
            OutlinedButton(onClick = onGrant) { Text("Conceder acceso") }
        }
    }
}

@Composable
fun MediaCard(
    item: StatusItem,
    onOpen: (StatusItem) -> Unit,
    onSave: (StatusItem) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().aspectRatio(.82f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.kind == MediaKind.VIDEO) {
                VideoThumbnail(
                    uri = item.uri,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            }
            if (item.kind == MediaKind.VIDEO) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = .55f)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        "Video",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(30.dp)
                    )
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .66f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (item.saved) "Guardado" else "Estado",
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = { onOpen(item) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Fullscreen, "Abrir")
                }
                if (!item.saved) {
                    IconButton(onClick = { onSave(item) }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Download, "Guardar", tint = Orange)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(uri: Uri, contentDescription: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(640, 640), null)
                } else {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        retriever.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        retriever.release()
                    }
                }
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(0xFF222222)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = Orange)
        }
    }
}

@Composable
fun SavedScreen(
    items: List<SavedMedia>,
    onOpen: (SavedMedia) -> Unit,
    onDelete: (SavedMedia) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(14.dp)
    ) {
        Text("Guardados", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 15.dp))
        if (items.isEmpty()) {
            Text("Aún no tienes archivos guardados.", color = Color.Gray)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(items, key = { it.uri.toString() }) { SavedCard(it, onOpen, onDelete) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedCard(
    item: SavedMedia,
    onOpen: (SavedMedia) -> Unit,
    onDelete: (SavedMedia) -> Unit
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val anim by animateFloatAsState(offset, label = "swipe")
    Card(
        Modifier
            .fillMaxWidth()
            .aspectRatio(.82f)
            .offset(x = (anim / 3).dp)
            .pointerInput(item.uri) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offset < -180f) onDelete(item)
                        offset = 0f
                    },
                    onHorizontalDrag = { _, d -> offset = (offset + d).coerceIn(-320f, 320f) }
                )
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Card)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.kind == MediaKind.VIDEO) {
                VideoThumbnail(item.uri, item.name, Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(true).build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (item.kind == MediaKind.VIDEO) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = .55f)
                ) {
                    Icon(Icons.Default.PlayArrow, "Video", tint = Color.White, modifier = Modifier.padding(8.dp).size(30.dp))
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = .66f)).padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.name, maxLines = 1, fontSize = 10.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { onOpen(item) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Fullscreen, "Abrir")
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onGrant: () -> Unit, onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(20.dp)
    ) {
        Text("Ajustes", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Setting("Carpeta de estados", "Autoriza una carpeta si Android no permite detectar los estados automáticamente", onGrant)
        Setting("Permisos multimedia", "Solicitar de nuevo los permisos necesarios", onRequest)
        Setting("Escanear nuevamente", "Buscar estados nuevos", vm::scan)
        Spacer(Modifier.height(20.dp))
        Text("Acerca de WhatStatus", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("WhatStatus V1.0", modifier = Modifier.padding(top = 8.dp))
        Text("By Vørem", color = Orange)
        Spacer(Modifier.height(8.dp))
        Text("WhatStatus no está afiliado a WhatsApp ni a Meta.", color = Color.Gray, fontSize = 12.sp)
        Text("La aplicación funciona localmente y no sube tus archivos a servidores.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Setting(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, fontSize = 12.sp, color = Color.Gray) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    )
    HorizontalDivider(color = Color.DarkGray)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun MediaViewer(
    uri: Uri,
    kind: MediaKind,
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
            }
            Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, color = Color.White)
            IconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Download, "Guardar", tint = Orange)
            }
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            if (kind == MediaKind.IMAGE) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Video(uri)
            }
        }
    }
}

@Composable
fun Video(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        modifier = Modifier.fillMaxSize()
    )
}
