package dev.guilhermeluan.planner.storage

import androidx.room.Entity
import androidx.room.ForeignKey

/** Só o total do Dia; não há histórico de lançamentos. */
@Entity(
    tableName = "water_intakes",
    primaryKeys = ["accountId", "day"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WaterIntakeEntity(
    val accountId: String,
    val day: String,
    val totalMl: Int,
    val updatedAt: String,
)

/** Vigência da Meta de água: vale do Dia [validFrom] até a próxima vigência. */
@Entity(
    tableName = "water_goals",
    primaryKeys = ["accountId", "validFrom"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WaterGoalEntity(
    val accountId: String,
    val validFrom: String,
    val goalMl: Int,
)

/** Configuração do Lembrete de água: uma linha por Conta, com os horários no formato "08:00". */
@Entity(
    tableName = "water_reminder_settings",
    primaryKeys = ["accountId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WaterReminderSettingsEntity(
    val accountId: String,
    val enabled: Boolean,
    val intervalHours: Int,
    val windowStart: String,
    val windowEnd: String,
)
