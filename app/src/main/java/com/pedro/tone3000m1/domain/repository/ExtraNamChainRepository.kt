package com.pedro.tone3000m1.domain.repository

import com.pedro.tone3000m1.domain.model.ExtraNamEntry

internal interface ExtraNamChainRepository {
    fun readExtraNamChain(): MutableList<ExtraNamEntry>
    fun persistExtraNamChain(entries: List<ExtraNamEntry>)
}
