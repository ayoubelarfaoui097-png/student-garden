package com.jdinohaila.studentgarden.domain

import kotlin.random.Random

/** Shuffle new arrivals once; keep the current card and remaining session order stable. */
fun reviewOrder(current: List<String>, eligible: List<String>, random: Random = Random.Default): List<String> {
    val live = eligible.toSet()
    val remaining = current.distinct().filter { it in live }
    val waiting = remaining.toSet()
    return remaining + eligible.distinct().filter { it !in waiting }.shuffled(random)
}
