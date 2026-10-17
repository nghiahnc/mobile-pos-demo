package vn.nghia.pos.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import vn.nghia.pos.PosApplication
import vn.nghia.pos.R
import vn.nghia.pos.databinding.*
import vn.nghia.pos.domain.*

abstract class PosFragment<B : ViewBinding> : Fragment() {
    private var _binding: B? = null
    protected val binding get() = requireNotNull(_binding)
    protected val vm: PosViewModel by activityViewModels { (requireActivity().application as PosApplication).component.viewModelFactory() }
    protected val host get() = requireActivity() as MainActivity
    abstract fun inflate(inflater: LayoutInflater, parent: ViewGroup?): B
    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = inflate(inflater, container)
        return binding.root
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}

class CatalogFragment : PosFragment<FragmentCatalogBinding>() {
    private val adapter = ProductAdapter { vm.change(it, 1) }
    override fun inflate(inflater: LayoutInflater, parent: ViewGroup?) = FragmentCatalogBinding.inflate(inflater, parent, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val twoPane = resources.getBoolean(R.bool.two_pane)
        if (resources.compactUi()) {
            binding.menuHeading.isVisible = false
            binding.menuCaption.isVisible = false
            binding.menuEyebrow.isVisible = false
            (binding.menuEyebrow.parent as View).setPadding(requireContext().dp(20), 0, requireContext().dp(20), 0)
        }
        binding.inlineCart.root.isVisible = twoPane
        binding.orderSummary.isVisible = !twoPane && resources.configuration.screenHeightDp >= 480
        val catalogWidth = resources.configuration.screenWidthDp - if (twoPane) 340 else 0
        val columns = if (resources.configuration.fontScale >= 1.6f) (catalogWidth / 360).coerceIn(1, 3)
            else (catalogWidth / 180).coerceIn(2, 4)
        binding.products.layoutManager = GridLayoutManager(requireContext(), columns)
        binding.products.adapter = adapter
        binding.products.itemAnimator = null
        binding.searchInput.setText(vm.query)
        binding.searchInput.doAfterTextChanged { vm.query = it.toString(); filterProducts() }
        binding.viewCart.setOnClickListener { host.navigate("cart") }
        binding.retryButton.setOnClickListener { vm.loadProducts() }
        vm.products.observe(viewLifecycleOwner) { products ->
            binding.categories.removeAllViews()
            (listOf(getString(R.string.all)) + products.map { it.category }.distinct()).forEach { name ->
                val chip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = name
                    isCheckable = true
                    isChecked = name == vm.category
                    setEnsureMinTouchTargetSize(true)
                    setOnClickListener { vm.category = name; filterProducts() }
                }
                binding.categories.addView(chip)
            }
            filterProducts()
        }
        vm.catalogLoading.observe(viewLifecycleOwner) { filterProducts() }
        vm.catalogError.observe(viewLifecycleOwner) { filterProducts() }
        vm.cart.observe(viewLifecycleOwner) { lines ->
            binding.cartTotal.text = money(MoneyRules.total(lines))
            binding.cartCount.text = "ĐƠN HIỆN TẠI · ${getString(R.string.item_count, lines.sumOf { it.quantity })}"
        }
        vm.busy.observe(viewLifecycleOwner) { adapter.controlsEnabled = !it; binding.viewCart.isEnabled = !it }
        if (twoPane) bindCartPanel(binding.inlineCart)
    }
    private fun filterProducts() {
        val query = searchKey(vm.query.trim())
        val filtered = vm.products.value.orEmpty().filter {
            (vm.category == getString(R.string.all) || it.category == vm.category) && searchKey(it.name).contains(query)
        }
        adapter.submitList(filtered)
        val error = vm.catalogError.value
        val loading = vm.catalogLoading.value == true
        binding.catalogMessage.isVisible = loading || error != null || filtered.isEmpty()
        binding.catalogMessage.text = when {
            loading -> getString(R.string.loading)
            error != null -> error
            else -> getString(R.string.no_results)
        }
        binding.retryButton.isVisible = error != null
    }
    override fun onDestroyView() { binding.products.adapter = null; super.onDestroyView() }
}

class CartFragment : PosFragment<PanelCartBinding>() {
    override fun inflate(inflater: LayoutInflater, parent: ViewGroup?) = PanelCartBinding.inflate(inflater, parent, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { bindCartPanel(binding) }
    override fun onDestroyView() { binding.cartItems.adapter = null; super.onDestroyView() }
}

private fun PosFragment<*>.bindCartPanel(panel: PanelCartBinding) {
    // Accessors are obtained from the same Activity store; a panel has no independent cart state.
    val activity = requireActivity() as MainActivity
    val model = androidx.lifecycle.ViewModelProvider(activity)[PosViewModel::class.java]
    val adapter = CartAdapter({ product, delta -> model.change(product, delta) }, { model.remove(it) })
    if (resources.compactUi()) {
        panel.cartEyebrow.isVisible = false
        panel.cartCaption.isVisible = false
        panel.addMore.isVisible = false
        panel.totalLabel.isVisible = false
        panel.subtotalNote.isVisible = false
        panel.totalAmount.textSize = 24f
        panel.root.setPadding(requireContext().dp(16), requireContext().dp(4), requireContext().dp(16), 0)
    }
    panel.cartItems.layoutManager = LinearLayoutManager(requireContext())
    panel.cartItems.adapter = adapter
    panel.cartItems.itemAnimator = null
    panel.addMore.setOnClickListener { activity.navigate("catalog") }
    panel.checkoutButton.setOnClickListener { activity.navigate("checkout") }
    model.cart.observe(viewLifecycleOwner) { lines ->
        adapter.submitList(lines)
        panel.emptyState.isVisible = lines.isEmpty()
        panel.cartItems.isVisible = lines.isNotEmpty()
        panel.totalsPanel.isVisible = lines.isNotEmpty()
        panel.totalAmount.text = money(MoneyRules.total(lines))
        panel.checkoutButton.isEnabled = lines.isNotEmpty() && model.busy.value != true
    }
    model.busy.observe(viewLifecycleOwner) {
        adapter.controlsEnabled = !it
        panel.addMore.isEnabled = !it
        panel.checkoutButton.isEnabled = !it && model.cart.value.orEmpty().isNotEmpty()
    }
}

class CheckoutFragment : PosFragment<FragmentCheckoutBinding>() {
    override fun inflate(inflater: LayoutInflater, parent: ViewGroup?) = FragmentCheckoutBinding.inflate(inflater, parent, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (resources.configuration.fontScale >= 1.6f) {
            binding.methodGroup.orientation = LinearLayout.VERTICAL
            listOf(binding.cashMethod, binding.cardMethod).forEach {
                it.layoutParams = android.widget.RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        binding.methodGroup.check(if (vm.method == PaymentMethod.CASH) R.id.cashMethod else R.id.cardMethod)
        binding.scenarioGroup.check(when (vm.scenario) {
            DemoScenario.APPROVED -> R.id.scenarioApproved
            DemoScenario.DECLINED -> R.id.scenarioDeclined
            DemoScenario.CONNECTION_ERROR -> R.id.scenarioConnection
        })
        binding.cashInput.setText(vm.cashText)
        binding.cashInput.doAfterTextChanged {
            vm.cashText = it.toString()
            vm.clearPaymentError()
            updateChange()
        }
        binding.methodGroup.setOnCheckedChangeListener { _, checked ->
            vm.method = if (checked == R.id.cashMethod) PaymentMethod.CASH else PaymentMethod.CARD
            vm.clearPaymentError()
            updateMethod()
        }
        binding.scenarioGroup.setOnCheckedChangeListener { _, checked ->
            vm.scenario = when (checked) {
                R.id.scenarioDeclined -> DemoScenario.DECLINED
                R.id.scenarioConnection -> DemoScenario.CONNECTION_ERROR
                else -> DemoScenario.APPROVED
            }
            vm.clearPaymentError()
        }
        binding.payButton.setOnClickListener {
            (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(binding.root.windowToken, 0)
            vm.pay()
        }
        binding.backButton.setOnClickListener { host.navigate("cart") }
        vm.cart.observe(viewLifecycleOwner) { lines ->
            binding.amount.text = money(MoneyRules.total(lines))
            binding.itemCount.text = getString(R.string.item_count, lines.sumOf { it.quantity })
            binding.payButton.isEnabled = lines.isNotEmpty() && vm.busy.value != true
            buildSuggestions()
            updateChange()
        }
        vm.paymentError.observe(viewLifecycleOwner) { error ->
            binding.paymentError.isVisible = error != null
            binding.paymentError.text = error
            binding.cashInputLayout.error = error?.takeIf { vm.method == PaymentMethod.CASH }
        }
        vm.busy.observe(viewLifecycleOwner) { busy ->
            binding.methodGroup.enableTree(!busy)
            binding.scenarioGroup.enableTree(!busy)
            binding.cashPanel.enableTree(!busy)
            binding.backButton.isEnabled = !busy
            binding.payButton.isEnabled = !busy && vm.cart.value.orEmpty().isNotEmpty()
            binding.payButton.setText(if (busy) R.string.processing else R.string.pay)
            binding.processingLabel.isVisible = busy
        }
        updateMethod()
    }
    private fun updateMethod() {
        binding.cashPanel.isVisible = vm.method == PaymentMethod.CASH
        binding.cardPanel.isVisible = vm.method == PaymentMethod.CARD
        updateChange()
    }
    private fun updateChange() {
        val total = MoneyRules.total(vm.cart.value.orEmpty())
        val tendered = vm.cashText.toLongOrNull() ?: 0L
        binding.changeAmount.text = money(maxOf(0L, tendered - total))
    }
    private fun buildSuggestions() {
        val total = MoneyRules.total(vm.cart.value.orEmpty())
        binding.cashSuggestions.removeAllViews()
        (listOf(total) + listOf(100000L, 200000L, 500000L).filter { it > total }).distinct().forEach { value ->
            val button = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = if (value == total) getString(R.string.exact) else money(value)
                minHeight = requireContext().dp(48)
                isEnabled = vm.busy.value != true
                setOnClickListener { binding.cashInput.setText(value.toString()) }
            }
            binding.cashSuggestions.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = requireContext().dp(8) })
        }
    }
}

class HistoryFragment : PosFragment<FragmentHistoryBinding>() {
    private val adapter = ReceiptAdapter { host.navigate("receipt", it.id) }
    override fun inflate(inflater: LayoutInflater, parent: ViewGroup?) = FragmentHistoryBinding.inflate(inflater, parent, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.transactions.layoutManager = LinearLayoutManager(requireContext())
        if (resources.compactUi()) {
            binding.historyEyebrow.isVisible = false
            binding.revenueNote.isVisible = false
            binding.revenue.textSize = 24f
            binding.historyHeading.textSize = 20f
            binding.revenueContent.setPadding(requireContext().dp(12), requireContext().dp(8), requireContext().dp(12), requireContext().dp(8))
            (binding.revenueCard.layoutParams as ViewGroup.MarginLayoutParams).topMargin = requireContext().dp(8)
            binding.recentLabel.textSize = 14f
            (binding.recentLabel.layoutParams as ViewGroup.MarginLayoutParams).apply { topMargin = requireContext().dp(8); bottomMargin = requireContext().dp(4) }
            binding.root.setPadding(requireContext().dp(20), 0, requireContext().dp(20), 0)
        }
        binding.transactions.adapter = adapter
        vm.receipts.observe(viewLifecycleOwner) { receipts ->
            adapter.submitList(receipts)
            binding.emptyHistory.isVisible = receipts.isEmpty()
            binding.transactions.isVisible = receipts.isNotEmpty()
            val successful = receipts.filter { it.status == PaymentStatus.SUCCESS }
            binding.revenue.text = money(successful.sumOf { it.total })
            binding.successCount.text = "${successful.size} giao dịch thành công / ${receipts.size} lần thử"
        }
    }
    override fun onDestroyView() { binding.transactions.adapter = null; super.onDestroyView() }
}

class ReceiptFragment : PosFragment<FragmentReceiptBinding>() {
    override fun inflate(inflater: LayoutInflater, parent: ViewGroup?) = FragmentReceiptBinding.inflate(inflater, parent, false)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val receiptId = requireArguments().getString("receiptId")
        vm.receipts.observe(viewLifecycleOwner) { receipts ->
            receipts.firstOrNull { it.id == receiptId }?.let { showReceipt(it) }
        }
        binding.newOrder.setOnClickListener { host.navigate("catalog") }
        binding.viewHistory.setOnClickListener { host.navigate("history") }
    }
    private fun showReceipt(receipt: Receipt) {
        val success = receipt.status == PaymentStatus.SUCCESS
        binding.statusTitle.text = if (success) getString(R.string.success) else "Thanh toán ${receipt.status.label().lowercase()}"
        binding.statusCaption.text = if (success) getString(R.string.receipt_caption) else "Giao dịch chưa được ghi nhận thành công."
        binding.statusIcon.setImageResource(if (success) R.drawable.ic_check else R.drawable.ic_receipt)
        binding.statusIcon.imageTintList = ColorStateList.valueOf(requireContext().getColor(if (success) R.color.primary else R.color.error))
        binding.statusTitle.setTextColor(requireContext().getColor(if (success) R.color.primary else R.color.error))
        binding.receiptMeta.text = "${shortCode(receipt.id)}\n${timestamp(receipt.createdAt)}\nMã đầy đủ: ${receipt.id}"
        binding.receiptLines.removeAllViews()
        receipt.lines.forEach { line ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, requireContext().dp(8), 0, requireContext().dp(8))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val name = TextView(requireContext()).apply {
                text = "${line.product.name}\n${line.quantity} × ${money(line.product.price)}"
                setTextColor(requireContext().getColor(R.color.on_surface))
                textSize = 14f
                setLineSpacing(requireContext().dp(4).toFloat(), 1f)
            }
            val amount = TextView(requireContext()).apply {
                text = money(line.subtotal)
                setTextColor(requireContext().getColor(R.color.on_surface))
                textSize = 15f
                setPadding(requireContext().dp(12), 0, 0, 0)
            }
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(amount)
            binding.receiptLines.addView(row)
        }
        binding.receiptTotal.text = money(receipt.total)
        binding.paymentDetails.text = buildString {
            append("Phương thức: ${receipt.method.label()}\nTrạng thái: ${receipt.status.label()}")
            if (receipt.method == PaymentMethod.CASH) append("\nKhách đưa: ${money(receipt.tendered ?: 0)}\nTiền thừa: ${money(receipt.change)}")
        }
    }
}
