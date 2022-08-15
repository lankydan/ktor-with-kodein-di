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
//      incoming.receiveAsFlow().onEach { frame ->
//        (frame as? Frame.Text)?.let { text ->
//          filter = when (text.readText()) {
//            "saves" -> PeopleFilter.SAVES
//            "deletes" -> {
//              PeopleFilter.DELETES
//            }
//            else -> PeopleFilter.ALL
//          }
//        }
//      }.launchIn(this)

//      launch {
//        for (frame in incoming) {
//          (frame as? Frame.Text)?.let { text ->
//            filter = when (text.readText()) {
//              "saves" -> PeopleFilter.SAVES
//              "deletes" -> PeopleFilter.DELETES
//              "stop" -> return@webSocket // Terminates the WebSocket
//              else -> PeopleFilter.ALL
//            }
//          }
//        }
//      }

      // throwing an exception does not work
//      launch {
//        for (frame in incoming) {
//          (frame as? Frame.Text)?.let { text ->
//            filter = when (text.readText()) {
//              "saves" -> PeopleFilter.SAVES
//              "deletes" -> PeopleFilter.DELETES
//              "stop" -> throw RuntimeException("Finish") // Terminates the WebSocket
//              else -> PeopleFilter.ALL
//            }
//          }
//        }
//      }

      launch {
        for (frame in incoming) {
          (frame as? Frame.Text)?.let { text ->
            filter = when (text.readText()) {
              "saves" -> PeopleFilter.SAVES
              "deletes" -> PeopleFilter.DELETES
              "stop" -> {
                close() // Terminates the WebSocket, but does not return from the [webSocket] handler, so the handler is still live and receives updates
//                throw ClosedReceiveChannelException("closeReason.await()?.message")
//                cancel()
                // ONLY NEED THE CLOSE, HOWEVER IT DOES LET THE NEXT UPDATE STILL EXECUTE
                return@let
//                this.can
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

        val incomingJob = incoming.receiveAsFlow().onEach { frame ->
          (frame as? Frame.Text)?.let { text ->
            filter = when (text.readText()) {
              "saves" -> PeopleFilter.SAVES
              "deletes" -> PeopleFilter.DELETES
              "stop" -> {
                close(CloseReason(CloseReason.Codes.NORMAL, "close this socket")) // Terminates the WebSocket, but does not return from the [webSocket] handler, so the handler is still live and receives updates
                return@let
              }
              else -> PeopleFilter.ALL
            }
          }
        }.launchIn(this)

      // after closing the flow will still execute once when an update comes in unless it is cancelled
      val outgoingJob = personRepository.updates.onEach { (id, person) ->
        when (person) {
          null -> {
            if (filter == PeopleFilter.ALL || filter == PeopleFilter.DELETES) {
              println("delete")
              outgoing.send(Frame.Text("Deleted person [$id]"))
            }
          }
          else -> {
            if (filter == PeopleFilter.ALL || filter == PeopleFilter.SAVES) {
              println("save")
              outgoing.send(Frame.Text("Saved person [$id] $person"))
            }
          }
        }
      }.launchIn(this)

      val reason = closeReason.await()

      incomingJob.cancel()
      outgoingJob.cancel()
      joinAll(incomingJob, outgoingJob)

      println("Closed websocket: ${reason?.message}")
    }
  }
}