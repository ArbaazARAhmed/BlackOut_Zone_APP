package com.blackoutzone.triage.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BlackoutZoneDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "blackout_triage.db"
        private const val DATABASE_VERSION = 1
        
        const val TABLE_PROTOCOLS = "triage_protocols"
        const val COLUMN_ID = "id"
        const val COLUMN_CATEGORY = "injury_category"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_KEYWORDS = "keywords"
        const val COLUMN_STEPS = "treatment_steps"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_PROTOCOLS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_CATEGORY + " TEXT,"
                + COLUMN_PRIORITY + " TEXT,"
                + COLUMN_KEYWORDS + " TEXT,"
                + COLUMN_STEPS + " TEXT" + ")")
        db.execSQL(createTable)
        
        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PROTOCOLS")
        onCreate(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val rows = listOf(
            listOf("Cardiac / chest pain", "RED", "chest pain, heart, crushing, pressure, sweating, shortness of breath", "1) Stop activity; sit/lie down. 2) Loosen tight clothing. 3) If breathing trouble/unconscious: call for urgent medical help. 4) Do not give food/drink."),
            listOf("Severe bleeding", "RED", "bleeding, hemorrhage, blood, spurting, soaking, wound", "1) Apply firm direct pressure with clean cloth. 2) If bleeding soaks through, add layers—do not remove. 3) If limb bleed uncontrolled and trained: apply tourniquet above wound. 4) Treat for shock: keep warm, lay flat if safe."),
            listOf("Breathing difficulty", "RED", "difficulty breathing, shortness of breath, wheeze, choking, airway", "1) Sit upright; loosen clothing. 2) If choking and cannot speak/cough: back blows/abdominal thrusts (trained). 3) If lips turning blue, severe distress, or worsening: urgent medical help. 4) Avoid smoke/dust exposure."),
            listOf("Stroke warning signs", "RED", "stroke, face droop, arm weakness, speech slurred, sudden confusion, FAST", "1) Treat as emergency. 2) Note time symptoms started. 3) Keep person safe and still; do not give food/drink. 4) Seek urgent medical help immediately."),
            listOf("Burns", "YELLOW", "burn, scald, blister, flame, chemical burn", "1) Cool burn with cool running water for ~20 minutes if available. 2) Remove rings/tight items if not stuck. 3) Cover with clean non-stick dressing/cloth. 4) Do not pop blisters; do not apply oils. 5) Large/deep burns: urgent medical help."),
            listOf("Fracture / suspected broken bone", "YELLOW", "fracture, broken bone, deformity, swelling, severe pain, cannot bear weight", "1) Immobilize in position found; splint above and below. 2) Check circulation beyond injury. 3) Ice/cool pack if available (wrap). 4) Open fracture or loss of circulation: urgent medical help."),
            listOf("Dehydration / heat illness", "YELLOW", "dehydration, heat, dizzy, dry mouth, no urine, cramps", "1) Move to shade/cool area. 2) Sip oral rehydration/water; small frequent sips. 3) If confusion, fainting, or no sweating with high heat: urgent medical help."),
            listOf("General minor injury", "GREEN", "general, minor, small cut, scrape, mild pain", "1) Clean with clean water. 2) Cover with clean dressing. 3) Monitor for infection (redness, swelling, fever). 4) Escalate if worsening.")
        )

        rows.forEach { protocol ->
            db.execSQL(
                "INSERT INTO $TABLE_PROTOCOLS ($COLUMN_CATEGORY, $COLUMN_PRIORITY, $COLUMN_KEYWORDS, $COLUMN_STEPS) VALUES (?, ?, ?, ?)",
                arrayOf(protocol[0], protocol[1], protocol[2], protocol[3])
            )
        }
    }

    fun queryByKeyword(keyword: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_PROTOCOLS WHERE $COLUMN_KEYWORDS LIKE ? OR $COLUMN_CATEGORY LIKE ?",
            arrayOf("%$keyword%", "%$keyword%")
        )

        if (cursor.moveToFirst()) {
            do {
                val map = mapOf(
                    "id" to cursor.getInt(0).toString(),
                    "category" to cursor.getString(1),
                    "priority" to cursor.getString(2),
                    "steps" to cursor.getString(4)
                )
                results.add(map)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return results
    }

    fun getProtocolsByKeywords(keywords: List<String>): List<TriageProtocol> {
        val results = mutableListOf<TriageProtocol>()
        val db = this.readableDatabase
        
        val whereClause = keywords.joinToString(" OR ") { "$COLUMN_KEYWORDS LIKE ?" }
        val args = keywords.map { "%$it%" }.toTypedArray()
        
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_PROTOCOLS WHERE $whereClause",
            args
        )

        if (cursor.moveToFirst()) {
            do {
                val protocol = TriageProtocol(
                    id = cursor.getInt(0),
                    injuryCategory = cursor.getString(1),
                    triagePriority = cursor.getString(2),
                    symptomsKeywords = cursor.getString(3),
                    treatmentSteps = cursor.getString(4),
                    category = cursor.getString(1), // duplicate for compatibility
                    priority = cursor.getString(2),
                    keywords = cursor.getString(3),
                    steps = cursor.getString(4)
                )
                results.add(protocol)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return results
    }
}