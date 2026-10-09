package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertPlanner(planner: PlannerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertMetadata(metadata: SessionMetadataEntity)

    @Query("SELECT value FROM session_metadata WHERE `key` = 'active_account_id'")
    abstract suspend fun activeAccountId(): String?

    @Query("SELECT value FROM session_metadata WHERE `key` = :key")
    abstract suspend fun metadata(key: String): String?

    @Query("SELECT value FROM session_metadata WHERE `key` = :key")
    abstract fun observeMetadata(key: String): Flow<String?>

    @Query("SELECT * FROM accounts WHERE id = :accountId")
    abstract suspend fun account(accountId: String): AccountEntity?

    @Query("SELECT * FROM planners WHERE accountId = :accountId")
    abstract suspend fun planner(accountId: String): PlannerEntity?

    @Query("UPDATE accounts SET timezone = :timezone WHERE id = :accountId")
    abstract suspend fun updateTimezone(accountId: String, timezone: String)

    @Query("UPDATE accounts SET username = :username, timezone = :timezone, mustChangePassword = :mustChangePassword WHERE id = :accountId")
    abstract suspend fun updateAccount(accountId: String, username: String, timezone: String, mustChangePassword: Boolean)

    @Query("UPDATE accounts SET username = :username WHERE id = :accountId")
    abstract suspend fun updateName(accountId: String, username: String)

    @Query("DELETE FROM session_metadata WHERE `key` = 'active_account_id'")
    abstract suspend fun clearActiveAccess()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveMetadata(metadata: SessionMetadataEntity)

    @Query("DELETE FROM session_metadata WHERE `key` = :key")
    abstract suspend fun clearMetadata(key: String)

    @Transaction
    open suspend fun saveActive(account: AccountEntity, planner: PlannerEntity) {
        upsertAccount(account)
        upsertPlanner(planner)
        upsertMetadata(SessionMetadataEntity(ACTIVE_ACCOUNT_KEY, account.id))
    }

    @Transaction
    open suspend fun createLocalPlanner(account: AccountEntity, planner: PlannerEntity) {
        check(activeAccountId() == null) { "Este dispositivo já possui um Planner" }
        upsertAccount(account)
        upsertPlanner(planner)
        upsertMetadata(SessionMetadataEntity(ACTIVE_ACCOUNT_KEY, account.id))
    }

    companion object {
        internal const val ACTIVE_ACCOUNT_KEY = "active_account_id"
    }
}
