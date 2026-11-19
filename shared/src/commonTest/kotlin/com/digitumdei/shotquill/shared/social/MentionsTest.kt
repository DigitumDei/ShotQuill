package com.digitumdei.shotquill.shared.social

import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentionsTest {
    @Test
    fun findsEveryNameWrittenWithAnAtIncludingBareOnes() {
        val text = "Cheers @BskyBeer.bsky.social, (@doctrinebrewing.co.za) and @bskybeer. Mail me@example.com @bskybeer @ #tag"

        assertEquals(
            listOf("bskybeer.bsky.social", "doctrinebrewing.co.za", "bskybeer"),
            blueskyMentionNames(text),
        )
        assertTrue(blueskyMentionNames("no mentions, just a@b.c and #tags").isEmpty())
    }

    @Test
    fun aBareNameIsLookedUpUnderTheUsualDomain() {
        assertTrue(isFullBlueskyHandle("doctrinebrewing.co.za"))
        assertFalse(isFullBlueskyHandle("bskybeer"))
        assertFalse(isFullBlueskyHandle("name.123"))
        assertEquals("doctrinebrewing.co.za", blueskyHandleToLookUp("doctrinebrewing.co.za"))
        assertEquals("bskybeer.bsky.social", blueskyHandleToLookUp("bskybeer"))
        assertNull(blueskyHandleToLookUp("craft_beer"))
        assertEquals(
            listOf("bskybeer.bsky.social", "doctrinebrewing.co.za"),
            blueskyHandlesToLookUp("@bskybeer @doctrinebrewing.co.za @BSKYBEER.bsky.social @craft_beer"),
        )
    }

    @Test
    fun blueskyMentionsSayWhatWasFound() {
        val text = "@doctrinebrewing.co.za @quiet.bsky.social @nobody.example.com @later.bsky.social @offline.example.org"
        val checks = blueskyMentionChecks(
            text,
            mapOf(
                "doctrinebrewing.co.za" to AccountLookup.Found("doctrinebrewing.co.za", " Dion "),
                "quiet.bsky.social" to AccountLookup.Found("quiet.bsky.social"),
                "nobody.example.com" to AccountLookup.NotFound,
                "offline.example.org" to AccountLookup.Unreachable,
            ),
        )

        assertEquals(
            listOf(
                MentionStatus.Found,
                MentionStatus.Found,
                MentionStatus.NotFound,
                MentionStatus.Checking,
                MentionStatus.Unreachable,
            ),
            checks.map { it.status },
        )
        assertEquals("Dion", checks[0].note)
        assertEquals("https://bsky.app/profile/doctrinebrewing.co.za", checks[0].profileUrl)
        assertEquals("Found", checks[1].note)
        assertTrue(checks[2].note.contains("plain text"))
        assertNull(checks[2].profileUrl)
        assertEquals(listOf(false, false, true, false, false), checks.map { it.needsAttention })
    }

    @Test
    fun aBareBlueskyNameIsAProblemWhateverTheLookupSays() {
        fun check(lookups: Map<String, AccountLookup>) = blueskyMentionChecks("with @bskybeer", lookups).single()

        assertEquals(MentionStatus.Checking, check(emptyMap()).status)

        val exists = check(mapOf("bskybeer.bsky.social" to AccountLookup.Found("bskybeer.bsky.social", "Beer on Bluesky")))
        assertEquals(MentionStatus.Problem, exists.status)
        assertTrue(exists.note.contains("@bskybeer.bsky.social exists (Beer on Bluesky)"))
        assertEquals("https://bsky.app/profile/bskybeer.bsky.social", exists.profileUrl)

        val unnamed = check(mapOf("bskybeer.bsky.social" to AccountLookup.Found("bskybeer.bsky.social")))
        assertTrue(unnamed.note.contains("@bskybeer.bsky.social exists:"))

        val missing = check(mapOf("bskybeer.bsky.social" to AccountLookup.NotFound))
        assertEquals(MentionStatus.Problem, missing.status)
        assertTrue(missing.note.contains("no @bskybeer.bsky.social either"))
        assertTrue(missing.needsAttention)

        assertEquals(MentionStatus.Unreachable, check(mapOf("bskybeer.bsky.social" to AccountLookup.Unreachable)).status)
        assertEquals(MentionStatus.Problem, check(mapOf("bskybeer.bsky.social" to AccountLookup.CannotCheck)).status)

        val notAHandle = blueskyMentionChecks("@craft_beer", emptyMap()).single()
        assertEquals(MentionStatus.Problem, notAHandle.status)
        assertTrue(notAHandle.note.contains("Not a Bluesky handle"))

        val unchecked = blueskyMentionChecks("@a.example.com", mapOf("a.example.com" to AccountLookup.CannotCheck)).single()
        assertEquals(MentionStatus.CannotCheck, unchecked.status)
    }

    @Test
    fun instagramNamesAreCheckedForHowTheyAreWritten() {
        val checks = instagramMentionChecks(
            "Thanks @Doctrine.Brewing_ and @doctrine.brewing_. Not @craft-beer, @a..b, @.hidden or @${"x".repeat(31)} me@example.com",
        )

        assertEquals(
            listOf("Doctrine.Brewing_", "craft-beer", "a..b", ".hidden", "x".repeat(31)),
            checks.map { it.name },
        )
        assertEquals(MentionStatus.CannotCheck, checks[0].status)
        assertEquals("https://www.instagram.com/doctrine.brewing_/", checks[0].profileUrl)
        assertTrue(checks[0].note.contains("no way to check"))
        assertFalse(checks[0].needsAttention)
        assertTrue(checks.drop(1).all { it.status == MentionStatus.Problem && it.profileUrl == null })
        assertTrue(checks[1].note.contains("would link to @craft"))
        assertTrue(checks[2].note.contains("two full stops"))
        assertTrue(checks[3].note.contains("start with a full stop"))
        assertTrue(checks[4].note.contains("at most 30"))
        assertTrue(instagramMentionChecks("No names here. #craftbeer").isEmpty())
    }

    @Test
    fun anUnfinishedMentionAtTheEndIsNoticedAndCanBeCompleted() {
        assertEquals("doct", mentionBeingTyped("Cheers @doct"))
        assertEquals("", mentionBeingTyped("Cheers @"))
        assertEquals("some_fr", mentionBeingTyped("(@some_fr"))
        assertEquals("a.b-c", mentionBeingTyped("@a.b-c"))
        assertNull(mentionBeingTyped("Cheers @doct "))
        assertNull(mentionBeingTyped("mail me@exam"))
        assertNull(mentionBeingTyped("no mention here"))
        assertNull(mentionBeingTyped(""))

        assertEquals("Cheers @doctrinebrewing.co.za ", completeMention("Cheers @doct", "doctrinebrewing.co.za"))
        assertEquals("#craftbeer @some_friend ", completeMention("#craftbeer @", "some_friend"))
        assertEquals("nothing to finish", completeMention("nothing to finish", "someone"))
    }

    @Test
    fun namesAreRememberedInTheFormEachServiceUses() {
        val text = "With @Doctrine_Brewing and @doctrinebrewing.co.za and @bare @craft-beer"

        assertEquals(listOf("doctrinebrewing.co.za"), mentionNamesFor(PublishTarget.Bluesky, text))
        assertEquals(
            listOf("doctrine_brewing", "doctrinebrewing.co.za", "bare"),
            mentionNamesFor(PublishTarget.Instagram, text),
        )

        assertEquals(listOf("new", "Old", "older"), rememberMentions(listOf("Old", "older", "NEW"), listOf("new")))
        assertEquals(listOf("a", "b"), rememberMentions(listOf("b", "c"), listOf("a"), limit = 2))
    }

    @Test
    fun suggestionsPutNamesUsedBeforeFirstThenWhatTheServiceOffers() {
        val offers = suggestMentions(
            typed = "doc",
            usedBefore = listOf("brewdoc.bsky.social", "doctrinebrewing.co.za", "unrelated.example"),
            fromService = listOf(
                AccountSuggestion("doctrinebrewing.co.za", "Dion"),
                AccountSuggestion("doctor.example.com", "A Doctor"),
            ),
        )

        assertEquals(
            listOf(
                AccountSuggestion("doctrinebrewing.co.za", "Dion"),
                AccountSuggestion("brewdoc.bsky.social"),
                AccountSuggestion("doctor.example.com", "A Doctor"),
            ),
            offers,
        )
        // Just after the @ every remembered name is on offer; a name typed in full is not offered again.
        assertEquals(listOf("a", "b"), suggestMentions("", listOf("a", "b")).map { it.handle })
        assertTrue(suggestMentions("a", listOf("A")).isEmpty())
        assertEquals(2, suggestMentions("", (1..9).map { "name$it" }, limit = 2).size)
    }

    @Test
    fun anInstagramNameMetaConfirmsIsShownAsFound() {
        val checks = instagramMentionChecks(
            "With @Some_Friend and @unknown_one",
            mapOf(
                "some_friend" to AccountLookup.Found("some_friend", "Some Friend Brewing"),
                "unknown_one" to AccountLookup.CannotCheck,
            ),
        )

        assertEquals(MentionStatus.Found, checks[0].status)
        assertEquals("Some Friend Brewing", checks[0].note)
        assertEquals("https://www.instagram.com/some_friend/", checks[0].profileUrl)
        assertEquals(MentionStatus.CannotCheck, checks[1].status)
    }

    @Test
    fun blueskySuggestsAccountsForANameBeingTyped() {
        val urls = mutableListOf<String>()
        var reply = HttpResponse(
            200,
            """{"actors":[{"did":"did:plc:a","handle":"doctrinebrewing.co.za","displayName":"Dion"},{"handle":"not a handle"},{"handle":"doctor.bsky.social"}]}""",
        )
        var failure: Exception? = null
        val publisher = BlueskyPublisher(
            settings = InMemoryLocalSettingsRepository(),
            transport = { request ->
                failure?.let { throw it }
                urls += request.url
                reply
            },
        )

        assertEquals(
            listOf(AccountSuggestion("doctrinebrewing.co.za", "Dion"), AccountSuggestion("doctor.bsky.social")),
            publisher.suggestAccounts(" @Doct "),
        )
        assertEquals("https://public.api.bsky.app/xrpc/app.bsky.actor.searchActorsTypeahead?q=doct&limit=6", urls.single())

        // Too short, or something that could not be part of a handle, is never sent.
        assertTrue(publisher.suggestAccounts("d").isEmpty())
        assertTrue(publisher.suggestAccounts("a b&c=d").isEmpty())
        assertEquals(1, urls.size)

        reply = HttpResponse(500, "oops")
        assertTrue(publisher.suggestAccounts("doct").isEmpty())
        reply = HttpResponse(200, "<html>")
        assertTrue(publisher.suggestAccounts("doct").isEmpty())
        failure = IllegalStateException("offline")
        assertTrue(publisher.suggestAccounts("doct").isEmpty())
    }

    @Test
    fun blueskyLooksAccountsUpInItsOpenDirectoryWithoutSigningIn() {
        val requests = mutableListOf<HttpRequest>()
        var reply = HttpResponse(200, """{"did":"did:plc:abc","handle":"doctrinebrewing.co.za","displayName":"Dion"}""")
        var failure: Exception? = null
        val publisher = BlueskyPublisher(
            settings = InMemoryLocalSettingsRepository(),
            transport = { request ->
                failure?.let { throw it }
                requests += request
                reply
            },
        )

        assertEquals(
            AccountLookup.Found("doctrinebrewing.co.za", "Dion"),
            publisher.findAccount(" @DoctrineBrewing.co.za "),
        )
        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor=doctrinebrewing.co.za", request.url)
        assertTrue(request.headers.isEmpty())
        assertNull(request.body)

        reply = HttpResponse(200, """{"did":"did:plc:abc"}""")
        assertEquals(AccountLookup.Found("quiet.bsky.social"), publisher.findAccount("quiet.bsky.social"))

        reply = HttpResponse(400, """{"error":"InvalidRequest","message":"Profile not found"}""")
        assertEquals(AccountLookup.NotFound, publisher.findAccount("bskybeer.bsky.social"))

        reply = HttpResponse(429, """{"error":"RateLimitExceeded"}""")
        assertEquals(AccountLookup.Unreachable, publisher.findAccount("bskybeer.bsky.social"))

        reply = HttpResponse(200, "<html>proxy</html>")
        assertEquals(AccountLookup.Unreachable, publisher.findAccount("bskybeer.bsky.social"))

        failure = IllegalStateException("Unable to resolve host")
        assertEquals(AccountLookup.Unreachable, publisher.findAccount("bskybeer.bsky.social"))

        failure = null
        requests.clear()
        assertEquals(AccountLookup.NotFound, publisher.findAccount("bskybeer"))
        assertTrue(requests.isEmpty())
    }
}
