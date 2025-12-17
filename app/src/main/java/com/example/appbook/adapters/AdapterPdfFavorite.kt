package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appbook.R
import com.example.appbook.MyApplication
import com.example.appbook.activities.BookDetailActivity
import com.example.appbook.databinding.RowPdfFavoriteBinding
import com.example.appbook.models.ModelBook
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdapterPdfFavorite(
    private val context: Context,
    private var booksArrayList: ArrayList<ModelBook>
) : RecyclerView.Adapter<AdapterPdfFavorite.HolderPdfFavorite>() {

    private val TAG = "ADAPTER_FAV_TAG"
    private lateinit var binding: RowPdfFavoriteBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderPdfFavorite {
        binding = RowPdfFavoriteBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderPdfFavorite(binding.root)
    }

    override fun onBindViewHolder(holder: HolderPdfFavorite, position: Int) {
        val model = booksArrayList[position]

        loadBookDetails(model, holder)

        // Click item -> Mở chi tiết sách
        holder.itemView.setOnClickListener {
            val intent = Intent(context, BookDetailActivity::class.java)
            intent.putExtra("bookId", model.id)
            context.startActivity(intent)
        }

        // Click nút xóa -> Xóa khỏi yêu thích
        holder.removeFavBtn.setOnClickListener {
            MyApplication.removeFromFavorite(context, model.id)
        }
    }

    private fun loadBookDetails(model: ModelBook, holder: HolderPdfFavorite) {
        val bookId = model.id

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy dữ liệu cơ bản
                    val title = "${snapshot.child("title").value}"
                    val description = "${snapshot.child("description").value}"
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    val imageUrl = "${snapshot.child("imageUrl").value}"

                    // Lấy dữ liệu mới (Text-based app)
                    val author = "${snapshot.child("author").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"

                    // Cập nhật Model
                    model.isFavorite = true
                    model.title = title
                    model.description = description
                    model.categoryId = categoryId
                    model.timestamp = timestamp.toLongOrNull() ?: 0L
                    model.author = if (author == "null") "" else author
                    model.viewsCount = viewsCount.toLongOrNull() ?: 0L
                    model.imageUrl = imageUrl

                    // Hiển thị ngày tháng
                    val date = MyApplication.formatTimestamp(model.timestamp)
                    holder.dateTv.text = date

                    // 1. Load Category
                    MyApplication.loadCategory(model.categoryId, holder.categoryTv)

                    // 2. Load Ảnh Bìa (Dùng Glide)
                    holder.progressBar.visibility = View.VISIBLE
                    try {
                        Glide.with(context)
                            .load(model.imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_book_white)
                            .into(holder.coverIv)
                    } catch (e: Exception) {
                        holder.coverIv.setImageResource(R.drawable.ic_book_white)
                    } finally {
                        holder.progressBar.visibility = View.GONE
                    }

                    // 3. Hiển thị thông tin lên View
                    holder.titleTv.text = title
                    holder.descriptionTv.text = description

                    // Hiển thị Tác giả thay vì số trang
                    holder.authorTv.text = if (model.author.isNotEmpty()) model.author else "Tác giả: N/A"

                    // Hiển thị Lượt xem thay vì kích thước file
                    holder.viewsTv.text = "${model.viewsCount} lượt xem"
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "onCancelled: ${error.message}")
                }
            })
    }

    override fun getItemCount(): Int {
        return booksArrayList.size
    }

    // ViewHolder ánh xạ các view từ row_pdf_favorite.xml
    inner class HolderPdfFavorite(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var coverIv = binding.coverIv
        var progressBar = binding.progressBar
        var titleTv = binding.titleTv
        var removeFavBtn = binding.removeFavBtn
        var descriptionTv = binding.descriptionTv
        var categoryTv = binding.categoryTv
        var dateTv = binding.dateTv
        var authorTv = binding.authorTv  // Thay thế pagesTv
        var viewsTv = binding.viewsTv    // Thay thế sizeTv
    }
}