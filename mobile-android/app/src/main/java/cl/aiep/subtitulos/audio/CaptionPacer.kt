package cl.aiep.subtitulos.audio

data class CaptionPacerSnapshot(
    val visibleText: String = "",
    val pendingWords: Int = 0,
)

class CaptionPacer(
    private val maxVisibleWords: Int = 18,
    private val maxHistoryWords: Int = 420,
    private val anchorSearchWords: Int = 140,
    private val distinctiveKeyLength: Int = 5,
) {
    private val visibleWords = mutableListOf<String>()
    private val pendingWords = ArrayDeque<String>()
    private val acceptedHistory = mutableListOf<String>()
    private var displayedIsPlaceholder = false

    val snapshot: CaptionPacerSnapshot
        get() = CaptionPacerSnapshot(
            visibleText = visibleWords.joinToString(" "),
            pendingWords = pendingWords.size,
        )

    fun set(text: String, instant: Boolean = false): CaptionPacerSnapshot {
        if (instant) {
            visibleWords.clear()
            visibleWords.addAll(toWords(text))
            pendingWords.clear()
            acceptedHistory.clear()
            acceptedHistory.addAll(visibleWords.filterNot(::isEmptyWord).takeLast(maxHistoryWords))
            displayedIsPlaceholder = true
            return snapshot
        }

        if (displayedIsPlaceholder) {
            visibleWords.clear()
            pendingWords.clear()
            acceptedHistory.clear()
            displayedIsPlaceholder = false
        }

        val incoming = toWords(text).filterNot(::isEmptyWord)
        if (incoming.isEmpty()) return snapshot
        if (containsSequence(acceptedHistory, incoming)) return snapshot

        val overlap = findOverlap(acceptedHistory, incoming)
        val novel = if (overlap > 0) {
            incoming.drop(overlap)
        } else {
            findNovelTailAfterKnownAnchor(acceptedHistory, incoming)
        }
        if (novel.isEmpty()) return snapshot

        pendingWords.addAll(novel)
        acceptedHistory.addAll(novel)
        trimHistory()
        return snapshot
    }

    fun tick(): CaptionPacerSnapshot {
        if (pendingWords.isEmpty()) return snapshot
        if (visibleWords.size >= maxVisibleWords) {
            visibleWords.clear()
        }
        visibleWords.add(pendingWords.removeFirst())
        return snapshot
    }

    fun reset(): CaptionPacerSnapshot {
        visibleWords.clear()
        pendingWords.clear()
        acceptedHistory.clear()
        displayedIsPlaceholder = false
        return snapshot
    }

    private fun toWords(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun isEmptyWord(word: String): Boolean =
        CaptionProcessor.wordKey(word).isEmpty()

    private fun sameWord(a: String, b: String): Boolean =
        CaptionProcessor.wordKey(a) == CaptionProcessor.wordKey(b)

    private fun findOverlap(known: List<String>, incoming: List<String>): Int {
        val max = minOf(known.size, incoming.size)
        for (size in max downTo 1) {
            var ok = true
            for (index in 0 until size) {
                if (!sameWord(known[known.size - size + index], incoming[index])) {
                    ok = false
                    break
                }
            }
            if (ok) return size
        }
        return 0
    }

    private fun findNovelTailAfterKnownAnchor(known: List<String>, incoming: List<String>): List<String> {
        val recentKnown = known.takeLast(anchorSearchWords)
        for (incomingIndex in incoming.indices.reversed()) {
            val incomingKey = CaptionProcessor.wordKey(incoming[incomingIndex])
            if (incomingKey.isEmpty()) continue

            for (knownIndex in recentKnown.indices.reversed()) {
                if (CaptionProcessor.wordKey(recentKnown[knownIndex]) != incomingKey) continue

                var matchLength = 0
                while (
                    incomingIndex - matchLength >= 0 &&
                    knownIndex - matchLength >= 0 &&
                    sameWord(recentKnown[knownIndex - matchLength], incoming[incomingIndex - matchLength])
                ) {
                    matchLength += 1
                }

                val hasContext = matchLength >= 2
                val hasDistinctiveAnchor = incomingKey.length >= distinctiveKeyLength
                if (hasContext || hasDistinctiveAnchor) {
                    return incoming.drop(incomingIndex + 1)
                }
            }
        }
        return incoming
    }

    private fun containsSequence(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (start in 0..haystack.size - needle.size) {
            var ok = true
            for (offset in needle.indices) {
                if (!sameWord(haystack[start + offset], needle[offset])) {
                    ok = false
                    break
                }
            }
            if (ok) return true
        }
        return false
    }

    private fun trimHistory() {
        if (acceptedHistory.size <= maxHistoryWords) return
        val keep = acceptedHistory.takeLast(maxHistoryWords)
        acceptedHistory.clear()
        acceptedHistory.addAll(keep)
    }
}
