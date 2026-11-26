package com.example.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.model.TransferDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TransferForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isForegroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                closeService()
                return START_NOT_STICKY
            }
            ACTION_START, ACTION_UPDATE, null -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Max Share"
                val content = intent?.getStringExtra(EXTRA_CONTENT) ?: "Transferring files"
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0)?.coerceIn(0, 100) ?: 0
                val isReceiving = intent?.getBooleanExtra(EXTRA_IS_RECEIVING, false) ?: false

                acquireLocks()
                postNotification(
                    title = title,
                    content = content,
                    progress = progress,
                    isReceiving = isReceiving
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        stopForegroundCompat()
        _isRunning.value = false
        super.onDestroy()
    }

    private fun closeService() {
        releaseLocks()
        stopForegroundCompat()
        _isRunning.value = false
        stopSelf()
    }

    private fun postNotification(
        title: String,
        content: String,
        progress: Int,
        isReceiving: Boolean
    ) {
        val notification = buildNotification(
            title = title,
            content = content,
            progress = progress,
            isReceiving = isReceiving
        )
        if (!isForegroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundStarted = true
            _isRunning.value = true
        } else {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (isForegroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shows progress while sending or receiving files in background"
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        isReceiving: Boolean
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = if (isReceiving) {
            android.R.drawable.stat_sys_download
        } else {
            android.R.drawable.stat_sys_upload
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MaxShare:TransferWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire()
            }
        } catch (_: Exception) {}

        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "MaxShare:TransferWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
        wifiLock = null
    }

    companion object {
        private const val CHANNEL_ID = "max_share_transfer_fg"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_START = "com.example.transfer.action.START"
        private const val ACTION_UPDATE = "com.example.transfer.action.UPDATE"
        private const val ACTION_STOP = "com.example.transfer.action.STOP"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_CONTENT = "extra_content"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_IS_RECEIVING = "extra_is_receiving"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(
            context: Context,
            direction: TransferDirection,
            content: String,
            progressPercent: Int
        ) {
            val isReceiving = direction == TransferDirection.RECEIVE
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, if (isReceiving) "Receiving files" else "Sending files")
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_PROGRESS, progressPercent)
                putExtra(EXTRA_IS_RECEIVING, isReceiving)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }

        fun update(
            context: Context,
            direction: TransferDirection,
            content: String,
            progressPercent: Int
        ) {
            if (!_isRunning.value) return
            val isReceiving = direction == TransferDirection.RECEIVE
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, if (isReceiving) "Receiving files" else "Sending files")
                putExtra(EXTRA_CONTENT, content)
                putExtra(EXTRA_PROGRESS, progressPercent)
                putExtra(EXTRA_IS_RECEIVING, isReceiving)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            _isRunning.value = false
            try {
                context.stopService(Intent(context, TransferForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }
}
