package com.eatbefore.feature.ocr

import com.eatbefore.domain.ocr.DateCandidate
import com.eatbefore.domain.ocr.ExpiryDateOcrProvider
import com.eatbefore.domain.ocr.OcrResult
import com.eatbefore.testutil.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * Reading a date off a photo. The part worth protecting is not the recognition — that is
 * ML Kit's — but what happens to the photograph afterwards: it is a picture taken inside
 * the user's home, and the app promises it does not keep them.
 *
 * Robolectric, because the deletion goes through `android.net.Uri`: on a bare JVM that
 * class is a stub returning null, the delete quietly does nothing, and a test would
 * pass while the photograph stayed on disk.
 */
@RunWith(RobolectricTestRunner::class)
class OcrViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val candidate = DateCandidate(LocalDate.of(2026, 9, 1), confidence = 0.9f, sourceText = "01.09.2026")

    private fun provider(result: OcrResult = OcrResult("годен до 01.09.2026", listOf(candidate))) =
        object : ExpiryDateOcrProvider {
            override suspend fun recognize(imageUri: String): OcrResult = result
        }

    @Test
    fun `a recognized date reaches the screen with the text it came from`() = runTest {
        val vm = OcrViewModel(provider())

        vm.recognize("file:///tmp/whatever.jpg")
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isRecognizing)
        assertTrue(state.hasResult)
        assertEquals("годен до 01.09.2026", state.rawText)
        assertEquals(listOf(candidate), state.candidates)
    }

    /**
     * Recognising nothing is a result too: the screen has to stop spinning and offer
     * another go, rather than sit there looking busy.
     */
    @Test
    fun `finding no date still counts as an answer`() = runTest {
        val vm = OcrViewModel(provider(OcrResult("мутный текст", emptyList())))

        vm.recognize("file:///tmp/whatever.jpg")
        advanceUntilIdle()

        assertTrue(vm.state.value.hasResult)
        assertTrue(vm.state.value.candidates.isEmpty())
    }

    /** The photo is a means, not a record. Once read, it goes. */
    @Test
    fun `the photograph is deleted once it has been read`() = runTest {
        val photo = File.createTempFile("ocr", ".jpg").apply { writeText("not really a jpeg") }
        val vm = OcrViewModel(provider())

        vm.recognize(photo.toURI().toString())
        advanceUntilIdle()

        assertFalse("the captured image must not outlive the recognition", photo.exists())
    }

    /** A URI that is not a file on disk must not take the recognition down with it. */
    @Test
    fun `a uri with nothing behind it is survivable`() = runTest {
        val vm = OcrViewModel(provider())

        vm.recognize("content://media/external/images/42")
        advanceUntilIdle()

        assertTrue(vm.state.value.hasResult)
    }

    @Test
    fun `retrying clears the previous answer`() = runTest {
        val vm = OcrViewModel(provider())
        vm.recognize("file:///tmp/whatever.jpg")
        advanceUntilIdle()

        vm.retry()

        assertEquals(OcrUiState(), vm.state.value)
    }
}
