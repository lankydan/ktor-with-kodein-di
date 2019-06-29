package dev.lankydan.ktor

import com.datastax.oss.driver.api.core.CqlSession
import dev.lankydan.ktor.repository.PersonRepository
import dev.lankydan.ktor.repository.cassandraSession
import dev.lankydan.ktor.web.people
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpHeaders
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.slf4j.event.Level

fun main() {
  startServer()
}

fun startServer() {
  embeddedServer(Netty, 8080, module = Application::module).start()
}

fun Application.module() {
  // Kodein for dependency injection
  // not really that useful here since there are so few dependencies
  val kodein = Kodein {
    bind<CqlSession>() with singleton { cassandraSession() }
    bind<PersonRepository>() with singleton { PersonRepository(instance()) }
  }
  val personRepository by kodein.instance<PersonRepository>()
  // sets the server header (has a default value of the application name if not set)
  install(DefaultHeaders) { header(HttpHeaders.Server, "My ktor server") }
  // controls what level the call logging is logged to
  install(CallLogging) { level = Level.INFO }
  // setup jackson json serialisation
  install(ContentNegotiation) { jackson() }
  // route requests to handler functions
  routing { people(personRepository) }
}