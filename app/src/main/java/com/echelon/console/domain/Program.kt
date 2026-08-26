package com.echelon.console.domain

data class Program(
    val id: ProgramId,
    val title: String,
    val category: ProgramCategory,
    val durationLabel: String,
    val promise: String,
)
