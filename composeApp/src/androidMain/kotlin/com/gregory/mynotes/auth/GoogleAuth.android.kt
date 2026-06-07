package com.gregory.mynotes.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CompletableDeferred

@SuppressLint("StaticFieldLeak")
actual object GoogleAuth {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var activity: Activity
    private var pendingSignIn: CompletableDeferred<GoogleUser>? = null

    // Вызывается из MainActivity.onCreate
    fun init(activity: Activity) {
        this.activity = activity

        Log.d("PKG", activity.packageName)
        val clientId = "398279470752-to09eb62a520vman7oce28flbvdg700k.apps.googleusercontent.com"
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(activity, gso)
    }

    actual suspend fun signIn(): GoogleUser {
        if (pendingSignIn != null) {
            return pendingSignIn!!.await()
        }
        pendingSignIn = CompletableDeferred()
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, RC_SIGN_IN)
        return pendingSignIn!!.await()
    }

    // Обработка результата из MainActivity.onActivityResult
    fun handleActivityResult(requestCode: Int, data: Intent?) {
        if (requestCode == RC_SIGN_IN) {
            Log.d("GoogleAuth", "Sign-in result received")
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("GoogleAuth", "Account: ${account.email}")
                handleSignInResult(account)
            } catch (e: ApiException) {
                Log.e("GoogleAuth", "Sign-in failed", e)
                handleSignInResult(null, e)
            }
        }
    }

    private fun handleSignInResult(account: GoogleSignInAccount?, error: Exception? = null) {
        val deferred = pendingSignIn ?: return

        if (error != null || account == null) {
            pendingSignIn = null
            deferred.completeExceptionally(error ?: Exception("Google Sign-In failed"))
            return
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(account.idToken, null)
        FirebaseAuth.getInstance()
            .signInWithCredential(firebaseCredential)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser == null) {
                    deferred.completeExceptionally(Exception("Firebase user is null"))
                    pendingSignIn = null
                } else {
                    // Получаем Firebase ID token
                    firebaseUser.getIdToken(false)
                        .addOnSuccessListener { tokenResult ->
                            val firebaseIdToken = tokenResult.token
                            if (firebaseIdToken == null) {
                                deferred.completeExceptionally(Exception("Failed to get Firebase ID token"))
                                pendingSignIn = null
                                return@addOnSuccessListener
                            }

                            val googleUser = GoogleUser(
                                id = firebaseUser.email!!.replace(".", "_").replace("@", "_"),
                                email = firebaseUser.email ?: "",
                                name = firebaseUser.displayName ?: "",
                                picture = firebaseUser.photoUrl?.toString(),
                                accessToken = firebaseIdToken,
                            )
                            deferred.complete(googleUser)
                            pendingSignIn = null
                        }
                        .addOnFailureListener { exception ->
                            deferred.completeExceptionally(exception)
                            pendingSignIn = null
                        }
                }
            }
            .addOnFailureListener { exception ->
                deferred.completeExceptionally(exception)
                pendingSignIn = null
            }
    }


//    actual fun signOut() {
//        googleSignInClient.signOut()
//        FirebaseAuth.getInstance().signOut()
//    }
//
//    actual fun getCurrentUser(): GoogleUser? {
//        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return null
//        return GoogleUser(
//            id = firebaseUser.uid,
//            email = firebaseUser.email ?: "",
//            name = firebaseUser.displayName ?: "",
//            picture = firebaseUser.photoUrl?.toString()
//        )
//    }

    private const val RC_SIGN_IN = 1001
}