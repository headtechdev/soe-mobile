package br.com.soe.campo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class,
        AreaEntity::class,
        JobRoleEntity::class,
        ShiftEntity::class,
        PersonEntity::class,
        ChecklistTemplateEntity::class,
        ChecklistItemEntity::class,
        ChecklistExecutionEntity::class,
        ChecklistAnswerEntity::class,
        IncidentEntity::class,
        AlertEntity::class,
        PendingAttachmentEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SoeDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun incidentDao(): IncidentDao
    abstract fun alertDao(): AlertDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile
        private var instance: SoeDatabase? = null

        fun get(context: Context): SoeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        SoeDatabase::class.java,
                        "soe-campo.db",
                    )
                    // O banco local e um cache reconstituivel pelo sincronismo:
                    // em caso de mudanca de schema vale recriar em vez de migrar.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
