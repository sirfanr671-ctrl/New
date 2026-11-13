package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

sealed interface AiStreamEvent {
    data class Chunk(val accumulated: String) : AiStreamEvent
    data class Completed(val fullText: String) : AiStreamEvent
    data class Error(val message: String) : AiStreamEvent
}

class FirebaseAiRepository {

    private var activePersonality: String = "professional"
    private var activeLanguage: String = "en-US"
    private var activeChat: Chat? = null

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(buildSystemInstruction(activePersonality, activeLanguage))
            }
        )
    }

    fun updateContext(personality: String, languageCode: String) {
        if (personality != activePersonality || languageCode != activeLanguage) {
            activePersonality = personality
            activeLanguage = languageCode
            activeChat = null
        }
    }

    fun resetConversation() {
        activeChat = null
    }

    fun streamConversation(userPrompt: String): Flow<AiStreamEvent> = flow {
        val chat = activeChat ?: model.startChat().also { activeChat = it }
        val accumulated = StringBuilder()
        try {
            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(
                    "[Language preference: $activeLanguage | Tone: $activePersonality]\n$userPrompt"
                ).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val result = accumulated.toString().trim()
            if (result.isNotBlank()) {
                emit(AiStreamEvent.Completed(result))
            } else {
                emit(AiStreamEvent.Error("Gemini returned an empty response."))
            }
        } catch (_: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI request timed out")
            emit(AiStreamEvent.Error("AI response timed out after 60 seconds. Check your connection and try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            emit(AiStreamEvent.Error(e.localizedMessage ?: "AI request failed."))
        }
    }

    private fun buildSystemInstruction(personality: String, languageCode: String): String {
        val tone = when (personality) {
            "friendly" -> "warm, encouraging, conversational, and helpful"
            "concise" -> "ultra-concise, direct, and crisp (under 2-3 sentences unless asked for detail)"
            "technical" -> "precise, analytical, and structured"
            else -> "polished, calm, executive, and reliable"
        }
        return """
            You are CSI (Voice Master AI), a personal Android voice assistant serving S Irfan.
            Keep answers spoken-friendly, clear, and well-structured without noisy markdown symbols when read aloud.
            Adapt to the user's spoken language naturally: support English, Telugu (తెలుగు), and Hindi (हिन्दी).
            Current preferred language hint: $languageCode.
            Current assistant personality: $tone.
            Remember context across follow-up questions in this session.
            Never claim to perform impossible OS actions; be honest about Android security boundaries.
        """.trimIndent()
    }

    private companion object {
        const val TAG = "FirebaseAiRepo"
        const val AI_TIMEOUT_MS = 60_000L
    }
}
