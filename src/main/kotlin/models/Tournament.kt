package com.example.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime


@Serializable
data class Tournament(
    val id: Int,
    val name: String,
    val description: String,
    val format: String,
    val creatorId: Int,
    val creationDate: String
)

object Tournaments : Table("tournaments") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val description = text("description")
    val format = enumerationByName("format", 50, TournamentFormat::class)
    val creatorId = integer("creator_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val creationDate = datetime("creation_date").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}