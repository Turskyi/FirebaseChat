package ua.turskyi.firebasechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.*

class CustomFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        Log.d("on new token", p0)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.notification?.imageUrl
        showNotification(
            remoteMessage.notification?.title!!, remoteMessage.notification?.body!!
        )
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        val channelId = "ua.turskyi.firebasechat"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                "Notification",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            notificationChannel.description = "customDescription"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.BLUE

            val bitmap = BitmapFactory.decodeResource(
                resources,
                R.mipmap.ic_launcher
            )
            notificationManager.createNotificationChannel(notificationChannel)
            val notificationBuilder = NotificationCompat.Builder(this, channelId)
            notificationBuilder.setAutoCancel(true)
                .setSmallIcon(R.drawable.pic_firebase_chat)
                .setLargeIcon(bitmap)
                .setContentTitle(title)
                .setContentText(body)
                .setWhen(System.currentTimeMillis())
                .setDefaults(Notification.DEFAULT_ALL)
            notificationManager.notify(Random().nextInt(), notificationBuilder.build())
        } else {
            TODO("VERSION.SDK_INT < O")
        }
    }
}