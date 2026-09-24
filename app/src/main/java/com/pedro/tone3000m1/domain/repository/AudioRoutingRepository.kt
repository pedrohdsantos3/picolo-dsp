package com.pedro.tone3000m1.domain.repository

internal interface AudioRoutingRepository {
    fun cycleInput(): Int
    fun cycleOutput(): Int
    fun restoreSavedRoutes()
    fun routingInfo(): String
}
