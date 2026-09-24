package com.pedro.tone3000m1.domain.usecase

import com.pedro.tone3000m1.domain.repository.AudioRoutingRepository

/** Coordinates persisted input and output route selections with the audio engine. */
internal class AudioRoutingUseCase(
    private val repository: AudioRoutingRepository,
) {
    fun cycleInput(): Int = repository.cycleInput()

    fun cycleOutput(): Int = repository.cycleOutput()

    fun restoreSavedRoutes() = repository.restoreSavedRoutes()

    fun routingInfo(): String = repository.routingInfo()
}
