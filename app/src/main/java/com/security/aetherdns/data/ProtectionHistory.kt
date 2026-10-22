package com.security.aetherdns.data

import java.io.File

enum class HistoryAction { ENABLED, DISABLED, PAUSED, RESUMED, RESOLVER_CHANGED }

/** Asal perubahan; [SYSTEM] = di luar AetherDNS (Settings HP), dicatat saat aplikasi mendeteksinya. */
enum class ChangeSource { APP, TILE, SHORTCUT, AUTOMATION, SCHEDULE, SYSTEM, AUTOPILOT }

data class HistoryEvent(val timestamp: Long, val action: HistoryAction, val source: ChangeSource, val resolver: String)

/** Ringkasan status yang dibandingkan antar-pengamatan. */
data class ProtectionSnapshot(val active: Boolean, val specifier: String, val paused: Boolean) {
    companion object {
        fun of(state: DnsState) = ProtectionSnapshot(state.isProtectionActive, state.specifier.trim(), state.pausedUntil != null)
    }
}

/** Logika murni: perubahan status → kejadian riwayat. Diuji di `ProtectionHistoryTest`. */
object HistoryTracker {
    fun transition(previous: ProtectionSnapshot?, next: ProtectionSnapshot, source: ChangeSource, now: Long): HistoryEvent? {
        if (previous == null || previous == next) return null
        val action = when {
            !previous.active && next.active -> if (previous.paused) HistoryAction.RESUMED else HistoryAction.ENABLED
            previous.active && !next.active -> if (next.paused) HistoryAction.PAUSED else HistoryAction.DISABLED
            previous.active && next.active && !previous.specifier.equals(next.specifier, ignoreCase = true) ->
                HistoryAction.RESOLVER_CHANGED
            else -> return null
        }
        return HistoryEvent(now, action, source, next.specifier.ifEmpty { previous.specifier })
    }

    fun counts(events: List<HistoryEvent>, now: Long, windowMs: Long): Map<HistoryAction, Int> =
        events.filter { now - it.timestamp <= windowMs }.groupingBy { it.action }.eachCount()
}

object HistoryCodec {
    fun encode(event: HistoryEvent) =
        "E\t${event.timestamp}\t${event.action}\t${event.source}\t${event.resolver.replace('\t', ' ').replace('\n', ' ')}"

    fun decode(line: String): HistoryEvent? = runCatching {
        val p = line.split('\t')
        if (p[0] != "E") return null
        HistoryEvent(p[1].toLong(), HistoryAction.valueOf(p[2]), ChangeSource.valueOf(p[3]), p.getOrElse(4) { "" })
    }.getOrNull()

    fun encodeSnapshot(s: ProtectionSnapshot) = "S\t${s.active}\t${s.paused}\t${s.specifier.replace('\t', ' ').replace('\n', ' ')}"

    fun decodeSnapshot(line: String): ProtectionSnapshot? = runCatching {
        val p = line.split('\t')
        if (p[0] != "S") return null
        ProtectionSnapshot(p[1].toBooleanStrict(), p.getOrElse(3) { "" }, p[2].toBooleanStrict())
    }.getOrNull()
}

interface HistoryStore {
    fun lastSnapshot(): ProtectionSnapshot?
    fun saveSnapshot(snapshot: ProtectionSnapshot)
    fun append(event: HistoryEvent)
    fun events(): List<HistoryEvent>
    fun clear()
}

/** `noBackupFilesDir/history.tsv` (kejadian) + `history_state` (status terakhir yang diketahui). */
class FileHistoryStore(dir: File, private val now: () -> Long = System::currentTimeMillis) : HistoryStore {
    private val eventsFile = File(dir, "history.tsv")
    private val snapshotFile = File(dir, "history_state")

    @Synchronized
    override fun lastSnapshot(): ProtectionSnapshot? =
        runCatching { HistoryCodec.decodeSnapshot(snapshotFile.readText().trim()) }.getOrNull()

    @Synchronized
    override fun saveSnapshot(snapshot: ProtectionSnapshot) {
        runCatching { snapshotFile.writeText(HistoryCodec.encodeSnapshot(snapshot)) }
    }

    @Synchronized
    override fun append(event: HistoryEvent) {
        val kept = events().filter { now() - it.timestamp <= MAX_AGE_MS }.takeLast(MAX_EVENTS - 1) + event
        runCatching { eventsFile.writeText(kept.joinToString("\n") { HistoryCodec.encode(it) }) }
    }

    @Synchronized
    override fun events(): List<HistoryEvent> = runCatching {
        if (!eventsFile.exists()) emptyList() else eventsFile.readLines().mapNotNull { HistoryCodec.decode(it) }
    }.getOrDefault(emptyList())

    /** Hanya kejadian; status terakhir tetap disimpan agar perubahan berikutnya tetap terdeteksi. */
    @Synchronized
    override fun clear() {
        runCatching { eventsFile.delete() }
    }

    companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_EVENTS = 500
    }
}

/** Untuk uji dan pemanggil tanpa penyimpanan. */
class InMemoryHistoryStore : HistoryStore {
    private var snapshot: ProtectionSnapshot? = null
    private val list = mutableListOf<HistoryEvent>()
    override fun lastSnapshot() = snapshot
    override fun saveSnapshot(snapshot: ProtectionSnapshot) { this.snapshot = snapshot }
    override fun append(event: HistoryEvent) { list += event }
    override fun events(): List<HistoryEvent> = list.toList()
    override fun clear() = list.clear()
}
