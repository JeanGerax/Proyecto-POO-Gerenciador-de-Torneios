package com.example


import com.example.models.*
import com.example.models.Users.passwordHash
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.thymeleaf.Thymeleaf
import io.ktor.server.thymeleaf.ThymeleafContent
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.css.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import com.example.models.Tournaments
import com.example.models.Tournaments.creationDate
import com.example.models.Tournaments.creatorId
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.mindrot.jbcrypt.BCrypt
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
    routing {

        route("/register") {
            get {
                val error = call.request.queryParameters["error"] ?: ""
                call.respond(ThymeleafContent("register", mapOf("error" to error)))
            }
            post {
                val params = call.receiveParameters()
                try {
                    val userRequest = UserRegistrationRequest(
                        name = params["name"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Name is required"
                        ),
                        email = params["email"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Email is required"
                        ),
                        password = params["password"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "Password is required"
                        )
                    )


                    val userExists = dbQuery {
                        Users.selectAll().where { Users.email eq userRequest.email }.firstOrNull() != null
                    }

                    if (userExists) {
                        call.respondRedirect("/register?error=User already exists")
                        return@post
                    }
                    val hashedPassword = BCrypt.hashpw(userRequest.password, BCrypt.gensalt())
                    dbQuery {
                        Users.insert {
                            it[name] = userRequest.name
                            it[email] = userRequest.email
                            it[passwordHash] = hashedPassword
                        }
                    }
                    call.respondRedirect("/login")
                } catch (e: Exception) {
                    call.respondRedirect("/register?error=Error during registration")
                }
            }
        }

        route("/login")  {
            get {
                val error = call.request.queryParameters["error"] ?: ""
                call.respond(ThymeleafContent("login", mapOf("error" to error)))
            }

            post {
                val params = call.receiveParameters()
                val email = params["email"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val password = params["password"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                val user = dbQuery {

                    Users.selectAll().where { Users.email eq email }.firstOrNull()
                        ?.let { resultRow ->
                            User(
                                resultRow[Users.name],
                                resultRow[Users.email],
                                resultRow[Users.passwordHash]
                            )
                        }
                }

                if (user != null && BCrypt.checkpw(password, user.passwordHash)) {
                    call.sessions.set(UserSession(email = user.email))
                    call.respondRedirect("/home")
                } else {
                    call.respondRedirect("/login?error=Invalid credentials")
                }
            }
        }


        authenticate("auth-session") {
            get("/home") {
                val session = call.principal<UserSession>()!!
                val user = dbQuery {
                    Users.selectAll().where { Users.email eq session.email }.firstOrNull()
                        ?.let { User(it[Users.name], it[Users.email], "******") }
                }

                if (user == null) {
                    call.sessions.clear<UserSession>()
                    call.respondRedirect("/login")
                    return@get
                }


                val tournaments = dbQuery {
                    Tournaments.selectAll().map { it: ResultRow ->
                        Tournament(
                            id = it[Tournaments.id] ?: throw IllegalStateException("ID cannot be null"),
                            name = it[Tournaments.name] ?: "No Name",
                            description = it[Tournaments.description] ?: "",
                            format = it[Tournaments.format].name ?: "Unknown Format",
                            creatorId = it[Tournaments.creatorId] ?: 0,
                            creationDate = it[Tournaments.creationDate].toLocalDate()?.toString() ?: "Unknown Date"
                        )
                    } ?: emptyList()
                }
                call.respond(ThymeleafContent("home", mapOf("tournaments" to tournaments)))

                route("/tournaments") {
                    get("/new") {
                        call.respond(ThymeleafContent("tournaments/create", emptyMap()))
                    }
                    post("/new") {
                        val session = call.principal<UserSession>()!!
                        val params = call.receiveParameters()
                        val name = params["name"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val description = params["description"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val format = TournamentFormat.valueOf(
                            params["format"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        )


                        val creator = dbQuery {
                            Users.selectAll().where { Users.email eq session.email }.single()
                        }
                        val creatorId = creator[Users.id]


                        dbQuery {
                            Tournaments.insert {
                                it[Tournaments.name] = name
                                it[Tournaments.description] = description
                                it[Tournaments.format] = format
                                it[Tournaments.creatorId] = creatorId
                            }
                        }

                        call.respondRedirect("/home")
                    }

                    get("/{id}") {
                        val session = call.principal<UserSession>()!!
                        val tournamentId =
                            call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)

                        var tournamentData: Tournament? = null
                        var creatorName: String? = null
                        var participants = listOf<String>()
                        var isCreator = false
                        var isParticipant = false


                        dbQuery {
                            val currentUser = Users.selectAll().where { Users.email eq session.email }.single()
                            val currentUserId = currentUser[Users.id]


                            val result = (Tournaments innerJoin Users)
                                .selectAll().where { Tournaments.id eq tournamentId }
                                .firstOrNull()

                            if (result == null) return@dbQuery
 if (result[Tournaments.creatorId] != currentUserId) return@dbQuery

                            tournamentData = Tournament(
                                id = result[Tournaments.id],
                                name = result[Tournaments.name],
                                description = result[Tournaments.description],
                                format = result[Tournaments.format].name,
                                creatorId = result[Tournaments.creatorId],
                                creationDate = result[Tournaments.creationDate].toLocalDate().toString()
                            )
                            creatorName = result[Users.name]


                            participants = (Participants innerJoin Users)
                                .selectAll().where { Participants.tournamentId eq tournamentId }
                                .map { it[Users.name] }


                            isCreator = tournamentData!!.creatorId == currentUserId
                            isParticipant = Participants
                                .selectAll()
                                .where { (Participants.tournamentId eq tournamentId) and (Participants.userId eq currentUserId) }
                                .count() > 0
                        }

                        if (tournamentData == null) {
                            return@get call.respond(HttpStatusCode.NotFound, "Tournament not found")
                        }


                        call.respond(
                            ThymeleafContent(
                                "tournaments/details", mapOf(
                                    "tournament" to tournamentData!!,
                                    "creatorName" to creatorName!!,
                                    "participants" to participants,
                                    "isCreator" to isCreator,
                                    "isParticipant" to isParticipant,
                                    "canJoin" to (!isCreator && !isParticipant)
                                )
                            )
                        )
                    }

                    post("/{id}/join") {
                        val session = call.principal<UserSession>()!!
                        val tournamentId =
                            call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)

                        val currentUser = dbQuery { Users.selectAll().where { Users.email eq session.email }.single() }
                        val tournament =
                            dbQuery { Tournaments.selectAll().where { Tournaments.id eq tournamentId }.single() }


                        if (tournament[Tournaments.creatorId] == currentUser[Users.id]) {
                            return@post call.respond(
                                HttpStatusCode.Forbidden,
                                "As the creator, you cannot join your own tournament."
                            )
                        }

                        dbQuery {
                            Participants.insert {
                                it[userId] = currentUser[Users.id]
                                it[Participants.tournamentId] = tournamentId
                            }
                        }
                        call.respondRedirect("/tournaments/$tournamentId")
                    }

                    post("/{id}/leave") {
                        val session = call.principal<UserSession>()!!
                        val tournamentId =
                            call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
                        val currentUserId =
                            dbQuery { Users.selectAll().where { Users.email eq session.email }.single()[Users.id] }

                        dbQuery {
                            Participants.deleteWhere { (Participants.tournamentId eq tournamentId) and (userId eq currentUserId) }
                        }
                        call.respondRedirect("/tournaments/$tournamentId")
                    }

                    post("/{id}/delete") {
                        val session = call.principal<UserSession>()!!
                        val tournamentId =
                            call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
                        val currentUserId =
                            dbQuery { Users.selectAll().where { Users.email eq session.email }.single()[Users.id] }
                        val tournament =
                            dbQuery { Tournaments.selectAll().where { Tournaments.id eq tournamentId }.singleOrNull() }
                                ?: return@post call.respond(HttpStatusCode.NotFound)


                        if (tournament[Tournaments.creatorId] != currentUserId) {
                            return@post call.respond(
                                HttpStatusCode.Forbidden,
                                "You are not the creator of this tournament."
                            )
                        }

                        dbQuery {

                            Participants.deleteWhere { Participants.tournamentId eq tournamentId }

                            Tournaments.deleteWhere { Tournaments.id eq tournamentId }
                        }
                        call.respondRedirect("/home")
                    }

                    get("/{id}/edit") {
                        val session = call.principal<UserSession>()!!
                        val tournamentId =
                            call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.NotFound)
                        val currentUserId =
                            dbQuery { Users.selectAll().where { Users.email eq session.email }.single()[Users.id] }

                        val tournament = dbQuery {
                            Tournaments.selectAll().where { Tournaments.id eq tournamentId }.firstOrNull()
                        } ?: return@get call.respond(HttpStatusCode.NotFound)


                        if (tournament[Tournaments.creatorId] != currentUserId) {
                            return@get call.respond(HttpStatusCode.Forbidden)
                        }

                        val tournamentData = Tournament(
                            id = tournament[Tournaments.id],
                            name = tournament[Tournaments.name],
                            description = tournament[Tournaments.description],
                            format = tournament[Tournaments.format].name,
                            creatorId = tournament[Tournaments.creatorId],
                            creationDate = ""
                        )
                        val allFormats = TournamentFormat.values()

                        call.respond(ThymeleafContent("tournaments/edit", mapOf("tournament" to tournamentData, "allFormats" to allFormats)))
                    }

                    post("/{id}/edit") {
                        val session = call.principal<UserSession>()!!
                        val tournamentId =
                            call.parameters["id"]?.toIntOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
                        val currentUserId =
                            dbQuery { Users.selectAll().where { Users.email eq session.email }.single()[Users.id] }

                        val tournament =
                            dbQuery { Tournaments.selectAll().where { Tournaments.id eq tournamentId }.singleOrNull() }
                                ?: return@post call.respond(HttpStatusCode.NotFound)


                        if (tournament[Tournaments.creatorId] != currentUserId) {
                            return@post call.respond(HttpStatusCode.Forbidden)
                        }

                        val params = call.receiveParameters()
                        val newName = params["name"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val newDescription =
                            params["description"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        val newFormat = TournamentFormat.valueOf(
                            params["format"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                        )

                        dbQuery {
                            Tournaments.update({ Tournaments.id eq tournamentId }) {
                                it[name] = newName
                                it[description] = newDescription
                                it[format] = newFormat
                            }
                        }
                        call.respondRedirect("/tournaments/$tournamentId")
                    }
                }
            }

        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/login")
        }
    }
}




