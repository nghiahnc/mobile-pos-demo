package vn.nghia.pos.domain

data class Product(val id: String, val name: String, val category: String, val price: Long, val note: String)
data class CartLine(val product: Product, val quantity: Int) {
    val subtotal: Long get() = Math.multiplyExact(product.price, quantity.toLong())
}
enum class PaymentMethod { CASH, CARD }
enum class DemoScenario { APPROVED, DECLINED, CONNECTION_ERROR }
enum class PaymentStatus { SUCCESS, DECLINED, CONNECTION_ERROR }
data class PaymentRequest(
    val attemptId: String,
    val method: PaymentMethod,
    val tendered: Long?,
    val scenario: DemoScenario = DemoScenario.APPROVED
)
data class Receipt(
    val id: String,
    val createdAt: Long,
    val lines: List<CartLine>,
    val total: Long,
    val method: PaymentMethod,
    val status: PaymentStatus,
    val tendered: Long?,
    val change: Long
)

object MoneyRules {
    fun total(lines: List<CartLine>): Long = lines.fold(0L) { total, line ->
        require(line.quantity in 1..99) { "Số lượng phải từ 1 đến 99." }
        require(line.product.price > 0) { "Giá sản phẩm không hợp lệ." }
        Math.addExact(total, line.subtotal)
    }

    fun validate(lines: List<CartLine>, request: PaymentRequest): Long {
        require(request.attemptId.isNotBlank()) { "Thiếu mã lần thanh toán." }
        require(lines.isNotEmpty()) { "Giỏ hàng đang trống." }
        val total = total(lines)
        if (request.method == PaymentMethod.CASH) {
            require((request.tendered ?: -1) >= total) { "Tiền khách đưa chưa đủ thanh toán." }
        }
        return total
    }
}

interface PaymentTerminal {
    fun authorize(request: PaymentRequest): PaymentStatus
}

/** No card numbers or real payment credentials enter the demo. */
class FakePaymentTerminal : PaymentTerminal {
    override fun authorize(request: PaymentRequest): PaymentStatus =
        if (request.method == PaymentMethod.CASH) PaymentStatus.SUCCESS
        else when (request.scenario) {
            DemoScenario.APPROVED -> PaymentStatus.SUCCESS
            DemoScenario.DECLINED -> PaymentStatus.DECLINED
            DemoScenario.CONNECTION_ERROR -> PaymentStatus.CONNECTION_ERROR
        }
}
