package dev.guilhermeluan.planner.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.guilhermeluan.planner.tasks.DoseStatus
import dev.guilhermeluan.planner.tasks.MedicineStatus
import dev.guilhermeluan.planner.tasks.StockLedger
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicineDao {
    // Upsert (não REPLACE): substituir apagaria a linha e, por CASCADE, as Doses registradas.
    @Upsert
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

    // IGNORE: duas edições no mesmo Dia mantêm a versão original, a única que vale para o passado.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPreviousVersion(previousVersion: MedicinePreviousVersionEntity)

    @Query("SELECT * FROM medicine_previous_versions WHERE medicineId = :medicineId AND until > :day ORDER BY until LIMIT 1")
    protected abstract suspend fun firstPreviousVersionAfter(medicineId: String, day: String): MedicinePreviousVersionEntity?

    @Query("DELETE FROM medicine_previous_versions WHERE medicineId = :medicineId AND until > :day")
    protected abstract suspend fun deletePreviousVersionsAfter(medicineId: String, day: String)

    /**
     * Guarda a versão que vigorava até [previousVersion] e grava o Remédio editado, na mesma transação. Se uma
     * edição anterior só valia depois de `previousVersion.until`, a versão anterior a ela é a que os Dias de antes
     * mostravam: ela passa a valer até `previousVersion.until`, e as posteriores deixam de existir.
     */
    @Transaction
    open suspend fun writeEditedMedicine(
        previousVersion: MedicinePreviousVersionEntity,
        medicine: MedicineEntity,
        times: List<MedicineTimeEntity>,
    ) {
        val shownBefore = firstPreviousVersionAfter(medicine.id, previousVersion.until)
        deletePreviousVersionsAfter(medicine.id, previousVersion.until)
        insertPreviousVersion(shownBefore?.copy(until = previousVersion.until) ?: previousVersion)
        writeLocalMedicine(medicine, times)
    }

    @Query("SELECT MAX(`day`) FROM dose_records WHERE medicineId = :medicineId")
    abstract suspend fun lastRegisteredDay(medicineId: String): String?

    @Query("SELECT medicineId, MAX(`day`) AS `day` FROM dose_records WHERE accountId = :accountId GROUP BY medicineId")
    abstract fun observeLastRegisteredDays(accountId: String): Flow<List<LastRegisteredDay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun openArchivedPeriod(period: MedicineArchivedPeriodEntity)

    @Query("UPDATE medicine_archived_periods SET archivedUntil = :day WHERE medicineId = :medicineId AND archivedUntil IS NULL")
    protected abstract suspend fun closeArchivedPeriod(medicineId: String, day: String)

    @Query("UPDATE medicines SET status = :status, updatedAt = :updatedAt WHERE id = :medicineId")
    protected abstract suspend fun setStatus(medicineId: String, status: String, updatedAt: String)

    /**
     * Grava só o status do Remédio e abre (arquivar) ou fecha (restaurar) o Período arquivado em [day].
     * Não regrava o resto da linha, para não desfazer um desconto de Estoque feito no meio do caminho.
     */
    @Transaction
    open suspend fun writeStatusChange(medicineId: String, status: MedicineStatus, updatedAt: String, day: String) {
        setStatus(medicineId, status.name, updatedAt)
        if (status == MedicineStatus.ARCHIVED) {
            openArchivedPeriod(MedicineArchivedPeriodEntity(medicineId, day, null))
        } else {
            closeArchivedPeriod(medicineId, day)
        }
    }

    @Query(
        "SELECT p.* FROM medicine_archived_periods p JOIN medicines m ON m.id = p.medicineId WHERE m.accountId = :accountId",
    )
    abstract fun observeArchivedPeriods(accountId: String): Flow<List<MedicineArchivedPeriodEntity>>

    @Query(
        "SELECT v.* FROM medicine_previous_versions v JOIN medicines m ON m.id = v.medicineId WHERE m.accountId = :accountId",
    )
    abstract fun observePreviousVersions(accountId: String): Flow<List<MedicinePreviousVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDoseRecord(record: DoseRecordEntity)

    @Query("DELETE FROM dose_records WHERE medicineId = :medicineId AND `day` = :day AND `time` = :time")
    abstract suspend fun deleteDoseRecord(medicineId: String, day: String, time: String)

    @Query("SELECT * FROM dose_records WHERE medicineId = :medicineId AND `day` = :day AND `time` = :time")
    abstract suspend fun doseRecord(medicineId: String, day: String, time: String): DoseRecordEntity?

    @Query("UPDATE medicines SET stockAmount = :amount WHERE id = :medicineId")
    protected abstract suspend fun setStock(medicineId: String, amount: Int)

    @Query("SELECT * FROM medicine_previous_versions WHERE medicineId = :medicineId")
    abstract suspend fun previousVersions(medicineId: String): List<MedicinePreviousVersionEntity>

    /**
     * Grava o estado da Dose e ajusta o Estoque na mesma transação: tomar desconta [amount], a dose que
     * vale no Dia (sem ficar negativo), sair de tomada devolve exatamente o que foi descontado, pular não
     * altera.
     */
    @Transaction
    open suspend fun writeDoseStatus(
        accountId: String,
        medicineId: String,
        day: String,
        time: String,
        status: String,
        updatedAt: String,
        amount: Int,
    ) {
        val medicine = checkNotNull(medicine(accountId, medicineId)) { "Remédio não encontrado" }
        val next = DoseStatus.valueOf(status)
        val previous = doseRecord(medicineId, day, time)
        val stock = medicine.stockAmount
        var deducted = 0
        if (stock != null) {
            val result = StockLedger.transition(
                stock, amount, previous?.let { DoseStatus.valueOf(it.status) }, previous?.stockDeducted ?: 0, next,
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
    }

    @Query("SELECT * FROM medicines WHERE accountId = :accountId")
    abstract fun observeMedicines(accountId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE accountId = :accountId AND id = :medicineId")
    abstract suspend fun medicine(accountId: String, medicineId: String): MedicineEntity?

    @Query("SELECT * FROM medicine_times WHERE medicineId = :medicineId")
    abstract suspend fun times(medicineId: String): List<MedicineTimeEntity>

    @Query(
        "SELECT t.* FROM medicine_times t JOIN medicines m ON m.id = t.medicineId WHERE m.accountId = :accountId",
    )
    abstract fun observeTimes(accountId: String): Flow<List<MedicineTimeEntity>>

    @Query("SELECT * FROM dose_records WHERE accountId = :accountId AND `day` = :day")
    abstract fun observeDoseRecords(accountId: String, day: String): Flow<List<DoseRecordEntity>>

    @Query("SELECT * FROM dose_records WHERE accountId = :accountId AND `day` BETWEEN :from AND :to")
    abstract fun observeDoseRecordsBetween(accountId: String, from: String, to: String): Flow<List<DoseRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDoseSnooze(snooze: DoseSnoozeEntity)

    @Query("SELECT * FROM dose_snoozes WHERE accountId = :accountId AND `day` = :day")
    abstract fun observeDoseSnoozes(accountId: String, day: String): Flow<List<DoseSnoozeEntity>>
}

/** Último Dia com Dose registrada de um Remédio. */
data class LastRegisteredDay(val medicineId: String, val day: String)
