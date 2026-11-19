package com.digitumdei.shotquill.shared.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstagramExtrasTest {
    @Test
    fun instagramNamesAreTidiedAndAnythingElseIsRefused() {
        assertEquals("some_friend", instagramNameOrNull("  @Some_Friend "))
        assertEquals("a.b", instagramNameOrNull("a.b"))
        assertNull(instagramNameOrNull("craft-beer"))
        assertNull(instagramNameOrNull("two words"))
        assertNull(instagramNameOrNull("a..b"))
        assertNull(instagramNameOrNull(".hidden"))
        assertNull(instagramNameOrNull("trailing."))
        assertNull(instagramNameOrNull("x".repeat(31)))
        assertNull(instagramNameOrNull("@"))
        assertNull(instagramNameOrNull(""))
    }

    @Test
    fun collaboratorsAreReadFromWhatWasTypedAndLimited() {
        assertEquals(listOf("Some_Friend", "another.one"), parseCollaborators(" @Some_Friend, another.one  @some_friend "))
        assertTrue(parseCollaborators("  ,  ").isEmpty())

        assertNull(collaboratorsProblem(""))
        assertNull(collaboratorsProblem("@a @b, c"))
        assertEquals(
            "Instagram allows up to 3 collaborators and this has 4.",
            collaboratorsProblem("a b c d"),
        )
        assertEquals("\"craft-beer\" is not an Instagram name.", collaboratorsProblem("a craft-beer"))
    }

    @Test
    fun photoTagsSurviveBeingWrittenAndReadAndDamagedOnesAreDropped() {
        val tags = listOf(PhotoTag("some_friend", 0.25f, 0.75f), PhotoTag("another.one"))

        assertEquals(tags, PhotoTagCodec.decode(PhotoTagCodec.encode(tags)))
        assertTrue(PhotoTagCodec.decode("").isEmpty())
        assertEquals(
            listOf(PhotoTag("kept", 1f, 0f)),
            PhotoTagCodec.decode("kept:7:-2;no-position;bad name:0.1:0.1;half:0.5;nan:NaN:0.5"),
        )
    }
}
