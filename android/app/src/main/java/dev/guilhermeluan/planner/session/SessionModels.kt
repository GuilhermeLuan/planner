package dev.guilhermeluan.planner.session

data class Account(
    val id: String,
    val username: String,
    val timezone: String,
    val mustChangePassword: Boolean,
)

data class Planner(
    val id: String,
    val accountId: String,
)
