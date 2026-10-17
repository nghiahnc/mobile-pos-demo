package vn.nghia.pos.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import vn.nghia.pos.PosApplication
import vn.nghia.pos.R
import vn.nghia.pos.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val vm: PosViewModel by viewModels { (application as PosApplication).component.viewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.brandHeader.isVisible = resources.configuration.screenHeightDp >= 480
        binding.headerDivider.isVisible = binding.brandHeader.isVisible
        binding.counterLabel.isVisible = resources.configuration.fontScale < 1.6f
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        binding.navigation.setOnItemSelectedListener {
            if (vm.busy.value == true) false
            else {
                when (it.itemId) {
                    R.id.nav_sale -> navigate("catalog")
                    R.id.nav_cart -> navigate("cart")
                    R.id.nav_history -> navigate("history")
                }
                true
            }
        }
        binding.aboutButton.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.about).setMessage(R.string.about_content)
                .setPositiveButton(R.string.close, null).show()
        }
        vm.cart.observe(this) { lines ->
            val count = lines.sumOf { it.quantity }
            binding.navigation.getOrCreateBadge(R.id.nav_cart).apply {
                isVisible = count > 0
                number = count
                backgroundColor = getColor(R.color.primary)
                badgeTextColor = getColor(R.color.on_primary)
            }
        }
        vm.message.observe(this) { text ->
            if (text != null) {
                Snackbar.make(binding.pageHost, text, Snackbar.LENGTH_LONG).setAnchorView(binding.navigation).show()
                vm.message.value = null
            }
        }
        vm.openReceipt.observe(this) { id ->
            if (id != null && !supportFragmentManager.isStateSaved) {
                navigate("receipt", id)
                vm.openReceipt.value = null
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vm.busy.value == true) return
                when (supportFragmentManager.findFragmentById(R.id.pageHost)) {
                    is CheckoutFragment -> navigate("cart")
                    is ReceiptFragment -> navigate("history")
                    is CartFragment, is HistoryFragment -> navigate("catalog")
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
                }
            }
        })
        if (savedInstanceState == null) navigate("catalog")
    }

    fun navigate(route: String, receiptId: String? = null) {
        if (supportFragmentManager.isStateSaved) return
        val tag = route + (receiptId ?: "")
        if (supportFragmentManager.findFragmentById(R.id.pageHost)?.tag == tag) return
        val fragment: Fragment = when (route) {
            "cart" -> CartFragment()
            "checkout" -> CheckoutFragment()
            "receipt" -> ReceiptFragment().apply { arguments = Bundle().apply { putString("receiptId", receiptId) } }
            "history" -> HistoryFragment()
            else -> CatalogFragment()
        }
        supportFragmentManager.commit { setReorderingAllowed(true); replace(R.id.pageHost, fragment, tag) }
        binding.navigation.menu.findItem(when (route) {
            "cart", "checkout" -> R.id.nav_cart
            "history", "receipt" -> R.id.nav_history
            else -> R.id.nav_sale
        }).isChecked = true
    }
}
