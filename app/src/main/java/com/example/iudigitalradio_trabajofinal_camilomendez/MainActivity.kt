package com.example.iudigitalradio_trabajofinal_camilomendez

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.iudigitalradio_trabajofinal_camilomendez.ui.theme.*

// ==============================================================
// MODELO DE DATOS DE LA EMISORA
// ==============================================================
data class RadioStation(
    val id: Int,
    val name: String,
    val dial: String,
    val genre: String,
    val streamUrl: String
)

val sampleStations = listOf(
    RadioStation(1, "IU Digital Estéreo", "104.5 FM", "Institucional / Academia", "https://stream.zeno.fm/f3wvbbqmdg8uv"),
    RadioStation(2, "Radiónica", "99.1 FM", "Rock / Alternativa", "https://stream.zeno.fm/f3wvbbqmdg8uv"),
    RadioStation(3, "Radio Nacional de Colombia", "95.9 FM", "Cultural / Noticias", "https://s36.myradiostream.com:7480/;"),
    RadioStation(4, "La Mega Medellín", "90.3 FM", "Juvenil / Pop Latino", "https://stream.radiocaroline.net/;"),
    RadioStation(5, "Caracol Radio", "100.9 FM", "Noticias / Opinión", "https://stream-01.aiir.com/evbqbacipzavv?zt=eyJhbGciOiJIUzI1NiJ9.eyJzdHJlYW0iOiJldmJxYmFjaXB6YXZ2IiwiaG9zdCI6InN0cmVhbS0wMS5haWlyLmNvbSIsInJ0dGwiOjUsImp0aSI6ImxMRXk0RWRwU3NTV1kxUURsVDJaX1EiLCJpYXQiOjE3OTAyNjI3OTcsImV4cCI6MTc5MDI2Mjg1N30.Gf2LsXULt1T-SvnW6rByBgil7kbruaGt6k8h9pZ6kjQ")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IUDigitalRadioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RadioApp()
                }
            }
        }
    }
}

// ==============================================================
// RF-05: RETROALIMENTACIÓN HÁPTICA (Vibración del Sistema)
// ==============================================================
enum class HapticType {
    CLICK,       // Vibración suave para botones generales
    STATION,     // Vibración rápida al cambiar de emisora FM
    MUTE         // Vibración doble para silenciar / activar el volumen
}

fun triggerHapticFeedback(context: Context, type: HapticType = HapticType.CLICK) {
    val durationMs: Long
    val pattern: LongArray?

    when (type) {
        HapticType.CLICK -> {
            durationMs = 40
            pattern = null
        }
        HapticType.STATION -> {
            durationMs = 60
            pattern = null
        }
        HapticType.MUTE -> {
            durationMs = 100
            pattern = longArrayOf(0, 40, 50, 40) // Pulso doble (vibra 40ms, pausa 50ms, vibra 40ms)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        val vibrator = vibratorManager?.defaultVibrator
        if (pattern != null) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (pattern != null) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            if (pattern != null) {
                vibrator?.vibrate(pattern, -1)
            } else {
                vibrator?.vibrate(durationMs)
            }
        }
    }
}

// ==============================================================
// COMPONENTE PRINCIPAL (Orquestador de Estado y Hardware)
// ==============================================================
@Composable
fun RadioApp() {
    val context = LocalContext.current

    // RF-04: Estados reactivos preservados ante rotación
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    var selectedStationIndex by rememberSaveable { mutableStateOf(0) }
    var userPhoto by rememberSaveable { mutableStateOf<Bitmap?>(null) }

    val currentStation = sampleStations.getOrElse(selectedStationIndex) { sampleStations.first() }

    // RF-07: Media3 ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(selectedStationIndex) {
        val mediaItem = MediaItem.fromUri(currentStation.streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (isPlaying) {
            exoPlayer.play()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // RF-02 y RF-03: Lanzador de Cámara y Permisos
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            userPhoto = bitmap
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Permiso de cámara requerido para actualizar perfil", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleCameraRequest() {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Barra Superior Institucional
        TopBrandBar()

        // Contenedor con scroll vertical de las 3 secciones
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // SECCIÓN 1: Perfil de usuario sobre fondo del Campus IU Digital
            UserProfileHeroSection(
                userPhoto = userPhoto,
                onCameraClick = {
                    triggerHapticFeedback(context, HapticType.CLICK)
                    handleCameraRequest()
                }
            )

            // SECCIÓN 2: Tarjeta del Reproductor Principal con Ondas Animadas
            MainPlayerCardSection(
                station = currentStation,
                isPlaying = isPlaying,
                isMuted = isMuted,
                onPlayPauseToggle = {
                    triggerHapticFeedback(context, HapticType.CLICK)
                    isPlaying = !isPlaying
                },
                onMuteToggle = {
                    triggerHapticFeedback(context, HapticType.MUTE)
                    isMuted = !isMuted
                }
            )

            // SECCIÓN 3: Catálogo de Emisoras Disponibles
            StationsCatalogSection(
                stations = sampleStations,
                selectedIndex = selectedStationIndex,
                onSelectStation = { index ->
                    triggerHapticFeedback(context, HapticType.STATION)
                    selectedStationIndex = index
                    isPlaying = true
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==============================================================
// BARRA SUPERIOR INSTITUCIONAL (Logo y Título)
// ==============================================================
@Composable
fun TopBrandBar() {
    Surface(
        color = IUNavyDark,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Logo circular institucional
                Image(
                    painter = painterResource(id = R.drawable.logo_iudigital_circle),
                    contentDescription = "Logo IU Digital",
                    modifier = Modifier.size(38.dp)
                )

                Column {
                    Text(
                        text = "IU DIGITAL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Radio Universitaria",
                        style = MaterialTheme.typography.labelSmall,
                        color = IUGoldAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Indicador de transmisión en vivo
            Surface(
                color = IURedAccent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, IURedAccent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(IURedAccent)
                    )
                    Text(
                        text = "EN LÍNEA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==============================================================
// SECCIÓN 1: PERFIL DE USUARIO CON FONDO DE CAMPUS (RF-02, RF-03)
// ==============================================================
@Composable
fun UserProfileHeroSection(
    userPhoto: Bitmap?,
    onCameraClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
        ) {
            // Fondo fotográfico del Campus IU Digital
            Image(
                painter = painterResource(id = R.drawable.campus_iudigital),
                contentDescription = "Campus IU Digital",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Máscara oscura con gradiente institucional para legibilidad
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                IUNavyDark.copy(alpha = 0.92f),
                                IUNavyPrimary.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Contenido del Perfil
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Avatar con anillo de estilo institucional
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(IURedAccent, IUGoldAccent, IUTechCyan, IUNavyPrimary, IURedAccent)
                                ),
                                shape = CircleShape
                            )
                            .background(IUNavyDark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userPhoto != null) {
                            Image(
                                bitmap = userPhoto.asImageBitmap(),
                                contentDescription = "Foto de perfil tomada con cámara",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Estudiante IU Digital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (userPhoto != null) "Foto de perfil actualizada" else "Presiona para capturar foto",
                            style = MaterialTheme.typography.bodySmall,
                            color = IUGoldAccent
                        )
                    }
                }

                // Botón de captura con icono de cámara
                FilledIconButton(
                    onClick = onCameraClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = IUTechCyan
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Tomar foto con la cámara",
                        tint = IUNavyDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ==============================================================
// SECCIÓN 2: REPRODUCTOR PRINCIPAL (RF-04, RF-05, RF-07)
// ==============================================================
@Composable
fun MainPlayerCardSection(
    station: RadioStation,
    isPlaying: Boolean,
    isMuted: Boolean,
    onPlayPauseToggle: () -> Unit,
    onMuteToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = IUNavyContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, IUBorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(IUNavyPrimary.copy(alpha = 0.5f), IUNavyDark)
                    )
                )
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fila superior con Dial y Ondas Animadas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge de Dial / Frecuencia
                Surface(
                    color = IUGoldAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IUGoldAccent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = station.dial,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = IUGoldAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Ecualizador animado institucional
                AnimatedAudioEqualizer(isPlaying = isPlaying)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nombre de la emisora
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            // Género y estado
            Text(
                text = "${station.genre} • ${if (isPlaying) "Sintonizando Audio" else "Pausado"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isPlaying) IUTechCyanLight else IUTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fila de Controles Interactivos con Retroalimentación Háptica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón MUTE
                OutlinedIconButton(
                    onClick = onMuteToggle,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isMuted) IURedAccent else IUBorderSubtle
                    ),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute",
                        tint = if (isMuted) IURedAccent else Color.White
                    )
                }

                // Botón PLAY / PAUSE (Principal)
                FilledIconButton(
                    onClick = onPlayPauseToggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) IURedAccent else IUTechCyan
                    ),
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = if (isPlaying) Color.White else IUNavyDark,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Botón de Estado / Señal
                Surface(
                    shape = CircleShape,
                    color = IUSurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IUBorderSubtle),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = "Radio",
                            tint = if (isPlaying) IUGoldAccent else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================
// ANIMACIÓN DEL ECUALIZADOR DE ONDAS DE AUDIO
// ==============================================================
@Composable
fun AnimatedAudioEqualizer(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 22f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 18f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 26f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 24f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "b4"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(28.dp)
    ) {
        val bars = listOf(
            Pair(if (isPlaying) h1.dp else 4.dp, IURedAccent),
            Pair(if (isPlaying) h2.dp else 6.dp, IUGoldAccent),
            Pair(if (isPlaying) h3.dp else 4.dp, IUTechCyan),
            Pair(if (isPlaying) h4.dp else 5.dp, IUTechCyanLight)
        )

        bars.forEach { (height, color) ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

// ==============================================================
// SECCIÓN 3: CATÁLOGO DINÁMICO DE EMISORAS (RF-06)
// ==============================================================
@Composable
fun StationsCatalogSection(
    stations: List<RadioStation>,
    selectedIndex: Int,
    onSelectStation: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Emisoras Disponibles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${stations.size} canales",
                style = MaterialTheme.typography.labelSmall,
                color = IUTextSecondary
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(stations) { index, station ->
                val isSelected = (index == selectedIndex)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) IUSurfaceCard else IUSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) IUTechCyan else IUBorderSubtle
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectStation(index) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) IUNavyPrimary else IUNavyDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.GraphicEq else Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = if (isSelected) IUGoldAccent else IUTextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = station.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else IUTextSecondary
                                )
                                Text(
                                    text = "${station.dial} • ${station.genre}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) IUTechCyanLight else IUTextMuted
                                )
                            }
                        }

                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IUTechCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, IUTechCyan)
                            ) {
                                Text(
                                    text = "EN VIVO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IUTechCyanLight,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================
// PREVIEW PARA ANDROID STUDIO
// ==============================================================
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RadioAppPreview() {
    IUDigitalRadioTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            RadioApp()
        }
    }
}
