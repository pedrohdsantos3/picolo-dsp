package com.pedro.tone3000m1.domain.repository

internal interface AudioRoutingRepository {
    suspend fun cycleInput(): Int
    suspend fun cycleOutput(): Int
    suspend fun restoreSavedRoutes()
    fun routingInfo(): String
}
