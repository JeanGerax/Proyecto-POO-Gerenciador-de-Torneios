package com.example

import com.example.models.Participants
import com.example.models.Tournaments
import com.example.models.Users
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.thymeleaf.Thymeleaf
import io.ktor.server.thymeleaf.ThymeleafContent
import jdk.internal.org.jline.utils.Colors.H
import kotlinx.coroutines.Dispatchers
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.css.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

fun Application.configureDatabases() {
    val dbHost = environment.config.property("postgres.host").getString()
    val dbPort = environment.config.property("postgres.port").getString()
    val dbDatabase = environment.config.property("postgres.database").getString()
    val dbUser = environment.config.property("postgres.user").getString()
    val dbPassword = environment.config.property("postgres.password").getString()

    val jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbDatabase"
    val driverClassName = "org.postgresql.Driver"

    Database.connect(jdbcUrl, driverClassName, dbUser, dbPassword)

    transaction {
        SchemaUtils.create(Users, Tournaments, Participants)
    }
    log.info("Connected to database")
}

suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
