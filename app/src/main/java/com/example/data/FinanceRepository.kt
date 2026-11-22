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

class FinanceRepository(
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

    private fun walletsCol(uid: String) = db.collection("users").document(uid).collection("wallets")
    private fun transactionsCol(uid: String) = db.collection("users").document(uid).collection("transactions")
    private fun budgetsCol(uid: String) = db.collection("users").document(uid).collection("budgets")

    fun observeWallets(userId: String): Flow<List<Wallet>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/wallets"
        emitAll(
            walletsCol(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Wallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, path)
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<FinanceTransaction>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/transactions"
        emitAll(
            transactionsCol(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FinanceTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<FinanceTransaction> { it.dateKey }
                            .thenByDescending { it.timeLabel }
                            .thenByDescending { it.createdAt?.seconds ?: 0L }
                    )
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, path)
                    throw error
                }
        )
    }

    fun observeBudgets(userId: String): Flow<List<MonthlyBudget>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/budgets"
        emitAll(
            budgetsCol(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MonthlyBudget::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.category }
                }
                .catch { error ->
                    logListenerErrorIfSignedIn(error, uid, path)
                    throw error
                }
        )
    }

    suspend fun ensureDefaultWalletsIfEmpty(userId: String): Result<Unit> {
        val path = "users/$userId/wallets"
        return try {
            val uid = requireUserId(userId)
            val existing = walletsCol(uid).limit(1).get().await()
            if (existing.isEmpty) {
                val batch = db.batch()
                val defaults = listOf(
                    Triple("wallet_cash", "Tunai", Pair(WalletType.CASH.name, "#0F6A53")),
                    Triple("wallet_bank", "Bank Utama", Pair(WalletType.BANK.name, "#1E5F74")),
                    Triple("wallet_ewallet", "E-Wallet", Pair(WalletType.EWALLET.name, "#B86B2B"))
                )
                defaults.forEach { (docId, name, typeAndColor) ->
                    val ref = walletsCol(uid).document(docId)
                    batch.set(
                        ref,
                        mapOf(
                            "name" to name,
                            "type" to typeAndColor.first,
                            "balance" to 0L,
                            "colorHex" to typeAndColor.second,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createWallet(
        userId: String,
        name: String,
        type: WalletType,
        balance: Long,
        colorHex: String
    ): Result<Unit> {
        var docPath = "users/$userId/wallets"
        return try {
            val uid = requireUserId(userId)
            val cleanName = name.trim()
            require(cleanName.length in 1..40) { "Nama dompet harus 1–40 karakter." }
            require(balance in -999_999_999_999L..999_999_999_999L) { "Nominal saldo di luar batas." }
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6,8}$").matches(colorHex)) colorHex else "#0F6A53"
            val docRef = walletsCol(uid).document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "name" to cleanName,
                    "type" to type.name,
                    "balance" to balance,
                    "colorHex" to safeColor,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateWallet(
        userId: String,
        walletId: String,
        name: String,
        type: WalletType,
        balance: Long,
        colorHex: String
    ): Result<Unit> {
        var docPath = "users/$userId/wallets/$walletId"
        return try {
            val uid = requireUserId(userId)
            require(walletId.isNotBlank()) { "ID dompet tidak valid." }
            val cleanName = name.trim()
            require(cleanName.length in 1..40) { "Nama dompet harus 1–40 karakter." }
            require(balance in -999_999_999_999L..999_999_999_999L) { "Nominal saldo di luar batas." }
            val safeColor = if (Regex("^#[0-9A-Fa-f]{6,8}$").matches(colorHex)) colorHex else "#0F6A53"
            val docRef = walletsCol(uid).document(walletId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "type" to type.name,
                    "balance" to balance,
                    "colorHex" to safeColor,
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

    suspend fun deleteWallet(userId: String, walletId: String): Result<Unit> {
        var docPath = "users/$userId/wallets/$walletId"
        return try {
            val uid = requireUserId(userId)
            require(walletId.isNotBlank()) { "ID dompet tidak valid." }
            val docRef = walletsCol(uid).document(walletId)
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

    suspend fun addTransaction(
        userId: String,
        type: TransactionType,
        amount: Long,
        category: String,
        wallet: Wallet,
        note: String,
        dateKey: String,
        timeLabel: String
    ): Result<Unit> {
        var docPath = "users/$userId/transactions"
        return try {
            val uid = requireUserId(userId)
            require(amount in 1L..999_999_999_999L) { "Nominal harus lebih dari Rp 0." }
            val cleanCategory = category.trim()
            require(cleanCategory.length in 1..40) { "Kategori tidak valid." }
            require(wallet.id.isNotBlank()) { "Pilih dompet terlebih dahulu." }
            val cleanNote = note.trim().take(200)
            val monthKey = Formatters.monthKeyFromDateKey(dateKey)

            val txRef = transactionsCol(uid).document()
            docPath = txRef.path
            val walletRef = walletsCol(uid).document(wallet.id)
            val balanceDelta = if (type == TransactionType.INCOME) amount else -amount

            val batch = db.batch()
            batch.set(
                txRef,
                mapOf(
                    "type" to type.name,
                    "amount" to amount,
                    "category" to cleanCategory,
                    "walletId" to wallet.id,
                    "walletName" to wallet.name.take(40),
                    "note" to cleanNote,
                    "dateKey" to dateKey,
                    "monthKey" to monthKey,
                    "timeLabel" to timeLabel,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                walletRef,
                mapOf(
                    "balance" to FieldValue.increment(balanceDelta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        userId: String,
        oldTx: FinanceTransaction,
        newType: TransactionType,
        newAmount: Long,
        newCategory: String,
        newWallet: Wallet,
        newNote: String,
        newDateKey: String,
        newTimeLabel: String
    ): Result<Unit> {
        var docPath = "users/$userId/transactions/${oldTx.id}"
        return try {
            val uid = requireUserId(userId)
            require(oldTx.id.isNotBlank()) { "ID transaksi tidak valid." }
            require(newAmount in 1L..999_999_999_999L) { "Nominal harus lebih dari Rp 0." }
            val cleanCategory = newCategory.trim()
            require(cleanCategory.length in 1..40) { "Kategori tidak valid." }
            require(newWallet.id.isNotBlank()) { "Pilih dompet terlebih dahulu." }
            val cleanNote = newNote.trim().take(200)
            val monthKey = Formatters.monthKeyFromDateKey(newDateKey)

            val txRef = transactionsCol(uid).document(oldTx.id)
            docPath = txRef.path

            val oldDelta = if (oldTx.transactionType == TransactionType.INCOME) oldTx.amount else -oldTx.amount
            val newDelta = if (newType == TransactionType.INCOME) newAmount else -newAmount

            val batch = db.batch()
            batch.update(
                txRef,
                mapOf(
                    "type" to newType.name,
                    "amount" to newAmount,
                    "category" to cleanCategory,
                    "walletId" to newWallet.id,
                    "walletName" to newWallet.name.take(40),
                    "note" to cleanNote,
                    "dateKey" to newDateKey,
                    "monthKey" to monthKey,
                    "timeLabel" to newTimeLabel,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (oldTx.walletId == newWallet.id) {
                val netDelta = newDelta - oldDelta
                if (netDelta != 0L) {
                    batch.update(
                        walletsCol(uid).document(newWallet.id),
                        mapOf(
                            "balance" to FieldValue.increment(netDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else {
                if (oldTx.walletId.isNotBlank()) {
                    val oldWalletRef = walletsCol(uid).document(oldTx.walletId)
                    if (oldWalletRef.get().await().exists()) {
                        batch.update(
                            oldWalletRef,
                            mapOf(
                                "balance" to FieldValue.increment(-oldDelta),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                batch.update(
                    walletsCol(uid).document(newWallet.id),
                    mapOf(
                        "balance" to FieldValue.increment(newDelta),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, tx: FinanceTransaction): Result<Unit> {
        var docPath = "users/$userId/transactions/${tx.id}"
        return try {
            val uid = requireUserId(userId)
            require(tx.id.isNotBlank()) { "ID transaksi tidak valid." }
            val txRef = transactionsCol(uid).document(tx.id)
            docPath = txRef.path

            val oldDelta = if (tx.transactionType == TransactionType.INCOME) tx.amount else -tx.amount
            val batch = db.batch()
            batch.delete(txRef)

            if (tx.walletId.isNotBlank()) {
                val walletRef = walletsCol(uid).document(tx.walletId)
                if (walletRef.get().await().exists()) {
                    batch.update(
                        walletRef,
                        mapOf(
                            "balance" to FieldValue.increment(-oldDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertBudget(
        userId: String,
        monthKey: String,
        category: String,
        limitAmount: Long
    ): Result<Unit> {
        val cleanCategory = category.trim()
        val budgetId = "${monthKey}_${Formatters.slugifyCategory(cleanCategory)}"
        var docPath = "users/$userId/budgets/$budgetId"
        return try {
            val uid = requireUserId(userId)
            require(cleanCategory.length in 1..40) { "Kategori anggaran tidak valid." }
            require(limitAmount in 1L..999_999_999_999L) { "Batas anggaran harus lebih dari Rp 0." }
            val docRef = budgetsCol(uid).document(budgetId)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "limitAmount" to limitAmount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "monthKey" to monthKey,
                        "category" to cleanCategory,
                        "limitAmount" to limitAmount,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(userId: String, budgetId: String): Result<Unit> {
        var docPath = "users/$userId/budgets/$budgetId"
        return try {
            val uid = requireUserId(userId)
            require(budgetId.isNotBlank()) { "ID anggaran tidak valid." }
            val docRef = budgetsCol(uid).document(budgetId)
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

    private fun logListenerErrorIfSignedIn(error: Throwable, uid: String, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }
}
