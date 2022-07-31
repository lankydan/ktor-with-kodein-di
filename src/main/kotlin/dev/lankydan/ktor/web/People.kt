package dev.lankydan.ktor.web

import dev.lankydan.ktor.data.Person
import dev.lankydan.ktor.repository.PersonRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
      incoming.receiveAsFlow().onEach { frame ->
        (frame as? Frame.Text)?.let { text ->
          filter = when(text.readText()) {
            "saves" -> PeopleFilter.SAVES
            "deletes" -> PeopleFilter.DELETES
            else -> PeopleFilter.ALL
          }
        }
      }.launchIn(this)

      personRepository.updates.collect { (id, person) ->
        when (person) {
          null -> {
            if (filter == PeopleFilter.ALL || filter == PeopleFilter.DELETES) {
              outgoing.send(Frame.Text("Deleted person [$id]"))
            }
          }
          else -> {
            if (filter == PeopleFilter.ALL || filter == PeopleFilter.SAVES) {
              outgoing.send(Frame.Text("Saved person [$id] $person"))
            }
          }
        }
      }
    }
  }
}