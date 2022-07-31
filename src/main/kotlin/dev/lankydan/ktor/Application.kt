package dev.lankydan.ktor

import com.datastax.oss.driver.api.core.CqlSession
import dev.lankydan.ktor.repository.PersonRepository
import dev.lankydan.ktor.repository.cassandraSession
import dev.lankydan.ktor.web.people
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.slf4j.event.Level
import java.time.Duration

fun main() {
  embeddedServer(Netty, port = 8080, module = Application::module).start()
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
  // allows websockets to be used
  install(WebSockets) {
    // this == WebSocketOptions
    pingPeriod = Duration.ofSeconds(15)
  }
  // route requests to handler functions
  routing { people(personRepository) }
}