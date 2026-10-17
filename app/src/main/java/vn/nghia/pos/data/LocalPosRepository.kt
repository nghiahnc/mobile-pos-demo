package vn.nghia.pos.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import vn.nghia.pos.domain.*
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPosRepository @Inject constructor(
    private val api: CatalogApi,
    private val database: PosDatabase,
    private val gson: Gson,
    private val terminal: PaymentTerminal
) : PosRepository {
    private val dao get() = database.dao()
    private val lineType = object : TypeToken<List<CartLine>>() {}.type
    override fun products(): Single<List<Product>> = api.products()
    override fun cart(): Flowable<List<CartLine>> = dao.observeCart().map { rows -> rows.map { it.toLine() } }
    override fun receipts(): Flowable<List<Receipt>> = dao.observeReceipts().map { rows -> rows.map { it.toReceipt() } }

    override fun changeQuantity(product: Product, delta: Int): Completable = Completable.fromAction {
        require(delta == -1 || delta == 1) { "Chỉ tăng hoặc giảm từng sản phẩm." }
        database.runInTransaction {
            val next = (dao.cartLine(product.id)?.quantity ?: 0) + delta
            require(next in 0..99) { "Mỗi sản phẩm tối đa 99 phần." }
            if (next == 0) dao.removeCart(product.id)
            else dao.putCart(CartEntity(product.id, product.name, product.category, product.price, product.note, next))
        }
    }

    override fun remove(productId: String): Completable = Completable.fromAction { dao.removeCart(productId) }

    override fun checkout(request: PaymentRequest): Single<Receipt> = Single.fromCallable {
        database.runInTransaction(Callable {
            // Returning a persisted outcome makes the same attempt id safe to replay.
            dao.receipt(request.attemptId)?.let { return@Callable it.toReceipt() }
            val lines = dao.readCart().map { it.toLine() }
            val total = MoneyRules.validate(lines, request)
            val status = terminal.authorize(request)
            val receipt = Receipt(
                request.attemptId, System.currentTimeMillis(), lines, total, request.method, status,
                request.tendered.takeIf { request.method == PaymentMethod.CASH },
                if (request.method == PaymentMethod.CASH && status == PaymentStatus.SUCCESS) request.tendered!! - total else 0
            )
            dao.insertReceipt(ReceiptEntity(
                receipt.id, receipt.createdAt, gson.toJson(lines), total,
                receipt.method.name, status.name, receipt.tendered, receipt.change
            ))
            // Both writes commit or both roll back. Failed attempts never consume the cart.
            if (status == PaymentStatus.SUCCESS) dao.clearCart()
            receipt
        })
    }.delaySubscription(900, TimeUnit.MILLISECONDS, Schedulers.io())

    private fun CartEntity.toLine() = CartLine(Product(productId, name, category, price, note), quantity)
    private fun ReceiptEntity.toReceipt() = Receipt(id, createdAt, gson.fromJson(linesJson, lineType), total,
        PaymentMethod.valueOf(method), PaymentStatus.valueOf(status), tendered, change)
}
