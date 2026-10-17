package vn.nghia.pos.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import dagger.Binds
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import vn.nghia.pos.data.*
import vn.nghia.pos.domain.*
import vn.nghia.pos.ui.PosViewModel
import javax.inject.Singleton

@Singleton
@Component(modules = [AppModule::class, RepositoryModule::class])
interface AppComponent {
    fun viewModelFactory(): PosViewModel.Factory
    fun database(): PosDatabase
    fun repository(): PosRepository
    @Component.Factory interface Factory {
        fun create(@BindsInstance context: Context): AppComponent
    }
}

@Module
object AppModule {
    @Provides @Singleton fun gson(): Gson = Gson()
    @Provides @Singleton fun database(context: Context): PosDatabase =
        Room.databaseBuilder(context, PosDatabase::class.java, "nghia-pos.db").build()
    @Provides @Singleton fun terminal(): PaymentTerminal = FakePaymentTerminal()
    @Provides @Singleton fun api(context: Context, gson: Gson): CatalogApi = Retrofit.Builder()
        .baseUrl("https://demo.nghia.invalid/api/")
        .client(OkHttpClient.Builder().addInterceptor(LocalCatalogInterceptor(context)).build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build().create(CatalogApi::class.java)
    @Provides @Singleton fun checkout(repository: PosRepository) = CheckoutUseCase(repository)
}

@Module
interface RepositoryModule {
    @Binds fun repository(implementation: LocalPosRepository): PosRepository
}
