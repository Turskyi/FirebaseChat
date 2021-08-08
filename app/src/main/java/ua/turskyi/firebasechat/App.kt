package ua.turskyi.firebasechat

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.lang.Exception

class App:Application() {
    override fun onCreate() {
        super.onCreate()

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
        }
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:858217350664:android:90b1ccf80587dd4d6fb3bd") // Required for Analytics.
            .setApiKey("AIzaSyB_ZQERR_rnM-nZ00Oz3htxE38dHtuTXRs") // Required for Auth.
            .setDatabaseUrl("https://friendlychat-87bc8.firebaseio.com") // Required for RTDB.
            .build()
        FirebaseApp.initializeApp(this , options, "secondary")
    }
}