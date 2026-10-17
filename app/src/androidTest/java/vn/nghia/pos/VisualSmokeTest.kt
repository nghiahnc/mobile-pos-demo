package vn.nghia.pos

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import vn.nghia.pos.domain.*
import vn.nghia.pos.ui.MainActivity
import java.io.File

/** Run again with emulator display/font/theme overrides to validate the actual layouts. */
@RunWith(AndroidJUnit4::class)
class VisualSmokeTest {
    @Test fun keyActionsRemainVisibleAndUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PosApplication
        val db = app.component.database()
        db.clearAllTables()
        val repository = app.component.repository()
        val products = repository.products().blockingGet()
        val coffee = products.first { it.id == "cf01" }
        val latte = products.first { it.id == "cf04" }
        repository.changeQuantity(coffee, 1).blockingAwait()
        repository.changeQuantity(coffee, 1).blockingAwait()
        repository.changeQuantity(products.first { it.id == "te01" }, 1).blockingAwait()
        val receipt = repository.checkout(PaymentRequest("8fd7b032-248f-4631-b1cf-e319d3d528ca", PaymentMethod.CARD, null)).blockingGet()
        assertEquals(89000L, receipt.total)
        repository.changeQuantity(coffee, 1).blockingAwait()
        repository.changeQuantity(latte, 1).blockingAwait()
        val profile = InstrumentationRegistry.getArguments().getString("profile") ?: "default"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun waitFor(predicate: (MainActivity) -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 15000
                while (SystemClock.elapsedRealtime() < deadline) {
                    var ready = false
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { ready = predicate(it) }
                    if (ready) return
                    SystemClock.sleep(50)
                }
                fail("Layout not ready: $profile")
            }
            fun capture(page: String) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(150)
                val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                File(dir, "$profile-$page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            fun assertTarget(id: Int) {
                scenario.onActivity { activity ->
                    val target = requireNotNull(activity.findViewById<View>(id))
                    val rect = Rect()
                    assertTrue("$profile target $id visible", target.getGlobalVisibleRect(rect))
                    val minPx = (48 * activity.resources.displayMetrics.density).toInt()
                    assertTrue("$profile target width ${rect.width()} >= $minPx", rect.width() >= minPx)
                    assertTrue("$profile target height ${rect.height()} >= $minPx", rect.height() >= minPx)
                }
            }
            waitFor { it.findViewById<RecyclerView>(R.id.products)?.adapter?.itemCount == 12 }
            capture("catalog")
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.products)
                val holder = requireNotNull(list.findViewHolderForAdapterPosition(0))
                val button = holder.itemView.findViewById<View>(R.id.addButton)
                val viewport = Rect()
                list.getGlobalVisibleRect(viewport)
                val xy = IntArray(2)
                button.getLocationOnScreen(xy)
                list.scrollBy(0, maxOf(0, xy[1] + button.height - viewport.bottom + 8))
            }
            onView(withContentDescription("Thêm Cà phê đen vào giỏ")).perform(click())
            onView(withId(R.id.nav_cart)).perform(click())
            waitFor { it.findViewById<View>(R.id.checkoutButton)?.isShown == true }
            assertTarget(R.id.checkoutButton)
            capture("cart")
            onView(withId(R.id.checkoutButton)).perform(click())
            waitFor { it.findViewById<View>(R.id.payButton)?.isShown == true }
            assertTarget(R.id.payButton)
            capture("checkout")
            onView(withId(R.id.nav_history)).perform(click())
            waitFor { it.findViewById<RecyclerView>(R.id.transactions)?.adapter?.itemCount == 1 }
            onView(withId(R.id.revenue)).check(matches(withText("89.000 ₫")))
            scenario.onActivity {
                val list = it.findViewById<RecyclerView>(R.id.transactions)
                assertTrue("$profile history has visible content", list.height >= (48 * it.resources.displayMetrics.density).toInt())
            }
            capture("history")
        }
    }
}
