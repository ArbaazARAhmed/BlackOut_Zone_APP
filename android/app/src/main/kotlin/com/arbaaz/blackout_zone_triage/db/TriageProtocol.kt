package com.blackoutzone.triage.db
data class TriageProtocol(
    val id: Int,
    val injuryCategory: String,
    val triagePriority: String,
    val symptomsKeywords: String,
    val treatmentSteps: String,
    val protocolVersion: String = "1.0",
    val clinicalNotes: String = "Local database record",
    val category: String,
    val priority: String,
    val keywords: String,
    val steps: String
)