package dev.guilhermeluan.planner.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicines",
    indices = [Index("accountId"), Index("plannerId")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannerEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MedicineEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val plannerId: String,
    val name: String,
    val amount: Int,
    val unit: String,
    val repeatKind: String,
    val repeatWeekdays: String,
    val startDate: String,
    val endDate: String?,
    val status: String,
    val updatedAt: String,
    val stockAmount: Int? = null,
    val stockCapacity: Int? = null,
    val stockThreshold: Int? = null,
)

@Entity(
    tableName = "medicine_times",
    primaryKeys = ["medicineId", "time"],
    indices = [Index("medicineId")],
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MedicineTimeEntity(
    val medicineId: String,
    val time: String,
)

/** Só existe quando a Dose muda de estado; Doses pendentes são projetadas pela repetição. */
@Entity(
    tableName = "dose_records",
    primaryKeys = ["medicineId", "day", "time"],
    indices = [Index("accountId", "day")],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DoseRecordEntity(
    val medicineId: String,
    val day: String,
    val time: String,
    val accountId: String,
    val status: String,
    val takenAt: String?,
    val updatedAt: String,
    /** Quanto do Estoque esta Dose descontou; é o que volta ao desmarcar. */
    @ColumnInfo(defaultValue = "0") val stockDeducted: Int = 0,
)
