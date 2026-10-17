package vn.nghia.pos.domain

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.atomic.AtomicBoolean

interface PosRepository {
    fun products(): Single<List<Product>>
    fun cart(): Flowable<List<CartLine>>
    fun receipts(): Flowable<List<Receipt>>
    fun changeQuantity(product: Product, delta: Int): Completable
    fun remove(productId: String): Completable
    fun checkout(request: PaymentRequest): Single<Receipt>
}

/** A second subscriber cannot start another payment while one is in progress. */
class CheckoutUseCase(private val repository: PosRepository) {
    private val inFlight = AtomicBoolean(false)
    fun execute(request: PaymentRequest): Single<Receipt> = Single.defer {
        if (!inFlight.compareAndSet(false, true)) {
            Single.error(IllegalStateException("Đang xử lý thanh toán. Vui lòng chờ."))
        } else {
            Single.defer { repository.checkout(request) }.doFinally { inFlight.set(false) }
        }
    }
}
