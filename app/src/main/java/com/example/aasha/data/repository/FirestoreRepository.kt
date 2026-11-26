package com.example.aasha.data.repository

import com.example.aasha.domain.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun isStudentIdTaken(studentId: String): Boolean {
        return try {
            firestore.collection("hostellers")
                .document(studentId)
                .get()
                .await()
                .exists()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                true
            } else {
                throw e
            }
        }
    }

    suspend fun saveUser(user: User) {
        val userRef = firestore.collection("hostellers").document(user.studentId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (snapshot.exists()) {
                throw IllegalStateException("Student/Hosteller ID already taken")
            }
            transaction.set(userRef, user)
        }.await()
    }

    suspend fun updateUserProfile(user: User) {
        firestore.collection("hostellers")
            .document(user.studentId)
            .set(user, SetOptions.merge())
            .await()
    }

    suspend fun getUserByStudentId(studentId: String): User? {
        return try {
            firestore.collection("hostellers")
                .document(studentId)
                .get()
                .await()
                .toObject(User::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getUserByAuthUid(authUid: String): User? {
        return try {
            firestore.collection("hostellers")
                .whereEqualTo("authUid", authUid)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.toObject(User::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
