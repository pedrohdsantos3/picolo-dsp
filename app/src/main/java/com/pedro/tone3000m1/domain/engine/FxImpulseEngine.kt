package com.pedro.tone3000m1.domain.engine

internal interface FxImpulseEngine {
    fun loadImpulseResponse(slot: Int, path: String): String
    fun setImpulseResponseBypass(slot: Int, bypass: Boolean)
    fun setImpulseResponseMix(slot: Int, mix: Float)
    fun setImpulseResponsePosition(slot: Int, namBlocksBefore: Int)
    fun namBlockCount(): Int
    fun start(): String
}
