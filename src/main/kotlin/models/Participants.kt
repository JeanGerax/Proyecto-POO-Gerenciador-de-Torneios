package com.example.models

import org.jetbrains.exposed.sql.Table


object Participants : Table("participants") {
    val userId = integer("user_id").references(Users.id)
    val tournamentId = integer("tournament_id").references(Tournaments.id)

    override val primaryKey = PrimaryKey(userId, tournamentId)
}