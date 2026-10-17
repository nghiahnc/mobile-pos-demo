package vn.nghia.pos.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import vn.nghia.pos.R
import vn.nghia.pos.databinding.ItemCartBinding
import vn.nghia.pos.databinding.ItemProductBinding
import vn.nghia.pos.databinding.ItemTransactionBinding
import vn.nghia.pos.domain.*

class ProductAdapter(private val add: (Product) -> Unit) : ListAdapter<Product, ProductAdapter.Holder>(object : DiffUtil.ItemCallback<Product>() {
    override fun areItemsTheSame(oldItem: Product, newItem: Product) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Product, newItem: Product) = oldItem == newItem
}) {
    var controlsEnabled = true
        set(value) { if (field != value) { field = value; notifyItemRangeChanged(0, itemCount, "enabled") } }
    class Holder(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val product = getItem(position)
        with(holder.binding) {
            val compact = root.resources.compactUi()
            artBackground.isVisible = !compact
            category.isVisible = !compact
            note.isVisible = !compact
            productName.text = product.name
            category.text = product.category.uppercase(java.util.Locale.forLanguageTag("vi-VN"))
            note.text = product.note
            price.text = money(product.price)
            productArt.setImageResource(artFor(product.category))
            artBackground.backgroundTintList = ColorStateList.valueOf(root.context.getColor(artColor(product.category)))
            addButton.contentDescription = root.context.getString(R.string.add_product, product.name)
            addButton.isEnabled = controlsEnabled
            addButton.setOnClickListener { add(product) }
        }
    }
}

class CartAdapter(private val change: (Product, Int) -> Unit, private val remove: (String) -> Unit) :
    ListAdapter<CartLine, CartAdapter.Holder>(object : DiffUtil.ItemCallback<CartLine>() {
        override fun areItemsTheSame(oldItem: CartLine, newItem: CartLine) = oldItem.product.id == newItem.product.id
        override fun areContentsTheSame(oldItem: CartLine, newItem: CartLine) = oldItem == newItem
    }) {
    var controlsEnabled = true
        set(value) { if (field != value) { field = value; notifyItemRangeChanged(0, itemCount, "enabled") } }
    class Holder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val line = getItem(position)
        with(holder.binding) {
            name.text = line.product.name
            unitPrice.text = money(line.product.price)
            art.setImageResource(artFor(line.product.category))
            quantity.text = line.quantity.toString()
            quantity.contentDescription = root.context.getString(R.string.quantity_label, line.quantity)
            subtotal.text = money(line.subtotal)
            val largeText = root.resources.configuration.fontScale >= 1.6f
            subtotal.isVisible = !largeText
            subtotalStacked.isVisible = largeText
            subtotalStacked.text = money(line.subtotal)
            decrease.contentDescription = root.context.getString(R.string.decrease_product, line.product.name)
            increase.contentDescription = root.context.getString(R.string.increase_product, line.product.name)
            this.remove.contentDescription = root.context.getString(R.string.remove_product, line.product.name)
            decrease.isEnabled = controlsEnabled
            increase.isEnabled = controlsEnabled && line.quantity < 99
            this.remove.isEnabled = controlsEnabled
            decrease.setOnClickListener { change(line.product, -1) }
            increase.setOnClickListener { change(line.product, 1) }
            this.remove.setOnClickListener { remove(line.product.id) }
        }
    }
}

class ReceiptAdapter(private val open: (Receipt) -> Unit) : ListAdapter<Receipt, ReceiptAdapter.Holder>(object : DiffUtil.ItemCallback<Receipt>() {
    override fun areItemsTheSame(oldItem: Receipt, newItem: Receipt) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Receipt, newItem: Receipt) = oldItem == newItem
}) {
    class Holder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val receipt = getItem(position)
        with(holder.binding) {
            code.text = shortCode(receipt.id)
            amount.text = money(receipt.total)
            details.text = "${timestamp(receipt.createdAt)} · ${receipt.method.label()}"
            status.text = receipt.status.label()
            val success = receipt.status == PaymentStatus.SUCCESS
            status.setTextColor(root.context.getColor(if (success) R.color.primary else R.color.error))
            status.backgroundTintList = ColorStateList.valueOf(root.context.getColor(if (success) R.color.soft_primary else R.color.soft_error))
            root.contentDescription = "${code.text}, ${amount.text}, ${status.text}, ${details.text}. Xem biên lai."
            root.setOnClickListener { open(receipt) }
        }
    }
}
