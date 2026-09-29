package com.eatbefore.data.catalog

import com.eatbefore.core.datastore.CatalogKeyProblem
import com.eatbefore.core.datastore.GoUpcKeyStore
import com.eatbefore.core.diagnostics.DiagnosticsLog
import com.eatbefore.data.catalog.goupc.GoUpcCatalogProvider
import com.eatbefore.di.NetworkModule
import com.eatbefore.domain.catalog.CatalogResult
import com.eatbefore.domain.catalog.KeyCheckResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How Go-UPC's answers are read. The bodies and status codes are the ones the service
 * documents and returns; the network is replaced by an interceptor, so nothing leaves
 * the machine.
 */
class GoUpcCatalogProviderTest {

    private val requests = mutableListOf<Request>()
    private var status = 200
    private var body = ""

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            requests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        .build()

    private val keyStore = mockk<GoUpcKeyStore>(relaxed = true) {
        coEvery { key() } returns "test-key"
    }

    private val provider = GoUpcCatalogProvider(
        client = client,
        json = NetworkModule.provideJson(),
        keyStore = keyStore,
        diagnostics = mockk<DiagnosticsLog>(relaxed = true),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun answer(code: Int, json: String = "") {
        status = code
        body = json
    }

    @Test
    fun `a hit is found and marked for review`() = runTest {
        answer(
            200,
            """{"code":"4607096006433","codeType":"EAN","product":{"name":"Домик В Деревне Масло 72,5% 180Гр Купить В Алматы",""" +
                """"brand":"Домик в деревне","imageUrl":"https://go-upc.s3.amazonaws.com/images/1.jpeg","upc":4607096006433,""" +
                """"specs":[["Weight","180 g"]]},"inferred":false}""",
        )

        val result = provider.lookupByBarcode("4607096006433")

        val product = (result as CatalogResult.Found).product
        assertEquals("Домик В Деревне Масло 72,5% 180Гр", product.name)
        assertEquals("Домик в деревне", product.brand)
        assertEquals("https://go-upc.s3.amazonaws.com/images/1.jpeg", product.imageUrl)
        assertTrue(product.needsReview)
    }

    /** The key travels in a header: a URL ends up in logs along the way. */
    @Test
    fun `the key is sent as a bearer token, never in the address`() = runTest {
        answer(404, """{"code":"4607096006433","error":"No product information was found"}""")

        provider.lookupByBarcode("4607096006433")

        val request = requests.single()
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertEquals("https://go-upc.com/api/v1/code/4607096006433", request.url.toString())
    }

    @Test
    fun `without a key nothing is sent`() = runTest {
        coEvery { keyStore.key() } returns null

        assertEquals(CatalogResult.NotFound, provider.lookupByBarcode("4607096006433"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an unknown code is not found`() = runTest {
        answer(404, """{"code":"4607065580018","error":"No product information was found"}""")

        assertEquals(CatalogResult.NotFound, provider.lookupByBarcode("4607065580018"))
        coVerify { keyStore.setProblem(null) }
    }

    /**
     * A refused key must not read as «could not reach the catalog»: retrying would not help.
     * The scan moves on to manual entry, and settings say what is wrong.
     */
    @Test
    fun `a refused key reads as not found and is reported to settings`() = runTest {
        answer(401, """{"message":"Access to the API requires an API key."}""")

        assertEquals(CatalogResult.NotFound, provider.lookupByBarcode("4607096006433"))
        coVerify { keyStore.setProblem(CatalogKeyProblem.REJECTED) }
    }

    @Test
    fun `a used-up month reads as not found and is reported to settings`() = runTest {
        answer(429)

        assertEquals(CatalogResult.NotFound, provider.lookupByBarcode("4607096006433"))
        coVerify { keyStore.setProblem(CatalogKeyProblem.QUOTA) }
    }

    /** A guessed digit means the answer is for some other product. */
    @Test
    fun `an inferred answer is not trusted`() = runTest {
        answer(200, """{"product":{"name":"Something else"},"inferred":true}""")

        assertEquals(CatalogResult.NotFound, provider.lookupByBarcode("4607096006433"))
    }

    @Test
    fun `a server failure is an error, not a miss`() = runTest {
        answer(503)

        assertTrue(provider.lookupByBarcode("4607096006433") is CatalogResult.Error)
    }

    @Test
    fun `a garbled answer is an error`() = runTest {
        answer(200, "<html>maintenance</html>")

        assertTrue(provider.lookupByBarcode("4607096006433") is CatalogResult.Error)
    }

    /** Not a barcode at all: not worth a request of the month's 150. */
    @Test
    fun `something that is not a barcode is never sent`() = runTest {
        assertEquals(CatalogResult.NotFound, provider.lookupByBarcode("01046"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a key that gets an answer works, found or not`() = runTest {
        answer(404)
        assertEquals(KeyCheckResult.WORKS, provider.checkKey())

        answer(200, """{"product":{"name":"Queso"},"inferred":false}""")
        assertEquals(KeyCheckResult.WORKS, provider.checkKey())
    }

    @Test
    fun `a key check tells refused from used up`() = runTest {
        answer(401)
        assertEquals(KeyCheckResult.REJECTED, provider.checkKey())

        answer(429)
        assertEquals(KeyCheckResult.QUOTA_USED, provider.checkKey())
    }

    @Test
    fun `there is nothing to check without a key`() = runTest {
        coEvery { keyStore.key() } returns null

        assertEquals(KeyCheckResult.NOT_SET, provider.checkKey())
        assertTrue(requests.isEmpty())
    }

    /** Real names from the coverage check on Russian barcodes. */
    @Test
    fun `shop listing leftovers are trimmed from names`() {
        assertEquals(
            "Домик В Деревне Масло 72,5% 180Гр",
            GoUpcCatalogProvider.cleanName("Домик В Деревне Масло 72,5% 180Гр Купить В Алматы"),
        )
        assertEquals(
            "Лапша \"Доширак Сытный Обед\" Со Вк.курицы, 110г",
            GoUpcCatalogProvider.cleanName("Лапша &quot;Доширак Сытный Обед&quot;  Со Вк.курицы, 110г"),
        )
        // Nothing to trim: left exactly as it came.
        assertEquals("Makfa Flour 2kg", GoUpcCatalogProvider.cleanName("Makfa Flour 2kg"))
    }
}
