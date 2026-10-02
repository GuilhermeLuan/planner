package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertMedicine(medicine: MedicineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTimes(times: List<MedicineTimeEntity>)

    @Query("DELETE FROM medicine_times WHERE medicineId = :medicineId")
    protected abstract suspend fun deleteTimes(medicineId: String)

    @Transaction
    open suspend fun writeLocalMedicine(medicine: MedicineEntity, times: List<MedicineTimeEntity>) {
        upsertMedicine(medicine)
        deleteTimes(medicine.id)
        insertTimes(times)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDoseRecord(record: DoseRecordEntity)

    @Query("DELETE FROM dose_records WHERE medicineId = :medicineId AND `day` = :day AND `time` = :time")
    abstract suspend fun deleteDoseRecord(medicineId: String, day: String, time: String)

    @Query("SELECT * FROM medicines WHERE accountId = :accountId")
    abstract fun observeMedicines(accountId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE accountId = :accountId AND id = :medicineId")
    abstract suspend fun medicine(accountId: String, medicineId: String): MedicineEntity?

    @Query(
        "SELECT t.* FROM medicine_times t JOIN medicines m ON m.id = t.medicineId WHERE m.accountId = :accountId",
    )
    abstract fun observeTimes(accountId: String): Flow<List<MedicineTimeEntity>>

    @Query("SELECT * FROM dose_records WHERE accountId = :accountId AND `day` = :day")
    abstract fun observeDoseRecords(accountId: String, day: String): Flow<List<DoseRecordEntity>>
}
