package com.example.voice

import java.util.Locale

data class WakeWordMatchResult(
    val detected: Boolean,
    val matchedProfile: String = "",
    val remainderCommand: String = ""
)

object WakeWordDetector {

    fun matchWakeWord(
        transcript: String,
        primaryWakeWord: String,
        secondaryWakeWordsCsv: String
    ): WakeWordMatchResult {
        val clean = transcript.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return WakeWordMatchResult(false)

        val profiles = buildList {
            if (primaryWakeWord.isNotBlank()) add(primaryWakeWord.trim())
            secondaryWakeWordsCsv.split(",").forEach { item ->
                val trimmed = item.trim()
                if (trimmed.isNotBlank()) add(trimmed)
            }
        }.distinctBy { it.lowercase(Locale.ROOT) }

        for (profile in profiles) {
            val pLower = profile.lowercase(Locale.ROOT)
            if (clean.startsWith(pLower)) {
                val remainder = clean.removePrefix(pLower).trim(' ', ',', '.', ':')
                return WakeWordMatchResult(true, profile, remainder)
            }
            val idx = clean.indexOf(pLower)
            if (idx >= 0) {
                val remainder = clean.substring(idx + pLower.length).trim(' ', ',', '.', ':')
                return WakeWordMatchResult(true, profile, remainder)
            }

            // Noise-resistant phonetic / fuzzy check for the leading N words
            val profileWords = pLower.split(Regex("\\s+")).filter { it.isNotBlank() }
            val inputWords = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (profileWords.isNotEmpty() && inputWords.size >= profileWords.size) {
                val prefixCandidate = inputWords.take(profileWords.size).joinToString(" ")
                val maxDist = (pLower.length * 0.28).toInt().coerceIn(1, 4)
                if (levenshtein(prefixCandidate, pLower) <= maxDist) {
                    val remainder = inputWords.drop(profileWords.size).joinToString(" ")
                    return WakeWordMatchResult(true, profile, remainder)
                }
            }
        }
        return WakeWordMatchResult(false)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
