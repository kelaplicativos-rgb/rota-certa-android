package br.com.mapeiaia.rotacerta

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SafetyRecorderMode0666 { AUDIO, VIDEO }

data class SafetyRecorderConfig0666(
    val frontCamera: Boolean = false,
    val videoQuality: String = "FHD",
    val recordVideoAudio: Boolean = true,
)

data class SafetyRecordingItem0666(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val mode: SafetyRecorderMode0666,
    val createdAtMillis: Long,
)

object SafetyRecorderRuntime0666 {
    @Volatile var activeMode: SafetyRecorderMode0666? = null
}

object SafetyRecorderPrefs0666 {
    private const val PREFS = "rota_certa_safety_recorder_0666"
    private const val KEY_FRONT = "front_camera"
    private const val KEY_QUALITY = "video_quality"
    private const val KEY_VIDEO_AUDIO = "video_audio"
    private const val KEY_ACTIVE_MODE = "active_mode"
    private const val KEY_ACTIVE_SINCE = "active_since"

    fun config(context: Context): SafetyRecorderConfig0666 {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SafetyRecorderConfig0666(
            frontCamera = p.getBoolean(KEY_FRONT, false),
            videoQuality = p.getString(KEY_QUALITY, "FHD") ?: "FHD",
            recordVideoAudio = p.getBoolean(KEY_VIDEO_AUDIO, true),
        )
    }

    fun setFrontCamera(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FRONT, value).apply()
    }

    fun setVideoQuality(context: Context, value: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUALITY, value).apply()
    }

    fun setVideoAudio(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VIDEO_AUDIO, value).apply()
    }

    fun markActive(context: Context, mode: SafetyRecorderMode0666, since: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE_MODE, mode.name).putLong(KEY_ACTIVE_SINCE, since).apply()
    }

    fun clearActive(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ACTIVE_MODE).remove(KEY_ACTIVE_SINCE).apply()
    }

    fun activeMode(context: Context): SafetyRecorderMode0666? {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_MODE, null)
        return runCatching { raw?.let(SafetyRecorderMode0666::valueOf) }.getOrNull()
    }

    fun activeSince(context: Context): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_ACTIVE_SINCE, 0L)
}

object SafetyRecorderIndex0666 {
    private const val PREFS = "rota_certa_safety_recorder_index_0666"
    private const val KEY = "items"
    private const val LIMIT = 400

    @Synchronized
    fun read(context: Context, mode: SafetyRecorderMode0666? = null): List<SafetyRecordingItem0666> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val parsedMode = runCatching {
                        SafetyRecorderMode0666.valueOf(o.optString("mode"))
                    }.getOrNull() ?: continue
                    val u = o.optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: continue
                    val item = SafetyRecordingItem0666(
                        uri = u,
                        displayName = o.optString("displayName").ifBlank { "Registro" },
                        mimeType = o.optString("mimeType").ifBlank {
                            if (parsedMode == SafetyRecorderMode0666.AUDIO) "audio/mp4" else "video/mp4"
                        },
                        mode = parsedMode,
                        createdAtMillis = o.optLong("createdAtMillis"),
                    )
                    if (mode == null || parsedMode == mode) add(item)
                }
            }.sortedByDescending { it.createdAtMillis }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(context: Context, item: SafetyRecordingItem0666) {
        persist(context, (listOf(item) + read(context)).distinctBy { it.uri.toString() }.take(LIMIT))
    }

    @Synchronized
    fun remove(context: Context, uri: Uri) {
        persist(context, read(context).filterNot { it.uri == uri })
    }

    private fun persist(context: Context, items: List<SafetyRecordingItem0666>) {
        val a = JSONArray()
        items.forEach { item ->
            a.put(
                JSONObject()
                    .put("uri", item.uri.toString())
                    .put("displayName", item.displayName)
                    .put("mimeType", item.mimeType)
                    .put("mode", item.mode.name)
                    .put("createdAtMillis", item.createdAtMillis),
            )
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, a.toString()).apply()
    }
}

/**
 * Porta de entrada curta dos atalhos flutuantes.
 * Ela mantém uma Activity em primeiro plano apenas durante a concessão/partida para respeitar
 * as restrições de câmera e microfone do Android 14+; não tenta ocultar indicadores do sistema.
 */
class SafetyRecorderGatewayActivity0666 : ComponentActivity() {
    private lateinit var mode: SafetyRecorderMode0666

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = parseMode(intent)
        if (SafetyRecorderRuntime0666.activeMode == mode) {
            dispatchAndFinish(false)
            return
        }
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            dispatchAndFinish(true)
        } else {
            requestPermissions(missing.toTypedArray(), REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            dispatchAndFinish(true)
        } else {
            Toast.makeText(
                this,
                if (mode == SafetyRecorderMode0666.AUDIO) {
                    "Autorize o microfone para usar Fala."
                } else {
                    "Autorize a câmera e o microfone configurado para usar Cena."
                },
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (mode == SafetyRecorderMode0666.AUDIO) {
            add(Manifest.permission.RECORD_AUDIO)
        } else {
            add(Manifest.permission.CAMERA)
            if (SafetyRecorderPrefs0666.config(this@SafetyRecorderGatewayActivity0666).recordVideoAudio) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun dispatchAndFinish(waitForForegroundStart: Boolean) {
        ContextCompat.startForegroundService(this, SafetyRecorderService0666.toggleIntent(this, mode))
        if (!waitForForegroundStart) {
            finish()
        } else {
            lifecycleScope.launch {
                delay(1_250L)
                if (!isFinishing) finish()
            }
        }
    }

    private fun parseMode(intent: Intent?): SafetyRecorderMode0666 =
        runCatching {
            SafetyRecorderMode0666.valueOf(
                intent?.getStringExtra(EXTRA_MODE).orEmpty(),
            )
        }.getOrDefault(SafetyRecorderMode0666.AUDIO)

    companion object {
        const val EXTRA_MODE = "safety_recorder_mode_0666"
        private const val REQUEST_CODE = 6606

        fun intent(context: Context, mode: SafetyRecorderMode0666): Intent =
            Intent(context, SafetyRecorderGatewayActivity0666::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}

class SafetyRecorderService0666 : LifecycleService() {
    private data class AudioHandle(
        val recorder: MediaRecorder,
        val uri: Uri?,
        val pfd: ParcelFileDescriptor?,
        val legacyFile: File?,
        val displayName: String,
        val startedAt: Long,
    )

    private var currentMode: SafetyRecorderMode0666? = null
    private var nextMode: SafetyRecorderMode0666? = null
    private var audio: AudioHandle? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoRecording: Recording? = null
    private var videoName: String = ""
    private var videoStartedAt: Long = 0L
    private var generation: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        SafetyRecorderRuntime0666.activeMode = null
        SafetyRecorderPrefs0666.clearActive(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_AUDIO -> toggle(SafetyRecorderMode0666.AUDIO)
            ACTION_VIDEO -> toggle(SafetyRecorderMode0666.VIDEO)
            ACTION_STOP -> {
                nextMode = null
                stopCurrent()
            }
            else -> if (currentMode == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun toggle(requested: SafetyRecorderMode0666) {
        if (currentMode == requested) {
            nextMode = null
            stopCurrent()
            return
        }
        if (currentMode != null) {
            nextMode = requested
            stopCurrent()
            return
        }
        startMode(requested)
    }

    private fun startMode(requested: SafetyRecorderMode0666) {
        currentMode = requested
        nextMode = null
        generation += 1L
        val started = System.currentTimeMillis()
        SafetyRecorderRuntime0666.activeMode = requested
        SafetyRecorderPrefs0666.markActive(this, requested, started)

        val foregroundOk = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(requested, true),
                foregroundType(requested),
            )
        }.isSuccess
        if (!foregroundOk) {
            fail("O Android não permitiu iniciar o registro.")
            return
        }

        when (requested) {
            SafetyRecorderMode0666.AUDIO -> startAudio(started)
            SafetyRecorderMode0666.VIDEO -> startVideo(generation, started)
        }
    }

    private fun foregroundType(mode: SafetyRecorderMode0666): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        return when (mode) {
            SafetyRecorderMode0666.AUDIO -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            SafetyRecorderMode0666.VIDEO -> {
                var value = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                if (SafetyRecorderPrefs0666.config(this).recordVideoAudio) {
                    value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                value
            }
        }
    }

    private fun startAudio(started: Long) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Permissão de microfone ausente.")
            return
        }

        val displayName = "RotaCerta_Fala_" + timestamp(started) + ".m4a"
        var uri: Uri? = null
        var pfd: ParcelFileDescriptor? = null
        var legacy: File? = null
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/Rota Certa/Seguranca",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Falha ao criar mídia de áudio")
                pfd = contentResolver.openFileDescriptor(requireNotNull(uri), "w")
                    ?: error("Falha ao abrir mídia de áudio")
                recorder.setOutputFile(requireNotNull(pfd).fileDescriptor)
            } else {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Rota Certa/Seguranca")
                dir.mkdirs()
                legacy = File(dir, displayName)
                recorder.setOutputFile(requireNotNull(legacy).absolutePath)
            }
            recorder.prepare()
            recorder.start()
        }.onSuccess {
            audio = AudioHandle(recorder, uri, pfd, legacy, displayName, started)
            notifyState(SafetyRecorderMode0666.AUDIO, false)
            UnifiedDebugEventStore.record("SAFETY_AUDIO_STARTED_0666", packageName, "name=" + displayName)
        }.onFailure {
            runCatching { recorder.release() }
            runCatching { pfd?.close() }
            uri?.let { value -> runCatching { contentResolver.delete(value, null, null) } }
            legacy?.delete()
            fail("Não foi possível iniciar Fala.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVideo(expectedGeneration: Long, started: Long) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("Permissão de câmera ausente.")
            return
        }
        val config = SafetyRecorderPrefs0666.config(this)
        if (
            config.recordVideoAudio &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            fail("Permissão de microfone ausente para Cena.")
            return
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                if (currentMode != SafetyRecorderMode0666.VIDEO || expectedGeneration != generation) return@addListener
                runCatching {
                    val provider = future.get()
                    cameraProvider = provider
                    provider.unbindAll()
                    val quality = when (config.videoQuality) {
                        "HD" -> Quality.HD
                        "SD" -> Quality.SD
                        else -> Quality.FHD
                    }
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(quality))
                        .build()
                    val capture = VideoCapture.withOutput(recorder)
                    val selector = if (config.frontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    provider.bindToLifecycle(this, selector, capture)

                    videoName = "RotaCerta_Cena_" + timestamp(started) + ".mp4"
                    videoStartedAt = started
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, videoName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_MOVIES + "/Rota Certa/Seguranca",
                            )
                        }
                    }
                    val output = MediaStoreOutputOptions.Builder(
                        contentResolver,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    ).setContentValues(values).build()

                    var pending = capture.output.prepareRecording(this, output)
                    if (config.recordVideoAudio) pending = pending.withAudioEnabled()
                    videoRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                if (currentMode == SafetyRecorderMode0666.VIDEO) {
                                    notifyState(SafetyRecorderMode0666.VIDEO, false)
                                    UnifiedDebugEventStore.record(
                                        "SAFETY_VIDEO_STARTED_0666",
                                        packageName,
                                        "name=" + videoName + "; front=" + config.frontCamera +
                                            "; quality=" + config.videoQuality + "; audio=" + config.recordVideoAudio,
                                    )
                                }
                            }
                            is VideoRecordEvent.Finalize -> finishVideo(event, expectedGeneration)
                        }
                    }
                }.onFailure {
                    fail("Não foi possível iniciar Cena.")
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun stopCurrent() {
        when (currentMode) {
            SafetyRecorderMode0666.AUDIO -> stopAudio()
            SafetyRecorderMode0666.VIDEO -> stopVideo()
            null -> stopSelf()
        }
    }

    private fun stopAudio() {
        val handle = audio
        audio = null
        var success = false
        if (handle != null) {
            success = runCatching { handle.recorder.stop() }.isSuccess
            runCatching { handle.recorder.release() }
            runCatching { handle.pfd?.close() }
            if (success) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && handle.uri != null) {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    runCatching { contentResolver.update(handle.uri, values, null, null) }
                }
                val savedUri = handle.uri ?: handle.legacyFile?.let(Uri::fromFile)
                if (savedUri != null) {
                    SafetyRecorderIndex0666.add(
                        this,
                        SafetyRecordingItem0666(
                            savedUri,
                            handle.displayName,
                            "audio/mp4",
                            SafetyRecorderMode0666.AUDIO,
                            handle.startedAt,
                        ),
                    )
                }
                UnifiedDebugEventStore.record(
                    "SAFETY_AUDIO_STOPPED_0666",
                    packageName,
                    "name=" + handle.displayName,
                )
            } else {
                handle.uri?.let { value -> runCatching { contentResolver.delete(value, null, null) } }
                handle.legacyFile?.delete()
            }
        }
        completeCurrent()
    }

    private fun stopVideo() {
        val active = videoRecording
        if (active != null) {
            runCatching { active.stop() }.onFailure {
                videoRecording = null
                runCatching { cameraProvider?.unbindAll() }
                cameraProvider = null
                completeCurrent()
            }
        } else {
            generation += 1L
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
            completeCurrent()
        }
    }

    private fun finishVideo(event: VideoRecordEvent.Finalize, expectedGeneration: Long) {
        if (expectedGeneration != generation && currentMode != SafetyRecorderMode0666.VIDEO) return
        val uri = event.outputResults.outputUri
        videoRecording = null
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        if (!event.hasError() && uri != Uri.EMPTY) {
            SafetyRecorderIndex0666.add(
                this,
                SafetyRecordingItem0666(
                    uri,
                    videoName.ifBlank { "RotaCerta_Cena_" + timestamp(videoStartedAt) + ".mp4" },
                    "video/mp4",
                    SafetyRecorderMode0666.VIDEO,
                    videoStartedAt,
                ),
            )
            UnifiedDebugEventStore.record(
                "SAFETY_VIDEO_STOPPED_0666",
                packageName,
                "name=" + videoName,
            )
        } else if (uri != Uri.EMPTY) {
            runCatching { contentResolver.delete(uri, null, null) }
        }
        completeCurrent()
    }

    private fun completeCurrent() {
        currentMode = null
        SafetyRecorderRuntime0666.activeMode = null
        SafetyRecorderPrefs0666.clearActive(this)
        val queued = nextMode
        nextMode = null
        if (queued != null) {
            startMode(queued)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fail(message: String) {
        UnifiedDebugEventStore.record("SAFETY_RECORDER_FAILED_0666", packageName, message)
        currentMode = null
        nextMode = null
        SafetyRecorderRuntime0666.activeMode = null
        SafetyRecorderPrefs0666.clearActive(this)
        runCatching { cameraProvider?.unbindAll() }
        cameraProvider = null
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(mode: SafetyRecorderMode0666, preparing: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            6600 + mode.ordinal,
            SafetyRecorderActivity0666.intent(this, mode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            6699,
            Intent(this, SafetyRecorderService0666::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            preparing && mode == SafetyRecorderMode0666.AUDIO -> "Preparando Fala"
            preparing -> "Preparando Cena"
            mode == SafetyRecorderMode0666.AUDIO -> "Fala em andamento"
            else -> "Cena em andamento"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Rota Certa • registro")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Parar", stop)
            .build()
    }

    private fun notifyState(mode: SafetyRecorderMode0666, preparing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            notification(mode, preparing),
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Registros de segurança",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Indica quando Fala ou Cena está em uso."
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        runCatching { audio?.recorder?.stop() }
        runCatching { audio?.recorder?.release() }
        runCatching { audio?.pfd?.close() }
        runCatching { videoRecording?.stop() }
        runCatching { cameraProvider?.unbindAll() }
        SafetyRecorderRuntime0666.activeMode = null
        SafetyRecorderPrefs0666.clearActive(this)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "rota_certa_safety_recorder_0666"
        private const val NOTIFICATION_ID = 6606
        private const val ACTION_AUDIO = "br.com.mapeiaia.rotacerta.SAFETY_AUDIO_TOGGLE_0666"
        private const val ACTION_VIDEO = "br.com.mapeiaia.rotacerta.SAFETY_VIDEO_TOGGLE_0666"
        private const val ACTION_STOP = "br.com.mapeiaia.rotacerta.SAFETY_STOP_0666"

        fun toggleIntent(context: Context, mode: SafetyRecorderMode0666): Intent =
            Intent(context, SafetyRecorderService0666::class.java).setAction(
                if (mode == SafetyRecorderMode0666.AUDIO) ACTION_AUDIO else ACTION_VIDEO,
            )

        private fun timestamp(value: Long): String =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(value.coerceAtLeast(1L)))
    }
}

class SafetyRecorderActivity0666 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = parseMode(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface { SafetyRecorderScreen0666(initial) }
            }
        }
    }

    private fun parseMode(intent: Intent?): SafetyRecorderMode0666 =
        runCatching {
            SafetyRecorderMode0666.valueOf(
                intent?.getStringExtra(SafetyRecorderGatewayActivity0666.EXTRA_MODE).orEmpty(),
            )
        }.getOrDefault(SafetyRecorderMode0666.AUDIO)

    companion object {
        fun intent(context: Context, mode: SafetyRecorderMode0666): Intent =
            Intent(context, SafetyRecorderActivity0666::class.java)
                .putExtra(SafetyRecorderGatewayActivity0666.EXTRA_MODE, mode.name)
    }
}

@Composable
private fun SafetyRecorderScreen0666(initialMode: SafetyRecorderMode0666) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(initialMode) }
    var config by remember { mutableStateOf(SafetyRecorderPrefs0666.config(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf(emptyList<SafetyRecordingItem0666>()) }
    val selected = remember { mutableStateListOf<String>() }

    LaunchedEffect(mode, refresh) {
        items = SafetyRecorderIndex0666.read(context, mode)
        selected.retainAll(items.map { it.uri.toString() }.toSet())
    }

    Column(
        modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Registros de segurança", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Toque curto em Fala ou Cena inicia/encerra. Segurar abre este módulo. " +
                "O Rota Certa preserva as permissões e os indicadores de câmera/microfone do Android.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == SafetyRecorderMode0666.AUDIO) {
                Button(onClick = {}) { Text("🗣️ Fala") }
                OutlinedButton(onClick = { mode = SafetyRecorderMode0666.VIDEO; selected.clear() }) {
                    Text("🎬 Cena")
                }
            } else {
                OutlinedButton(onClick = { mode = SafetyRecorderMode0666.AUDIO; selected.clear() }) {
                    Text("🗣️ Fala")
                }
                Button(onClick = {}) { Text("🎬 Cena") }
            }
        }

        Button(
            onClick = { context.startActivity(SafetyRecorderGatewayActivity0666.intent(context, mode)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (SafetyRecorderPrefs0666.activeMode(context) == mode) {
                    "Parar " + if (mode == SafetyRecorderMode0666.AUDIO) "Fala" else "Cena"
                } else {
                    "Iniciar " + if (mode == SafetyRecorderMode0666.AUDIO) "Fala" else "Cena"
                },
            )
        }

        if (mode == SafetyRecorderMode0666.VIDEO) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configuração da Cena", fontWeight = FontWeight.Bold)
                    Text("Câmera padrão")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!config.frontCamera) {
                            Button(onClick = {}) { Text("Traseira") }
                            OutlinedButton(onClick = {
                                SafetyRecorderPrefs0666.setFrontCamera(context, true)
                                config = SafetyRecorderPrefs0666.config(context)
                            }) { Text("Frontal") }
                        } else {
                            OutlinedButton(onClick = {
                                SafetyRecorderPrefs0666.setFrontCamera(context, false)
                                config = SafetyRecorderPrefs0666.config(context)
                            }) { Text("Traseira") }
                            Button(onClick = {}) { Text("Frontal") }
                        }
                    }
                    Text("Qualidade")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("FHD", "HD", "SD").forEach { quality ->
                            if (config.videoQuality == quality) {
                                Button(onClick = {}) { Text(quality) }
                            } else {
                                OutlinedButton(onClick = {
                                    SafetyRecorderPrefs0666.setVideoQuality(context, quality)
                                    config = SafetyRecorderPrefs0666.config(context)
                                }) { Text(quality) }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Áudio junto do vídeo")
                        Switch(
                            checked = config.recordVideoAudio,
                            onCheckedChange = {
                                SafetyRecorderPrefs0666.setVideoAudio(context, it)
                                config = SafetyRecorderPrefs0666.config(context)
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Text(
            if (mode == SafetyRecorderMode0666.AUDIO) "Áudios salvos" else "Vídeos salvos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        if (items.isEmpty()) {
            Text("Nenhum registro deste tipo ainda.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    selected.clear()
                    selected.addAll(items.map { it.uri.toString() })
                }) { Text("Selecionar todos") }
                OutlinedButton(onClick = { selected.clear() }) { Text("Limpar") }
            }
            Button(
                onClick = {
                    shareSafetyRecordings0666(context, items.filter { it.uri.toString() in selected })
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Compartilhar selecionados") }

            items.forEach { item ->
                SafetyRecordingRow0666(
                    item = item,
                    selected = item.uri.toString() in selected,
                    onSelectedChange = { checked ->
                        val key = item.uri.toString()
                        if (checked && key !in selected) selected.add(key)
                        if (!checked) selected.remove(key)
                    },
                    onOpen = { openSafetyRecording0666(context, item) },
                    onShare = { shareSafetyRecordings0666(context, listOf(item)) },
                    onDelete = {
                        val ok = runCatching {
                            context.contentResolver.delete(item.uri, null, null)
                            true
                        }.getOrDefault(false)
                        if (ok) {
                            SafetyRecorderIndex0666.remove(context, item.uri)
                            selected.remove(item.uri.toString())
                            refresh += 1
                        } else {
                            Toast.makeText(context, "Não foi possível excluir.", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Novos áudios: Music/Rota Certa/Seguranca. Novos vídeos: Movies/Rota Certa/Seguranca.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SafetyRecordingRow0666(
    item: SafetyRecordingItem0666,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = selected, onCheckedChange = onSelectedChange)
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
                            .format(Date(item.createdAtMillis)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) {
                    Text(if (item.mode == SafetyRecorderMode0666.AUDIO) "Ouvir" else "Abrir")
                }
                OutlinedButton(onClick = onShare) { Text("Enviar") }
                OutlinedButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

private fun openSafetyRecording0666(context: Context, item: SafetyRecordingItem0666) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, item.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Nenhum aplicativo disponível para abrir.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSafetyRecordings0666(context: Context, items: List<SafetyRecordingItem0666>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { it.uri })
    val type = when {
        items.all { it.mode == SafetyRecorderMode0666.AUDIO } -> "audio/*"
        items.all { it.mode == SafetyRecorderMode0666.VIDEO } -> "video/*"
        else -> "*/*"
    }
    val share = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).setType(type)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(share, "Compartilhar registro"))
    }.onFailure {
        Toast.makeText(context, "Não foi possível abrir o compartilhamento.", Toast.LENGTH_SHORT).show()
    }
}

// safety_recorder_0_1_666
