package vn.nghia.pos.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import vn.nghia.pos.domain.*
import java.util.UUID
import javax.inject.Inject

class PosViewModel(
    private val repository: PosRepository,
    private val checkout: CheckoutUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val disposables = CompositeDisposable()
    private val writes = PublishSubject.create<Completable>().toSerialized()
    private val _products = MutableLiveData<List<Product>>(emptyList())
    private val _cart = MutableLiveData<List<CartLine>>(emptyList())
    private val _receipts = MutableLiveData<List<Receipt>>(emptyList())
    private val _busy = MutableLiveData(false)
    private val _catalogLoading = MutableLiveData(false)
    private val _catalogError = MutableLiveData<String?>(null)
    private val _paymentError = MutableLiveData<String?>(null)
    val products: LiveData<List<Product>> = _products
    val cart: LiveData<List<CartLine>> = _cart
    val receipts: LiveData<List<Receipt>> = _receipts
    val busy: LiveData<Boolean> = _busy
    val catalogLoading: LiveData<Boolean> = _catalogLoading
    val catalogError: LiveData<String?> = _catalogError
    val paymentError: LiveData<String?> = _paymentError
    val message = MutableLiveData<String?>(null)
    val openReceipt = MutableLiveData<String?>(null)
    var query: String
        get() = savedState["query"] ?: ""
        set(value) { savedState["query"] = value }
    var category: String
        get() = savedState["category"] ?: "Tất cả"
        set(value) { savedState["category"] = value }
    var method: PaymentMethod
        get() = PaymentMethod.valueOf(savedState["method"] ?: PaymentMethod.CASH.name)
        set(value) { savedState["method"] = value.name }
    var scenario: DemoScenario
        get() = DemoScenario.valueOf(savedState["scenario"] ?: DemoScenario.APPROVED.name)
        set(value) { savedState["scenario"] = value.name }
    var cashText: String
        get() = savedState["cash"] ?: ""
        set(value) { savedState["cash"] = value }

    init {
        // Every cart write and checkout runs in order; rapid taps cannot lose increments.
        disposables.add(writes.concatMapCompletable { action ->
            action.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .doOnError { message.value = it.message ?: "Không thể lưu thay đổi." }.onErrorComplete()
        }.subscribe({}, { message.postValue("Hàng đợi dữ liệu bị gián đoạn. Hãy mở lại ứng dụng.") }))
        disposables.add(repository.cart().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ _cart.value = it }, { message.value = "Không thể đọc giỏ hàng: ${it.message}" }))
        disposables.add(repository.receipts().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ _receipts.value = it }, { message.value = "Không thể đọc lịch sử: ${it.message}" }))
        loadProducts()
    }

    fun loadProducts() {
        if (_catalogLoading.value == true) return
        _catalogLoading.value = true
        _catalogError.value = null
        disposables.add(repository.products().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ _products.value = it; _catalogLoading.value = false }, {
                _catalogLoading.value = false
                _catalogError.value = "Chưa tải được thực đơn. Vui lòng thử lại."
            }))
    }

    fun change(product: Product, delta: Int) {
        if (_busy.value != true) writes.onNext(repository.changeQuantity(product, delta))
    }
    fun remove(productId: String) {
        if (_busy.value != true) writes.onNext(repository.remove(productId))
    }
    fun clearPaymentError() { _paymentError.value = null }

    fun pay() {
        if (_busy.value == true) return
        val request = PaymentRequest(UUID.randomUUID().toString(), method, cashText.toLongOrNull(), scenario)
        try { MoneyRules.validate(_cart.value.orEmpty(), request) }
        catch (error: IllegalArgumentException) { _paymentError.value = error.message; return }
        _busy.value = true
        _paymentError.value = null
        writes.onNext(checkout.execute(request).observeOn(AndroidSchedulers.mainThread()).doOnSuccess { receipt ->
            if (receipt.status == PaymentStatus.SUCCESS) {
                cashText = ""
                openReceipt.value = receipt.id
            } else {
                _paymentError.value = when (receipt.status) {
                    PaymentStatus.DECLINED -> "Thẻ mô phỏng bị từ chối. Giỏ hàng được giữ nguyên; chọn kịch bản khác để thử lại."
                    else -> "Không kết nối được thiết bị mô phỏng. Chưa ghi nhận thanh toán; bạn có thể thử lại."
                }
            }
        }.doOnError { _paymentError.value = it.message ?: "Thanh toán chưa hoàn tất." }
            .doFinally { _busy.value = false }.ignoreElement())
    }

    override fun onCleared() { disposables.clear() }

    class Factory @Inject constructor(private val repository: PosRepository, private val checkout: CheckoutUseCase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(PosViewModel::class.java))
            return PosViewModel(repository, checkout, extras.createSavedStateHandle()) as T
        }
    }
}
