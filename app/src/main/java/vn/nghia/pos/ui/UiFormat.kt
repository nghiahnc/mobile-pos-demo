package vn.nghia.pos.ui

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import vn.nghia.pos.R
import vn.nghia.pos.domain.PaymentMethod
import vn.nghia.pos.domain.PaymentStatus
import java.text.Normalizer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun money(value: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " ₫"
fun timestamp(value: Long): String = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.forLanguageTag("vi-VN")).format(Date(value))
fun shortCode(id: String) = "#" + id.take(8).uppercase(Locale.ROOT)
fun PaymentMethod.label(): String = if (this == PaymentMethod.CASH) "Tiền mặt" else "Thẻ mô phỏng"
fun PaymentStatus.label(): String = when (this) {
    PaymentStatus.SUCCESS -> "Thành công"
    PaymentStatus.DECLINED -> "Bị từ chối"
    PaymentStatus.CONNECTION_ERROR -> "Lỗi kết nối"
}
fun searchKey(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").replace('đ', 'd')
fun artFor(category: String) = when (category) {
    "Bánh ngọt" -> R.drawable.ic_cake
    "Trà trái cây", "Đồ uống khác" -> R.drawable.ic_tea
    else -> R.drawable.ic_cup
}
fun artColor(category: String) = when (category) {
    "Bánh ngọt" -> R.color.soft_cake
    "Trà trái cây" -> R.color.soft_tea
    "Đồ uống khác" -> R.color.soft_primary
    else -> R.color.soft_coffee
}
fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
fun Resources.compactUi() = getBoolean(R.bool.compact_height) || configuration.screenHeightDp < 700 || configuration.fontScale >= 1.6f
fun View.enableTree(enabled: Boolean) {
    isEnabled = enabled
    if (this is ViewGroup) children.forEach { it.enableTree(enabled) }
}
