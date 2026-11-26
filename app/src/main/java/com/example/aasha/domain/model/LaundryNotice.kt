package com.example.aasha.domain.model

data class LaundryNotice(
    val id: String,
    val title: String,
    val description: String,
    val tag: String,
    val timestamp: Long = System.currentTimeMillis()
)
