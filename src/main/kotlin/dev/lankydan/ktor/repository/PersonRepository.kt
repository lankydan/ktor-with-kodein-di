package dev.lankydan.ktor.repository

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder
import dev.lankydan.ktor.data.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.*

class PersonRepository(private val session: CqlSession) {

//  private val sink = Sinks
//    .many()
//    .multicast()
//    .directBestEffort<Pair<UUID, Person?>>()

//  val updates: Flow<Pair<UUID, Person?>>
//    get() = sink.asFlux().asFlow()

//  val updates: Flow<Pair<UUID, Person?>> = sink.asFlux().asFlow()

  private val sink = MutableSharedFlow<Pair<UUID, Person?>>(extraBufferCapacity = 1)

  val updates: Flow<Pair<UUID, Person?>> = sink

  fun findAll(): List<Person> {
    return session.execute("SELECT * FROM people")
      .all()
      .map(this::rowToPerson)
  }

  fun find(id: UUID): Person? {
    return session.execute("SELECT * FROM people WHERE id = $id")
      .one()?.run { rowToPerson(this) }
  }

  fun delete(id: UUID) {
    session.execute("DELETE FROM people WHERE id = $id")
//    sink.tryEmitNext(id to null)
    sink.tryEmit(id to null)
  }

  fun save(person: Person): Person {
    session.execute(
      SimpleStatementBuilder(
        """
            INSERT INTO people (id, first_name, last_name, age, job)
            VALUES (:id, :firstName, :lastName, :age, :job)
              """
      ).addNamedValue("id", person.id)
        .addNamedValue("firstName", person.firstName)
        .addNamedValue("lastName", person.lastName)
        .addNamedValue("age", person.age)
        .addNamedValue("job", person.job)
        .build()
    )
//    sink.tryEmitNext(person.id!! to person)
    sink.tryEmit(person.id!! to person)
    return person
  }

  fun exists(id: UUID): Boolean {
    return find(id) != null
  }

  private fun rowToPerson(row: Row) = Person(
    row["id", UUID::class.java],
    row["first_name", String::class.java]!!,
    row["last_name", String::class.java]!!,
    row["age", Int::class.java]!!,
    row["job", String::class.java]!!
  )
}