package com.security.aetherdns

import com.security.aetherdns.data.ProfileCodec
import com.security.aetherdns.data.ProfileRepository
import com.security.aetherdns.data.ProfileSaveResult
import com.security.aetherdns.data.ProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {

    private class MemoryStore(var value: String? = null) : ProfileStore {
        override fun read() = value
        override fun write(encoded: String) { value = encoded }
    }

    @Test
    fun savedProfilesSurviveReload() {
        val store = MemoryStore()
        ProfileRepository(store).add("Rumah – Pi-hole", "  DNS.Home.Example.com. ")

        val reloaded = ProfileRepository(store).profiles.value
        assertEquals(1, reloaded.size)
        assertEquals("Rumah – Pi-hole", reloaded[0].name)
        assertEquals("dns.home.example.com", reloaded[0].hostname)
        assertTrue(reloaded[0].isCustom)
    }

    @Test
    fun rejectsInvalidAndDuplicateHostnames() {
        val repo = ProfileRepository(MemoryStore())
        assertTrue(repo.add("x", "https://dns.example.com") is ProfileSaveResult.Failure)
        assertTrue(repo.add("a", "dns.example.com") is ProfileSaveResult.Saved)
        assertTrue(repo.add("b", "DNS.example.com") is ProfileSaveResult.Failure)
        assertEquals(1, repo.profiles.value.size)
    }

    @Test
    fun blankNameFallsBackToHostname() {
        val saved = ProfileRepository(MemoryStore()).add("   ", "dns.example.com") as ProfileSaveResult.Saved
        assertEquals("dns.example.com", saved.profile.name)
    }

    @Test
    fun removeDeletesOnlyThatProfile() {
        val repo = ProfileRepository(MemoryStore())
        val a = (repo.add("a", "a.example.com") as ProfileSaveResult.Saved).profile
        repo.add("b", "b.example.com")
        repo.remove(a.id)
        assertEquals(listOf("b.example.com"), repo.profiles.value.map { it.hostname })
    }

    @Test
    fun codecStripsSeparatorsFromNamesAndSkipsCorruptLines() {
        val encoded = ProfileCodec.encode(listOf(ProfileCodec.customProfile("id1", "Nama\tdengan\ntab", "dns.example.com")))
        val decoded = ProfileCodec.decode(encoded + "\nrusak\nid2\tx\thttps://bad")
        assertEquals(1, decoded.size)
        assertEquals("Nama dengan tab", decoded[0].name)
    }
}
