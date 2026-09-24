package com.pedro.tone3000m1.domain.engine

/** Native audio operations needed by application audio routing. */
internal interface AudioRoutingEngine {
    fun cycleInputChannel(): Int
    fun cycleOutputPair(): Int
    fun setInputChannel(channel: Int)
    fun setOutputPair(pairIndex: Int)
    fun routingInfo(): String
}
