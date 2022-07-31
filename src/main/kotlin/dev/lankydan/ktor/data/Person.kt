package dev.lankydan.ktor.data

import java.util.*

data class Person(
  val id: UUID?,
  val firstName: String,
  val lastName: String,
  val age: Int,
  val job: String
)