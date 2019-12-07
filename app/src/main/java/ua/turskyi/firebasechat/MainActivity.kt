package ua.turskyi.firebasechat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.TextWatcher
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
import java.util.*

class MainActivity : AppCompatActivity() {
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mUsername = ANONYMOUS
        mFirebaseDatabase = FirebaseDatabase.getInstance()
        mFirebaseAuth = FirebaseAuth.getInstance()
        mMessagesDatabaseReference = mFirebaseDatabase!!.reference.child("messages")
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
            // TODO: Fire an intent to show an image picker
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
            // TODO: Send messages on click
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

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Sign in canceled!", Toast.LENGTH_SHORT).show()
                finish()
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

    companion object {
        private const val TAG = "MainActivity"
        const val ANONYMOUS = "anonymous"
        const val DEFAULT_MSG_LENGTH_LIMIT = 1000
        const val RC_SIGN_IN = 1
    }
}
