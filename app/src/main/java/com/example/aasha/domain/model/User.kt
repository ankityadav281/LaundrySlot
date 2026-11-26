package com.example.aasha.domain.model

data class User(
    val studentId: String = "",
    val authUid: String = "",
    val name: String = "",
    val email: String = "",
    val roomNumber: String = "",
    val hostelBlock: String = "Block A",
    val floor: Int = 1,
    val phoneNumber: String = "",
    val role: String = "Hosteller"
)
