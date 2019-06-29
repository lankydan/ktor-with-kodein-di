package dev.lankydan.ktor.repository

import com.datastax.oss.driver.api.core.CqlSession

fun cassandraSession(): CqlSession = CqlSession.builder().build()