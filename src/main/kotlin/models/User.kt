package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table


@Serializable
data class User(
    val name: String,
    val email: String,
    val passwordHash: String
)

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 512)

    override val primaryKey = PrimaryKey(id)
}