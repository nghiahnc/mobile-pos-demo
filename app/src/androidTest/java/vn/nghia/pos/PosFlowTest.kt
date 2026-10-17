package vn.nghia.pos

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.nghia.pos.domain.PaymentStatus
import vn.nghia.pos.ui.MainActivity
import java.io.File

@RunWith(AndroidJUnit4::class)
class PosFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as PosApplication
    private val dao get() = app.component.database().dao()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setup() {
        app.component.database().clearAllTables()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForView(R.id.products)
        waitFor { scenarioValue { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.products)?.adapter?.itemCount == 12 } }
    }
    @After fun teardown() { scenario.close() }

    @Test fun cardFailureRetryCashAndHistory() {
        capture("01-catalog")
        addCoffee(); addCoffee()
        waitFor { dao.readCart().singleOrNull()?.quantity == 2 }
        onView(withId(R.id.viewCart)).perform(click())
        waitForView(R.id.checkoutButton)
        onView(withId(R.id.totalAmount)).check(matches(withText("50.000 ₫")))
        capture("02-cart")
        onView(withId(R.id.checkoutButton)).perform(click())
        waitForView(R.id.payButton)
        onView(withId(R.id.cardMethod)).perform(click())
        onView(withId(R.id.scenarioDeclined)).perform(scrollTo(), click())
        onView(withId(R.id.payButton)).perform(click())
        waitFor { dao.observeReceipts().blockingFirst().size == 1 }
        waitForView(R.id.paymentError)
        onView(withId(R.id.paymentError)).perform(scrollTo()).check(matches(withText(containsString("bị từ chối"))))
        assertEquals(2, dao.readCart().single().quantity)
        capture("03-card-declined")
        onView(withId(R.id.scenarioApproved)).perform(scrollTo(), click())
        capture("04-card-checkout")
        // Two immediate taps must still produce just one additional successful receipt.
        onView(withId(R.id.payButton)).perform(click(), click())
        waitForView(R.id.statusTitle)
        onView(withId(R.id.statusTitle)).check(matches(withText(R.string.success)))
        waitFor { dao.observeReceipts().blockingFirst().size == 2 }
        assertEquals(1, dao.observeReceipts().blockingFirst().count { it.status == PaymentStatus.SUCCESS.name })
        assertTrue(dao.readCart().isEmpty())
        capture("05-receipt-card")
        onView(withId(R.id.newOrder)).perform(scrollTo(), click())
        waitForView(R.id.products)
        addCoffee()
        waitFor { dao.readCart().size == 1 }
        onView(withId(R.id.viewCart)).perform(click())
        waitForView(R.id.checkoutButton)
        onView(withId(R.id.checkoutButton)).perform(click())
        waitForView(R.id.payButton)
        onView(withId(R.id.cashMethod)).perform(click())
        onView(withId(R.id.cashInput)).perform(scrollTo(), replaceText("24000"))
        closeSoftKeyboard()
        onView(withId(R.id.payButton)).perform(click())
        waitForView(R.id.paymentError)
        assertEquals(2, dao.observeReceipts().blockingFirst().size)
        onView(withId(R.id.cashInput)).perform(scrollTo(), replaceText("100000"))
        closeSoftKeyboard()
        onView(withId(R.id.changeAmount)).perform(scrollTo()).check(matches(withText("75.000 ₫")))
        capture("06-cash-checkout")
        onView(withId(R.id.payButton)).perform(click())
        waitForView(R.id.statusTitle)
        onView(withId(R.id.paymentDetails)).perform(scrollTo()).check(matches(withText(containsString("Tiền thừa: 75.000 ₫"))))
        capture("07-receipt-cash")
        onView(withId(R.id.viewHistory)).perform(scrollTo(), click())
        waitForView(R.id.revenue)
        onView(withId(R.id.revenue)).check(matches(withText("75.000 ₫")))
        onView(withId(R.id.successCount)).check(matches(withText("2 giao dịch thành công / 3 lần thử")))
        capture("08-history")
        scenario.recreate()
        waitForView(R.id.revenue)
        onView(withId(R.id.revenue)).check(matches(withText("75.000 ₫")))
    }

    @Test fun searchAndCartSurviveRecreationAndRotation() {
        onView(withId(R.id.searchInput)).perform(replaceText("ca phe den"))
        closeSoftKeyboard()
        waitFor { scenarioValue { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.products)?.adapter?.itemCount == 1 } }
        addCoffee()
        waitFor { dao.readCart().singleOrNull()?.quantity == 1 }
        scenario.recreate()
        waitForView(R.id.searchInput)
        onView(withId(R.id.searchInput)).check(matches(withText("ca phe den")))
        assertEquals(1, dao.readCart().single().quantity)
        onView(withId(R.id.viewCart)).perform(click())
        waitForView(R.id.checkoutButton)
        onView(withId(R.id.checkoutButton)).perform(click())
        waitForView(R.id.payButton)
        onView(withId(R.id.cardMethod)).perform(click())
        onView(withId(R.id.scenarioConnection)).perform(scrollTo(), click())
        scenario.recreate()
        waitForView(R.id.cardMethod)
        onView(withId(R.id.cardMethod)).check(matches(isChecked()))
        onView(withId(R.id.scenarioConnection)).check(matches(isChecked()))
        var beforeRotation: MainActivity? = null
        scenario.onActivity { beforeRotation = it; it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        waitFor { scenarioValue {
            it !== beforeRotation && it.hasWindowFocus() && it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        } }
        waitForView(R.id.payButton)
        onView(withId(R.id.cardMethod)).check(matches(isChecked()))
        onView(withId(R.id.scenarioConnection)).check(matches(isChecked()))
        scenario.onActivity {
            val model = androidx.lifecycle.ViewModelProvider(it)[vn.nghia.pos.ui.PosViewModel::class.java]
            assertEquals(vn.nghia.pos.domain.PaymentMethod.CARD, model.method)
            assertEquals(vn.nghia.pos.domain.DemoScenario.CONNECTION_ERROR, model.scenario)
        }
        onView(withId(R.id.payButton)).perform(click())
        waitFor { dao.observeReceipts().blockingFirst().size == 1 }
        assertEquals(PaymentStatus.CONNECTION_ERROR.name, dao.observeReceipts().blockingFirst().single().status)
        assertEquals(1, dao.readCart().single().quantity)
        capture("09-landscape-error")
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    private fun addCoffee() { onView(withContentDescription("Thêm Cà phê đen vào giỏ")).perform(click()) }
    private fun waitForView(id: Int) = waitFor { scenarioValue { activity -> activity.findViewById<View>(id)?.isShown == true } }
    private fun scenarioValue(predicate: (MainActivity) -> Boolean): Boolean {
        var result = false
        scenario.onActivity { result = predicate(it) }
        return result
    }
    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        }
        capture("test-failure")
        fail("UI/database condition timed out after 15 seconds")
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(150)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
