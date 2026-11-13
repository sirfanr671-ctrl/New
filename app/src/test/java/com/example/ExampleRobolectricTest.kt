package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.system.DeviceActionController
import com.example.voice.WakeWordDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readAppNameAndWelcomeFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("CSI", context.getString(R.string.app_name))
        assertEquals("Welcome S Irfan", context.getString(R.string.welcome_irfan))
    }

    @Test
    fun wakeWordDetectorMatchesPrimaryAndFuzzyProfiles() {
        val exact = WakeWordDetector.matchWakeWord(
            transcript = "Hey Voice Master turn flashlight on",
            primaryWakeWord = "Hey Voice Master",
            secondaryWakeWordsCsv = "Hey CSI"
        )
        assertTrue(exact.detected)
        assertEquals("turn flashlight on", exact.remainderCommand)

        val secondary = WakeWordDetector.matchWakeWord(
            transcript = "Hey CSI good morning",
            primaryWakeWord = "Hey Voice Master",
            secondaryWakeWordsCsv = "Hey CSI"
        )
        assertTrue(secondary.detected)
        assertEquals("good morning", secondary.remainderCommand)
    }

    @Test
    fun otpPrivacyDetectorBlocksSensitiveCodes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ctrl = DeviceActionController(context)
        assertTrue(ctrl.isSensitiveOtpText("Your bank OTP is 491203. Do not share."))
        assertTrue(ctrl.isSensitiveOtpText("Verification code 8421 for login"))
        assertFalse(ctrl.isSensitiveOtpText("Hey Irfan, are we meeting at 5 pm?"))
    }
}
