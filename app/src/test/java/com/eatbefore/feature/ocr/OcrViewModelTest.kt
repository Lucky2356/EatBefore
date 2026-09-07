package com.eatbefore.feature.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
 * Robolectric, because the deletion goes through `android.net.Uri` and the app's cache
 * directory: on a bare JVM that class is a stub returning null, the delete quietly does
 * nothing, and a test would pass while the photograph stayed on disk.
 */
@RunWith(RobolectricTestRunner::class)
class OcrViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val candidate = DateCandidate(LocalDate.of(2026, 9, 1), confidence = 0.9f, sourceText = "01.09.2026")

    private fun provider(result: OcrResult = OcrResult("годен до 01.09.2026", listOf(candidate))) =
        object : ExpiryDateOcrProvider {
            override suspend fun recognize(imageUri: String): OcrResult = result
        }

    @Test
    fun `a recognized date reaches the screen with the text it came from`() = runTest {
        val vm = OcrViewModel(provider(), context)

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
        val vm = OcrViewModel(provider(OcrResult("мутный текст", emptyList())), context)

        vm.recognize("file:///tmp/whatever.jpg")
        advanceUntilIdle()

        assertTrue(vm.state.value.hasResult)
        assertTrue(vm.state.value.candidates.isEmpty())
    }

    /** The photo is a means, not a record. Once read, it goes. */
    @Test
    fun `the photograph is deleted once it has been read`() = runTest {
        val photo = File.createTempFile("ocr", ".jpg", context.cacheDir).apply { writeText("not really a jpeg") }
        val vm = OcrViewModel(provider(), context)

        vm.recognize(photo.toURI().toString())
        advanceUntilIdle()

        assertFalse("the captured image must not outlive the recognition", photo.exists())
    }

    /**
     * The other half of that promise, and the more important one. Since a picture can now
     * be chosen from the gallery, the same code path is handed files the app did not
     * create and the user means to keep. Deleting one would be silent and unrecoverable.
     */
    @Test
    fun `a picture from outside the cache is left alone`() = runTest {
        // filesDir rather than a temp file: it is never inside cacheDir, so the test says
        // what it means instead of relying on where the runner happens to put temp files.
        val elsewhere = File(context.filesDir, "not-the-cache").apply { mkdirs() }
        val ownPhoto = File(elsewhere, "photo.jpg").apply { writeText("someone's photograph") }
        val vm = OcrViewModel(provider(), context)

        vm.recognize(ownPhoto.toURI().toString())
        advanceUntilIdle()

        assertTrue("a photo the app did not take must survive being read", ownPhoto.exists())
        assertTrue(vm.state.value.hasResult)
        ownPhoto.delete()
    }

    /** A URI that is not a file on disk must not take the recognition down with it. */
    @Test
    fun `a uri with nothing behind it is survivable`() = runTest {
        val vm = OcrViewModel(provider(), context)

        vm.recognize("content://media/external/images/42")
        advanceUntilIdle()

        assertTrue(vm.state.value.hasResult)
    }

    @Test
    fun `retrying clears the previous answer`() = runTest {
        val vm = OcrViewModel(provider(), context)
        vm.recognize("file:///tmp/whatever.jpg")
        advanceUntilIdle()

        vm.retry()

        assertEquals(OcrUiState(), vm.state.value)
    }
}
