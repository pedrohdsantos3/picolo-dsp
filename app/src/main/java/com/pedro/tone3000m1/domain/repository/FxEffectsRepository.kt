package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.FxImpulseEntry

internal interface FxEffectsRepository {
    fun readImpulseChain(): MutableList<FxImpulseEntry>
    fun persistImpulseChain(entries: List<FxImpulseEntry>)
}
