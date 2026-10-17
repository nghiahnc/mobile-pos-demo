package vn.nghia.pos.domain

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import org.junit.Test

class CheckoutUseCaseTest {
    private val pending = SingleSubject.create<Receipt>()
    private var calls = 0
    private val repository = object : PosRepository {
        override fun products() = Single.just(emptyList<Product>())
        override fun cart() = Flowable.just(emptyList<CartLine>())
        override fun receipts() = Flowable.just(emptyList<Receipt>())
        override fun changeQuantity(product: Product, delta: Int) = Completable.complete()
        override fun remove(productId: String) = Completable.complete()
        override fun checkout(request: PaymentRequest): Single<Receipt> { calls++; return pending }
    }
    private val request = PaymentRequest("same-attempt", PaymentMethod.CARD, null)
    private val receipt = Receipt("same-attempt", 1, emptyList(), 25000, PaymentMethod.CARD, PaymentStatus.SUCCESS, null, 0)

    @Test fun duplicateTapIsRejectedWhilePaymentIsPending() {
        val useCase = CheckoutUseCase(repository)
        val first = useCase.execute(request).test()
        useCase.execute(request.copy(attemptId = "second")).test().assertError(IllegalStateException::class.java)
        org.junit.Assert.assertEquals(1, calls)
        pending.onSuccess(receipt)
        first.assertValue(receipt).assertComplete()
    }
    @Test fun failureReleasesThePaymentGate() {
        val useCase = CheckoutUseCase(repository)
        val first = useCase.execute(request).test()
        pending.onError(IllegalArgumentException("failure"))
        first.assertError(IllegalArgumentException::class.java)
        useCase.execute(request.copy(attemptId = "retry")).test().assertError(IllegalArgumentException::class.java)
        org.junit.Assert.assertEquals(2, calls)
    }
    @Test fun disposingReleasesThePaymentGate() {
        val useCase = CheckoutUseCase(repository)
        useCase.execute(request).test().dispose()
        val retry = useCase.execute(request).test()
        org.junit.Assert.assertEquals(2, calls)
        retry.dispose()
    }
}
