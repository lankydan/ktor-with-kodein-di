package dev.lankydan.ktor.web

import dev.lankydan.ktor.repository.PersonRepository
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import java.util.*

data class Person(val id: UUID?, val firstName: String, val lastName: String, val age: Int, val job: String) {
  constructor(firstName: String, lastName: String, age: Int, job: String) : this(null, firstName, lastName, age, job)
}

fun Routing.people(personRepository: PersonRepository) {
  route("/people") {
    get {
      context.respond(HttpStatusCode.OK, personRepository.findAll())
    }
    get("/{id}") {
      val id = UUID.fromString(call.parameters["id"]!!)
      personRepository.find(id)?.let {
        context.respond(HttpStatusCode.OK, it)
      } ?: context.respondText(status = HttpStatusCode.NotFound) { "There is no record with id: $id" }
    }
    post {
      val person = call.receive<Person>()
      val result = personRepository.save(person.copy(id = UUID.randomUUID()))
      context.respond(result)
    }
    put {
      val person = call.receive<Person>()
      when {
        person.id == null -> context.respondText(status = HttpStatusCode.BadRequest) { "Id is missing" }
        personRepository.exists(person.id) -> context.respond(HttpStatusCode.OK, personRepository.save(person))
        else -> context.respondText(status = HttpStatusCode.NotFound) { "There is no record with id: ${person.id}" }
      }
    }
    delete("/{id}") {
      val id = UUID.fromString(call.parameters["id"]!!)
      if (personRepository.exists(id)) {
        context.respond(HttpStatusCode.NoContent, personRepository.delete(id))
      } else {
        context.respondText(status = HttpStatusCode.NotFound) { "There is no record with id: ${id}" }
      }
    }
  }
}