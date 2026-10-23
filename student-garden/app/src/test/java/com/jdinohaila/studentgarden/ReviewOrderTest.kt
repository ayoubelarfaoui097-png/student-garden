package com.jdinohaila.studentgarden

import com.jdinohaila.studentgarden.domain.reviewOrder
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ReviewOrderTest {
    private val ids = (1..20).map { "card-$it" }

    @Test fun freshSessionsShuffleAllCardsWithoutDuplicates() {
        val sessions = (0..5).map { reviewOrder(emptyList(), ids, Random(it)) }
        sessions.forEach { order ->
            assertEquals(ids.size, order.size)
            assertEquals(ids.toSet(), order.toSet())
        }
        assertEquals(sessions.size, sessions.distinct().size)
        assertTrue(sessions.any { it != ids && it != ids.reversed() })
    }

    @Test fun refreshesDoNotReorderTheActiveCardOrPendingCards() {
        val initial = reviewOrder(emptyList(), ids, Random(3))
        assertEquals(initial, reviewOrder(initial, ids.reversed(), Random(4)))
        assertEquals(initial, reviewOrder(initial, ids, Random(5)))
    }

    @Test fun newlyDueAndReturningLearningCardsJoinAfterThoseAlreadyWaiting() {
        val current = listOf("one", "two", "three")
        val afterRating = reviewOrder(current, listOf("two", "three"), Random(1))
        assertEquals(listOf("two", "three"), afterRating)
        assertEquals(listOf("two", "three", "one"), reviewOrder(afterRating, current, Random(2)))
        val expanded = reviewOrder(current, current + ids, Random(3))
        assertEquals(current, expanded.take(3))
        assertEquals((current + ids).toSet(), expanded.toSet())
    }

    @Test fun removedCardsAndDuplicateIdsCannotRemainInTheQueue() {
        assertEquals(listOf("keep", "new"), reviewOrder(listOf("gone", "keep", "keep"), listOf("new", "keep", "new"), Random(7)))
        assertTrue(reviewOrder(ids, emptyList()).isEmpty())
    }
}
