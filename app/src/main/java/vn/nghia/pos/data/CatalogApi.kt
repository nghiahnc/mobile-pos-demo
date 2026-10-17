package vn.nghia.pos.data

import android.content.Context
import io.reactivex.rxjava3.core.Single
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.http.GET
import vn.nghia.pos.domain.Product

interface CatalogApi {
    @GET("products") fun products(): Single<List<Product>>
}

/** Always terminates locally. No chain.proceed(), DNS, sockets or INTERNET permission. */
class LocalCatalogInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val known = request.method == "GET" && request.url.encodedPath == "/api/products"
        val json = if (known) context.assets.open("products.json").bufferedReader().use { it.readText() }
        else "{\"error\":\"Unknown demo endpoint\"}"
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (known) 200 else 404).message(if (known) "OK (local demo)" else "Not found")
            .body(json.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
    }
}
