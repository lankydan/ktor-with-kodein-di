package dev.lankydan.ktor.web

import dev.lankydan.ktor.data.Person
import dev.lankydan.ktor.repository.PersonRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.*

private enum class PeopleFilter {
  ALL, SAVES, DELETES
}

// pretty much every method is an extension function
fun Routing.people(personRepository: PersonRepository) {
  route("/people") {
    get {
      call.respond(HttpStatusCode.OK, personRepository.findAll())
    }
    get("/{id}") {
      val id = UUID.fromString(call.parameters["id"]!!)
      personRepository.find(id)?.let {
        call.respond(HttpStatusCode.OK, it)
      } ?: call.respondText(status = HttpStatusCode.NotFound) { "There is no record with id: $id" }

    }
    post {
      val person = call.receive<Person>()
      val result = personRepository.save(person.copy(id = UUID.randomUUID()))
      call.respond(result)
    }
    put("/{id}") {
      val id = UUID.fromString(call.parameters["id"]!!)
      val person = call.receive<Person>().copy(id = id)
      when {
        personRepository.exists(id) -> call.respond(
          HttpStatusCode.OK,
          personRepository.save(person)
        )
        else -> call.respondText(status = HttpStatusCode.NotFound) { "There is no record with id: ${person.id}" }
      }
    }
    delete("/{id}") {
      val id = UUID.fromString(call.parameters["id"]!!)
      if (personRepository.exists(id)) {
        call.respond(HttpStatusCode.NoContent, personRepository.delete(id))
      } else {
        call.respondText(status = HttpStatusCode.NotFound) { "There is no record with id: $id" }
      }
    }
    webSocket {
      var filter = PeopleFilter.ALL
      try {
        launch {
          for (frame in incoming) {
            (frame as? Frame.Text)?.let { text ->
              filter = when (text.readText()) {
                "saves" -> PeopleFilter.SAVES
                "deletes" -> PeopleFilter.DELETES
                "stop" -> {
                  close()
                  return@let
                }
                else -> PeopleFilter.ALL
              }
            }
          }
        }

        personRepository.updates.collect { (id, person) ->
          when (person) {
            null -> {
              if (filter == PeopleFilter.ALL || filter == PeopleFilter.DELETES) {
                println("delete")
                outgoing.send(Frame.Text("Deleted person [$id]"))
                close()
              }
            }
            else -> {
              if (filter == PeopleFilter.ALL || filter == PeopleFilter.SAVES) {
                println("save")
                outgoing.send(Frame.Text("Saved person [$id] $person"))
              }
            }
          }
        }
      } finally {
        println("closed websocket")
      }
    }
  }
}