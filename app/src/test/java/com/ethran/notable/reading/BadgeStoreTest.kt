package com.ethran.notable.reading

import com.ethran.notable.reading.badges.BadgeStoreCodec
import com.ethran.notable.reading.badges.EarnedRecord
import com.ethran.notable.reading.badges.InMemoryBadgeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeStoreTest {
    @Test fun codecRoundTripsKeepingOrderDatesAndTheBacklogFlag() {
        val m = linkedMapOf("__init__" to EarnedRecord(5L, true), "streak_1" to EarnedRecord(1_762_000_000_000L), "sprint" to EarnedRecord(7L, false))
        val text = BadgeStoreCodec.encode(m)
        assertEquals(m, BadgeStoreCodec.decode(text))
        assertEquals(m.keys.toList(), BadgeStoreCodec.decode(text).keys.toList())
    }

    @Test fun emptyOrMissingTextIsAnEmptyStore() {
        assertTrue(BadgeStoreCodec.decode(null).isEmpty())
        assertTrue(BadgeStoreCodec.decode("").isEmpty())
        assertTrue(BadgeStoreCodec.decode("  \n ").isEmpty())
        assertEquals("", BadgeStoreCodec.encode(emptyMap()))
    }

    @Test fun anUnreadableLineIsSkippedAndTheOthersKept() {
        val text = "streak_1|100|0\nbroken\nsprint|notanumber|0\n|5|0\nmarathoner|200|1\nextra|1|0|9"
        val m = BadgeStoreCodec.decode(text)
        assertEquals(listOf("streak_1", "marathoner"), m.keys.toList())
        assertEquals(EarnedRecord(200L, true), m["marathoner"])
    }

    @Test fun inMemoryStoreKeepsWhatWasSavedAndCopiesIt() {
        val store = InMemoryBadgeStore()
        val m = linkedMapOf("a" to EarnedRecord(1L))
        store.save(m)
        m["b"] = EarnedRecord(2L)                 // a later change of the caller's map does not leak in
        assertEquals(setOf("a"), store.load().keys)
        assertEquals(1, store.saves)
    }
}
