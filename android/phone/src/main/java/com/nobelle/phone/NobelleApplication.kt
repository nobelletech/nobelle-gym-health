package com.nobelle.phone

import android.app.Application
import androidx.room.Room
import com.nobelle.core.data.NobelleDatabase
import com.nobelle.phone.data.EncryptedDatabasePassphraseStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class NobelleApplication : Application() {
    private val passphraseStore by lazy { EncryptedDatabasePassphraseStore(this) }

    /**
     * Application-scoped work (e.g. the outbox drain) that must outlive any
     * single Activity — an activity-scoped drain dies mid-upload if the user
     * leaves the screen.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Device-local identifier for sync provenance; never contains account or
     * email data and lives in no-backup storage like everything private.
     */
    val installationId: String by lazy {
        val file = File(noBackupFilesDir, "installation_id.v1")
        if (file.isFile) {
            file.readText().trim()
        } else {
            UUID.randomUUID().toString().also { file.writeText(it) }
        }
    }

    /**
     * Encrypted durable store. Opening fails closed (throwing) if a database
     * file exists without its passphrase — callers surface this as a UI error.
     */
    val database: NobelleDatabase by lazy {
        System.loadLibrary("sqlcipher")
        val databasePassphrase = passphraseStore.getOrCreate(getDatabasePath(DB_NAME).exists())
        Room.databaseBuilder(this, NobelleDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(databasePassphrase))
            .addMigrations(NobelleDatabase.MIGRATION_1_2, NobelleDatabase.MIGRATION_2_3)
            .build()
    }

    private companion object {
        const val DB_NAME = "nobelle.v2.db"
    }
}
