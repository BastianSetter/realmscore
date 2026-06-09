package de.morzo.realmscore.domain.game

import de.morzo.realmscore.domain.model.GameParticipant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureOrderingTest {

    private fun participant(profileId: String, seatOrder: Int, lastScanOrder: Int?) =
        GameParticipant(
            gameId = "g1",
            profileId = profileId,
            seatOrder = seatOrder,
            lastScanOrder = lastScanOrder,
        )

    @Test
    fun `orders by lastScanOrder ascending`() {
        val input = listOf(
            participant("c", seatOrder = 0, lastScanOrder = 2),
            participant("a", seatOrder = 1, lastScanOrder = 0),
            participant("b", seatOrder = 2, lastScanOrder = 1),
        )
        val ordered = CaptureOrdering.order(input).map { it.profileId }
        assertEquals(listOf("a", "b", "c"), ordered)
    }

    @Test
    fun `players without a scan order go last, sorted by seatOrder`() {
        val input = listOf(
            participant("new2", seatOrder = 5, lastScanOrder = null),
            participant("scanned", seatOrder = 9, lastScanOrder = 0),
            participant("new1", seatOrder = 1, lastScanOrder = null),
        )
        val ordered = CaptureOrdering.order(input).map { it.profileId }
        assertEquals(listOf("scanned", "new1", "new2"), ordered)
    }

    @Test
    fun `falls back to seatOrder when no scan history exists`() {
        val input = listOf(
            participant("third", seatOrder = 2, lastScanOrder = null),
            participant("first", seatOrder = 0, lastScanOrder = null),
            participant("second", seatOrder = 1, lastScanOrder = null),
        )
        val ordered = CaptureOrdering.order(input).map { it.profileId }
        assertEquals(listOf("first", "second", "third"), ordered)
    }
}
