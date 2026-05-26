package cl.aiep.subtitulos.audio

import java.text.Normalizer

object CaptionProcessor {
    private val duplicateSpaces = Regex("\\s+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]")

    private val normalizations: List<Pair<Regex, String>> = listOf(
        Regex("\\ba\\s*[&y]\\s*e\\s*[&y]?\\s*p\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\ba\\s*[&y]\\s*e\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\ba\\s*i\\s*e\\s*p\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\ba\\s*e\\s*i\\s*p\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\bayepe?\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\baiepe?\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\baip\\b", RegexOption.IGNORE_CASE) to "AIEP",
        Regex("\\bc\\s*f\\s*t\\b", RegexOption.IGNORE_CASE) to "CFT",
        Regex("\\bi\\s*p\\b", RegexOption.IGNORE_CASE) to "IP",
        Regex("\\bduoc\\s*u\\s*c\\b", RegexOption.IGNORE_CASE) to "Duoc UC",
        Regex("\\binacap\\b", RegexOption.IGNORE_CASE) to "INACAP",
    )

    fun normalize(text: String): String {
        var out = text.trim()
        if (out.isEmpty()) return ""
        for ((regex, replacement) in normalizations) {
            out = regex.replace(out, replacement)
        }
        return out.replace(duplicateSpaces, " ").trim()
    }

    fun words(text: String): List<String> =
        text.trim().split(duplicateSpaces).filter { it.isNotBlank() }

    fun wordKey(word: String): String =
        Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .let { nonWord.replace(it, "") }

    fun findWordOverlap(left: List<String>, right: List<String>): Int {
        val max = minOf(left.size, right.size)
        for (size in max downTo 1) {
            var ok = true
            for (index in 0 until size) {
                if (wordKey(left[left.size - size + index]) != wordKey(right[index])) {
                    ok = false
                    break
                }
            }
            if (ok) return size
        }
        return 0
    }

    fun mergeLine(
        previous: String,
        incoming: String,
        withinGap: Boolean,
    ): MergeResult {
        val previousWords = words(previous)
        val incomingWords = words(incoming)
        if (incomingWords.isEmpty()) return MergeResult.Skip

        val overlap = findWordOverlap(previousWords, incomingWords)
        if (overlap == incomingWords.size) return MergeResult.Skip

        if (overlap > 0 || withinGap) {
            val tail = incomingWords.drop(overlap).joinToString(" ").trim()
            if (tail.isEmpty()) return MergeResult.Skip
            return MergeResult.Merged("$previous $tail".replace(duplicateSpaces, " ").trim())
        }

        return MergeResult.NewLine(incoming)
    }
}

sealed interface MergeResult {
    data object Skip : MergeResult
    data class Merged(val text: String) : MergeResult
    data class NewLine(val text: String) : MergeResult
}
