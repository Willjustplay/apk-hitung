package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AgendaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Sesi akun berubah; operasi dibatalkan." }
        }
        return uid
    }

    private fun tasksCol(uid: String) = db.collection("users").document(uid).collection("tasks")

    fun observeTasks(userId: String): Flow<List<AgendaItem>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/tasks"
        emitAll(
            tasksCol(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AgendaItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addTask(
        userId: String,
        title: String,
        notes: String,
        dateKey: String,
        startTime: String?,
        endTime: String?,
        priority: TaskPriority,
        category: String
    ): Result<Unit> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim()
            require(cleanTitle.length in 1..120) { "Judul tugas/jadwal harus 1–120 karakter." }
            val cleanNotes = notes.trim().take(400)
            val cleanCategory = category.trim().ifBlank { "Pribadi" }.take(30)
            val validStart = startTime?.trim()?.takeIf { Regex("^[0-9]{2}:[0-9]{2}$").matches(it) }
            val validEnd = endTime?.trim()?.takeIf { Regex("^[0-9]{2}:[0-9]{2}$").matches(it) }

            val docRef = tasksCol(uid).document()
            docPath = docRef.path

            val rawMap = mutableMapOf<String, Any?>(
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "dateKey" to dateKey,
                "startTime" to validStart,
                "endTime" to validEnd,
                "priority" to priority.name,
                "category" to cleanCategory,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val payload = rawMap.filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        notes: String,
        dateKey: String,
        startTime: String?,
        endTime: String?,
        priority: TaskPriority,
        category: String,
        completed: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "ID tugas tidak valid." }
            val cleanTitle = title.trim()
            require(cleanTitle.length in 1..120) { "Judul tugas/jadwal harus 1–120 karakter." }
            val cleanNotes = notes.trim().take(400)
            val cleanCategory = category.trim().ifBlank { "Pribadi" }.take(30)
            val validStart = startTime?.trim()?.takeIf { Regex("^[0-9]{2}:[0-9]{2}$").matches(it) }
            val validEnd = endTime?.trim()?.takeIf { Regex("^[0-9]{2}:[0-9]{2}$").matches(it) }

            val docRef = tasksCol(uid).document(taskId)
            docPath = docRef.path

            val updatePayload = mapOf<String, Any>(
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "dateKey" to dateKey,
                "startTime" to (validStart ?: FieldValue.delete()),
                "endTime" to (validEnd ?: FieldValue.delete()),
                "priority" to priority.name,
                "category" to cleanCategory,
                "completed" to completed,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updatePayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleCompleted(userId: String, taskId: String, completed: Boolean): Result<Unit> {
        var docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "ID tugas tidak valid." }
            val docRef = tasksCol(uid).document(taskId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        var docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireUserId(userId)
            require(taskId.isNotBlank()) { "ID tugas tidak valid." }
            val docRef = tasksCol(uid).document(taskId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
