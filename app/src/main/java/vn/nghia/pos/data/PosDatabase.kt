package vn.nghia.pos.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import io.reactivex.rxjava3.core.Flowable

@Entity(tableName = "cart")
data class CartEntity(
    @PrimaryKey val productId: String,
    val name: String,
    val category: String,
    val price: Long,
    val note: String,
    val quantity: Int
)

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val linesJson: String,
    val total: Long,
    val method: String,
    val status: String,
    val tendered: Long?,
    val change: Long
)

@Dao
interface PosDao {
    @Query("SELECT * FROM cart ORDER BY name")
    fun observeCart(): Flowable<List<CartEntity>>
    @Query("SELECT * FROM cart ORDER BY name")
    fun readCart(): List<CartEntity>
    @Query("SELECT * FROM cart WHERE productId = :id")
    fun cartLine(id: String): CartEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putCart(line: CartEntity)
    @Query("DELETE FROM cart WHERE productId = :id")
    fun removeCart(id: String)
    @Query("DELETE FROM cart")
    fun clearCart()
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC, id DESC")
    fun observeReceipts(): Flowable<List<ReceiptEntity>>
    @Query("SELECT * FROM receipts WHERE id = :id")
    fun receipt(id: String): ReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertReceipt(receipt: ReceiptEntity)
}

@Database(entities = [CartEntity::class, ReceiptEntity::class], version = 1, exportSchema = true)
abstract class PosDatabase : RoomDatabase() {
    abstract fun dao(): PosDao
}
