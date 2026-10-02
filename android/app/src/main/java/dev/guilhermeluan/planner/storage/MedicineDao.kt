package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.StockLedger
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

    @Query("SELECT * FROM dose_records WHERE medicineId = :medicineId AND `day` = :day AND `time` = :time")
    abstract suspend fun doseRecord(medicineId: String, day: String, time: String): DoseRecordEntity?

    @Query("UPDATE medicines SET stockAmount = :amount WHERE id = :medicineId")
    protected abstract suspend fun setStock(medicineId: String, amount: Int)

    /**
     * Grava o estado da Dose e ajusta o Estoque na mesma transação: tomar desconta a quantidade
     * (sem ficar negativo), sair de tomada devolve exatamente o que foi descontado, pular não altera.
     * Devolve null quando o Remédio não existe.
     */
    @Transaction
    open suspend fun writeDoseStatus(
        accountId: String,
        medicineId: String,
        day: String,
        time: String,
        status: String,
        updatedAt: String,
    ): Unit? {
        val medicine = medicine(accountId, medicineId) ?: return null
        val next = DoseStatus.valueOf(status)
        val previous = doseRecord(medicineId, day, time)
        val stock = medicine.stockAmount
        var deducted = 0
        if (stock != null) {
            val result = StockLedger.transition(
                stock, medicine.amount, previous?.let { DoseStatus.valueOf(it.status) }, previous?.stockDeducted ?: 0, next,
            )
            if (result.stock != stock) setStock(medicineId, result.stock)
            deducted = result.deducted
        }
        if (next == DoseStatus.PENDING) {
            deleteDoseRecord(medicineId, day, time)
        } else {
            upsertDoseRecord(
                DoseRecordEntity(
                    medicineId = medicineId,
                    day = day,
                    time = time,
                    accountId = accountId,
                    status = status,
                    takenAt = updatedAt.takeIf { next == DoseStatus.TAKEN },
                    updatedAt = updatedAt,
                    stockDeducted = deducted,
                ),
            )
        }
        return Unit
    }

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
