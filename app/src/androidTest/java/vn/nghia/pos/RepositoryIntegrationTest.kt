package vn.nghia.pos

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import vn.nghia.pos.data.*
import vn.nghia.pos.domain.*

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: PosDatabase
    private lateinit var repository: LocalPosRepository
    private lateinit var api: CatalogApi
    private val coffee = Product("coffee", "Cà phê đen", "Cà phê", 25000, "")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
        api = Retrofit.Builder().baseUrl("https://demo.nghia.invalid/api/")
            .client(OkHttpClient.Builder().addInterceptor(LocalCatalogInterceptor(context)).build())
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build().create(CatalogApi::class.java)
        repository = LocalPosRepository(api, db, Gson(), FakePaymentTerminal())
    }
    @After fun teardown() { db.close() }
    private fun add(quantity: Int = 1) { repeat(quantity) { repository.changeQuantity(coffee, 1).blockingAwait() } }
    private fun request(id: String = "attempt-1", scenario: DemoScenario = DemoScenario.APPROVED) =
        PaymentRequest(id, PaymentMethod.CARD, null, scenario)
    private fun cart() = repository.cart().blockingFirst()
    private fun receipts() = repository.receipts().blockingFirst()

    @Test fun retrofitReadsTwelveLocalProductsWithoutNetworkPermission() {
        val products = repository.products().blockingGet()
        assertEquals(12, products.size)
        assertEquals(12, products.map { it.id }.distinct().size)
        assertTrue(products.all { it.price > 0 })
    }
    @Test fun cashCommitsReceiptAndClearsCartWithCorrectChange() {
        add(2)
        val receipt = repository.checkout(PaymentRequest("cash", PaymentMethod.CASH, 100000)).blockingGet()
        assertEquals(50000L, receipt.total)
        assertEquals(50000L, receipt.change)
        assertEquals(PaymentStatus.SUCCESS, receipt.status)
        assertTrue(cart().isEmpty())
        assertEquals(receipt, receipts().single())
    }
    @Test fun declinedPaymentRetainsCartAndCanRetry() {
        add()
        assertEquals(PaymentStatus.DECLINED, repository.checkout(request(scenario = DemoScenario.DECLINED)).blockingGet().status)
        assertEquals(1, cart().single().quantity)
        repository.checkout(request("retry")).blockingGet()
        assertTrue(cart().isEmpty())
        assertEquals(2, receipts().size)
        assertEquals(25000L, receipts().filter { it.status == PaymentStatus.SUCCESS }.sumOf { it.total })
    }
    @Test fun connectionErrorRetainsCartAndRecordsFailure() {
        add()
        val outcome = repository.checkout(request(scenario = DemoScenario.CONNECTION_ERROR)).blockingGet()
        assertEquals(PaymentStatus.CONNECTION_ERROR, outcome.status)
        assertEquals(1, cart().size)
        assertEquals(outcome, receipts().single())
    }
    @Test fun insufficientCashDoesNotWriteReceiptOrClearCart() {
        add()
        repository.checkout(PaymentRequest("poor-cash", PaymentMethod.CASH, 24000)).test()
            .await().assertError(IllegalArgumentException::class.java)
        assertEquals(1, cart().size)
        assertTrue(receipts().isEmpty())
    }
    @Test fun replayOfSameAttemptCannotConsumeANewCart() {
        add()
        val original = repository.checkout(request()).blockingGet()
        add(2)
        assertEquals(original, repository.checkout(request()).blockingGet())
        assertEquals(1, receipts().size)
        assertEquals(2, cart().single().quantity)
    }
    @Test fun concurrentSameAttemptCommitsExactlyOnce() {
        add()
        val outcomes = Single.zip(
            repository.checkout(request()).subscribeOn(Schedulers.io()),
            repository.checkout(request()).subscribeOn(Schedulers.io())
        ) { a, b -> listOf(a, b) }.blockingGet()
        assertEquals(outcomes[0], outcomes[1])
        assertEquals(1, receipts().size)
        assertTrue(cart().isEmpty())
    }
    @Test fun terminalExceptionRollsBackAndRetainsCart() {
        add()
        val throwingTerminal = object : PaymentTerminal {
            override fun authorize(request: PaymentRequest): PaymentStatus = throw IllegalStateException("Device failure")
        }
        val broken = LocalPosRepository(api, db, Gson(), throwingTerminal)
        broken.checkout(request()).test().await().assertError(IllegalStateException::class.java)
        assertTrue(receipts().isEmpty())
        assertEquals(1, cart().single().quantity)
    }
    @Test fun quantityBoundaryAndRemovalAreEnforced() {
        add(99)
        repository.changeQuantity(coffee, 1).test().assertError(IllegalArgumentException::class.java)
        assertEquals(99, cart().single().quantity)
        repository.remove(coffee.id).blockingAwait()
        assertTrue(cart().isEmpty())
        repository.changeQuantity(coffee, -1).test().assertError(IllegalArgumentException::class.java)
    }
    @Test fun fileDatabaseSurvivesReopen() {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, PosDatabase::class.java, name).build()
        try {
            var repo = LocalPosRepository(api, disk, Gson(), FakePaymentTerminal())
            repo.changeQuantity(coffee, 1).blockingAwait()
            repo.checkout(request("persisted")).blockingGet()
            repo.changeQuantity(coffee, 1).blockingAwait()
            disk.close()
            disk = Room.databaseBuilder(context, PosDatabase::class.java, name).build()
            repo = LocalPosRepository(api, disk, Gson(), FakePaymentTerminal())
            assertEquals("persisted", repo.receipts().blockingFirst().single().id)
            assertEquals(1, repo.cart().blockingFirst().single().quantity)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
