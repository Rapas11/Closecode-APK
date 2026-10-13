package com.closecode.ui.ide

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.closecode.MainActivity
import com.closecode.R

internal fun shouldAskForOverlayPermission(
    sdk: Int,
    canDrawOverlay: Boolean,
    permissionAskedBefore: Boolean,
): Boolean =
    sdk >= Build.VERSION_CODES.M && !canDrawOverlay && !permissionAskedBefore

internal object BackToEditor {
    const val ACTION_SHOW = "com.closecode.action.SHOW_BACK_TO_EDITOR"
    const val EXTRA_PACKAGE = "app_package"
    private const val CHANNEL_ID = "back_to_editor"

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun openInstalledApp(context: Context, packageName: String, showNotification: Boolean): Result<Unit> =
        runCatching {
            require(packageName.isNotBlank()) { "The app package name is missing." }
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: error("Android cannot find an app screen to open for $packageName.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                try {
                    context.startService(
                        Intent(context, BackToEditorOverlayService::class.java)
                            .setAction(ACTION_SHOW)
                            .putExtra(EXTRA_PACKAGE, packageName),
                    )
                } catch (error: IllegalStateException) {
                    android.util.Log.e("BackToEditor", "Could not start the overlay service.", error)
                    showBackNotification(context)
                } catch (error: SecurityException) {
                    android.util.Log.e("BackToEditor", "Android blocked the overlay service.", error)
                    showBackNotification(context)
                }
            } else if (showNotification) {
                showBackNotification(context)
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
        }

    internal fun showBackNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Return to CloseCode",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val returnIntent = PendingIntent.getActivity(
            context,
            101,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Return to CloseCode")
            .setContentText("Tap to go back to your editor.")
            .setContentIntent(returnIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            try {
                NotificationManagerCompat.from(context).notify(101, notification)
            } catch (error: SecurityException) {
                android.util.Log.e("BackToEditor", "Android blocked the return notification.", error)
            }
        }
    }
}

class BackToEditorOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var button: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != BackToEditor.ACTION_SHOW ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            !Settings.canDrawOverlays(this)
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        removeButton()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            text = "←  Back to editor"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            contentDescription = "Back to CloseCode editor"
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(26, 31, 43))
                cornerRadius = 24 * density
            }
            minHeight = (36 * density).toInt()
            setOnClickListener {
                startActivity(
                    Intent(this@BackToEditorOverlayService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                stopSelf()
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            (36 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
        }
        button = view
        layoutParams = params
        try {
            windowManager?.addView(view, params)
        } catch (error: SecurityException) {
            android.util.Log.e("BackToEditor", "Android denied overlay access.", error)
            BackToEditor.showBackNotification(this)
            removeButton()
            stopSelf(startId)
            return START_NOT_STICKY
        } catch (error: WindowManager.BadTokenException) {
            android.util.Log.e("BackToEditor", "Android rejected the overlay window.", error)
            BackToEditor.showBackNotification(this)
            removeButton()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        handler.postDelayed(::collapseToHandle, 4_000)
        return START_NOT_STICKY
    }

    private fun collapseToHandle() {
        val view = button ?: return
        val params = layoutParams ?: return
        view.text = "‹"
        view.contentDescription = "Back to CloseCode editor"
        view.setPadding(0, 0, 0, 0)
        params.width = (36 * resources.displayMetrics.density).toInt()
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun removeButton() {
        handler.removeCallbacksAndMessages(null)
        button?.let { view -> runCatching { windowManager?.removeView(view) } }
        button = null
        layoutParams = null
    }

    override fun onDestroy() {
        removeButton()
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
