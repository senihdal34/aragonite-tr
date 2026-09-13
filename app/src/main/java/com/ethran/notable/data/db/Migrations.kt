package com.ethran.notable.data.db

import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Page ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Page ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE Stroke ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Stroke ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE Notebook ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Notebook ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Page ADD COLUMN nativeTemplate TEXT NOT NULL DEFAULT 'blank'")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "DELETE FROM Page " +
                    "WHERE notebookId IS NOT NULL " +
                    "AND notebookId NOT IN (SELECT id FROM Notebook);"
        )
    }
}

@RenameColumn.Entries(
    RenameColumn(
        tableName = "Page",
        fromColumnName = "nativeTemplate",
        toColumnName = "background"
    )
)
class AutoMigration30to31 : AutoMigrationSpec

@RenameColumn.Entries(
    RenameColumn(
        tableName = "Notebook",
        fromColumnName = "defaultNativeTemplate",
        toColumnName = "defaultBackground"
    )
)
class AutoMigration31to32 : AutoMigrationSpec



val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Safely add columns — check existence first to avoid "duplicate column" errors
        val pageCols = getColumnNames(db, "Page")
        val annotCols = getColumnNames(db, "Annotation")

        if ("title" !in pageCols) {
            db.execSQL("ALTER TABLE Page ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        }
        if ("pinned" !in pageCols) {
            db.execSQL("ALTER TABLE Page ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        }
        if ("text" !in annotCols) {
            db.execSQL("ALTER TABLE Annotation ADD COLUMN text TEXT NOT NULL DEFAULT ''")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS tag_priority (tagName TEXT NOT NULL PRIMARY KEY, sortOrder INTEGER NOT NULL DEFAULT 0)")
    }
}

/** Utility: return column names for a given table. Handles both camelCase and lowercase table names. */
private fun getColumnNames(db: SupportSQLiteDatabase, tableName: String): Set<String> {
    val names = mutableSetOf<String>()
    val cursor = db.query("PRAGMA table_info('$tableName')")
    while (cursor.moveToNext()) {
        names.add(cursor.getString(1)) // column 'name' is at index 1
    }
    cursor.close()
    return names
}
// 1. Rename original Stroke table to stroke_old
// 2. Drop any carried indexes from old table (index_Stroke_pageId)
// 3. Create new Stroke table (with points as BLOB and color default)
// 4. Recreate required index on the NEW table
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL("ALTER TABLE stroke RENAME TO stroke_old")

        // IMPORTANT: drop the old index that now belongs to stroke_old
        // (otherwise we can't recreate it for the new table)
        db.execSQL("DROP INDEX IF EXISTS `index_Stroke_pageId`")

        // Create new table with correct name/case matching the entity @Entity(tableName = "Stroke") (default)
        db.execSQL(
            """
            CREATE TABLE `Stroke` (
                `id` TEXT NOT NULL,
                `size` REAL NOT NULL,
                `pen` TEXT NOT NULL,
                `color` INTEGER NOT NULL DEFAULT 0xFF000000,
                `maxPressure` INTEGER NOT NULL DEFAULT 4096,
                `top` REAL NOT NULL,
                `bottom` REAL NOT NULL,
                `left` REAL NOT NULL,
                `right` REAL NOT NULL,
                `points` BLOB NOT NULL,
                `pageId` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`pageId`) REFERENCES `Page`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Recreate the expected index for the NEW table
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Stroke_pageId` ON `Stroke` (`pageId`)")
    }
}