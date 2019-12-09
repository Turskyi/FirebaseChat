package ua.turskyi.firebasechat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.AuthUI.IdpConfig.EmailBuilder
import com.firebase.ui.auth.AuthUI.IdpConfig.GoogleBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        const val ANONYMOUS = "anonymous"
        const val DEFAULT_MSG_LENGTH_LIMIT = 1000
        const val FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length"
        const val RC_SIGN_IN = 1
        private const val RC_PHOTO_PICKER = 2
    }

    private var mMessageListView: ListView? = null
    private var mMessageAdapter: MessageAdapter? = null
    private var mProgressBar: ProgressBar? = null
    private var mPhotoPickerButton: ImageButton? = null
    private var mMessageEditText: EditText? = null
    private var mSendButton: Button? = null
    private var mUsername: String? = null
    // Firebase instance variables
    private var mFirebaseDatabase: FirebaseDatabase? = null
    private var mMessagesDatabaseReference: DatabaseReference? = null
    private var mChildEventListener: ChildEventListener? = null
    private var mFirebaseAuth: FirebaseAuth? = null
    private var mAuthstateListener: AuthStateListener? = null
    private var mFirebaseStorage: FirebaseStorage? = null
    private var mChatPhotosStorageReference: StorageReference? = null
    private var mFirebaseRemoteConfig: FirebaseRemoteConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mUsername = ANONYMOUS
        mFirebaseDatabase = FirebaseDatabase.getInstance()
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseStorage = FirebaseStorage.getInstance()
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        mMessagesDatabaseReference = mFirebaseDatabase!!.reference.child("messages")
        mChatPhotosStorageReference = mFirebaseStorage!!.reference.child("chat_photos")
        // Initialize references to views
        mProgressBar = findViewById<View>(R.id.progressBar) as ProgressBar
        mMessageListView =
            findViewById<View>(R.id.messageListView) as ListView
        mPhotoPickerButton = findViewById<View>(R.id.photoPickerButton) as ImageButton
        mMessageEditText = findViewById<View>(R.id.messageEditText) as EditText
        mSendButton = findViewById<View>(R.id.sendButton) as Button
        // Initialize message ListView and its adapter
        val friendlyMessages: List<FriendlyMessage?> =
            ArrayList()
        mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
        mMessageListView!!.adapter = mMessageAdapter
        // Initialize progress bar
        mProgressBar!!.visibility = ProgressBar.INVISIBLE
        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton!!.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            startActivityForResult(
                Intent.createChooser(intent, "Complete action using"),
                RC_PHOTO_PICKER
            )
        }
        // Enable Send button when there's text to send
        mMessageEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
            }

            override fun onTextChanged(
                charSequence: CharSequence,
                i: Int,
                i1: Int,
                i2: Int
            ) {
                mSendButton!!.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        mMessageEditText!!.filters = arrayOf<InputFilter>(
            LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)
        )
        // Send button sends a message and clears the EditText
        mSendButton!!.setOnClickListener {
            val friendlyMessage = FriendlyMessage(
                mMessageEditText!!.text
                    .toString(), mUsername, null
            )
            mMessagesDatabaseReference!!.push().setValue(friendlyMessage)
            // Clear input box
            mMessageEditText!!.setText("")
        }
        mAuthstateListener = AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) { //user is signed in
                onSignedInInitialize(user.displayName)
            } else { //user is signed out
                onSignedOutCleanup()
                val providers = Arrays.asList(
                    EmailBuilder().build(),
                    GoogleBuilder().build()
                )
                // Create and launch sign-in intent
                startActivityForResult(
                    AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setIsSmartLockEnabled(false)
                        .setAvailableProviders(providers)
                        .build(),
                    RC_SIGN_IN
                )
            }
        }

        // Create Remote Config Setting to enable developer mode.
        // Fetching configs from the server is normally limited to 5 requests per hour.
        // Enabling developer mode allows many more requests to be made per hour, so developers
        // can test different config values during development.
        // Create Remote Config Setting to enable developer mode.
// Fetching configs from the server is normally limited to 5 requests per hour.
// Enabling developer mode allows many more requests to be made per hour, so developers
// can test different config values during development.
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600L)
            .build()
        mFirebaseRemoteConfig!!.setConfigSettingsAsync(configSettings)

        // Define default config values. Defaults are used when fetched config values are not
        // available. Eg: if an error occurred fetching values from the server.
        // Define default config values. Defaults are used when fetched config values are not
// available. Eg: if an error occurred fetching values from the server.
        val defaultConfigMap: MutableMap<String, Any> = HashMap()
        defaultConfigMap[FRIENDLY_MSG_LENGTH_KEY] = DEFAULT_MSG_LENGTH_LIMIT
        mFirebaseRemoteConfig!!.setDefaultsAsync(defaultConfigMap)
        fetchConfig()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                //sign out
                AuthUI.getInstance().signOut(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) { // Sign-in succeeded, set up the UI
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show()
            } else if (resultCode == Activity.RESULT_CANCELED) { // Sign in was canceled by the
                // user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {
            val selectedImageUri = data?.data
            // Get a reference to store file at chat_photos/<FILENAME>
            val photoRef =
                mChatPhotosStorageReference!!.child(selectedImageUri!!.lastPathSegment!!)
            // Upload file to Firebase Storage
            photoRef.putFile(selectedImageUri)
                .addOnSuccessListener(
                    this
                ) { taskSnapshot ->
                    if (taskSnapshot.metadata != null) {
                        if (taskSnapshot.metadata!!.reference != null) {
                            val result =
                                taskSnapshot.storage.downloadUrl
                            result.addOnSuccessListener { uri ->
                                // Set the download URL to the message box, so that the user can
                                // send it to the database
                                val friendlyMessage =
                                    FriendlyMessage(null, mUsername, uri.toString())
                                mMessagesDatabaseReference!!.push().setValue(friendlyMessage)
                            }
                        }
                    }
                }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mAuthstateListener != null) {
            mFirebaseAuth!!.removeAuthStateListener(mAuthstateListener!!)
        }
        detachDatabaseReadListener()
        mMessageAdapter!!.clear()
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth!!.addAuthStateListener(mAuthstateListener!!)
    }

    private fun onSignedInInitialize(username: String?) {
        mUsername = username
        attachDatabaseReadListener()
    }

    private fun onSignedOutCleanup() {
        mUsername = ANONYMOUS
        mMessageAdapter!!.clear()
        detachDatabaseReadListener()
    }

    private fun attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = object : ChildEventListener {
                override fun onChildAdded(
                    dataSnapshot: DataSnapshot,
                    s: String?
                ) {
                    val friendlyMessage = dataSnapshot.getValue(
                        FriendlyMessage::class.java
                    )!!
                    mMessageAdapter!!.add(friendlyMessage)
                }

                override fun onChildChanged(
                    dataSnapshot: DataSnapshot,
                    s: String?
                ) {
                }

                override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
                override fun onChildMoved(
                    dataSnapshot: DataSnapshot,
                    s: String?
                ) {
                }

                override fun onCancelled(databaseError: DatabaseError) {}
            }
            mMessagesDatabaseReference!!.addChildEventListener(
                mChildEventListener as ChildEventListener
            )
        }
    }

    private fun detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mMessagesDatabaseReference!!.removeEventListener(mChildEventListener!!)
            mChildEventListener = null
        }
    }

    // Fetch the config to determine the allowed length of messages.
    private fun fetchConfig() {
        var cacheExpiration: Long = 3600 // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
// server. This should not be used in release builds.
        if (mFirebaseRemoteConfig!!.info.configSettings.minimumFetchIntervalInSeconds == 3600L) {
            cacheExpiration = 0
        }
        mFirebaseRemoteConfig!!.fetch(cacheExpiration)
            .addOnSuccessListener {
                // Make the fetched config available
                // via FirebaseRemoteConfig get<type> calls, e.g., getLong, getString.
                mFirebaseRemoteConfig!!.getBoolean(FRIENDLY_MSG_LENGTH_KEY)
                // Update the EditText length limit with
                // the newly retrieved values from Remote Config.
                applyRetrievedLengthLimit()
            }
            .addOnFailureListener { e ->
                // An error occurred when fetching the config.
                Log.w(TAG, "Error fetching config", e)
                // Update the EditText length limit with
                // the newly retrieved values from Remote Config.
                applyRetrievedLengthLimit()
            }
    }

    /**
     * Apply retrieved length limit to edit text field. This result may be fresh from the server or it may be from
     * cached values.
     */
    private fun applyRetrievedLengthLimit() {
        val friendlyMsgLength =
            mFirebaseRemoteConfig!!.getLong(FRIENDLY_MSG_LENGTH_KEY)
        mMessageEditText!!.filters = arrayOf<InputFilter>(LengthFilter(friendlyMsgLength.toInt()))
        Log.d(
            TAG,
            "$FRIENDLY_MSG_LENGTH_KEY = $friendlyMsgLength"
        )
    }
}
