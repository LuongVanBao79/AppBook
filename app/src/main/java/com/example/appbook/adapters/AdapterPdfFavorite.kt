package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <<-- Cần import Glide
import com.example.appbook.R // <<-- Cần import R cho Placeholder
import com.example.appbook.MyApplication
import com.example.appbook.activities.PdfDetailActivity
import com.example.appbook.databinding.RowPdfFavoriteBinding
import com.example.appbook.models.ModelPdf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AdapterPdfFavorite : RecyclerView.Adapter<AdapterPdfFavorite.HolderPdfFavorite> {

    private val TAG = "ADAPTER_FAV_TAG"
    private val context: Context
    private var booksArrayList: ArrayList<ModelPdf>
    private lateinit var binding: RowPdfFavoriteBinding

    // constructor (Giữ nguyên)
    constructor(context: Context, booksArrayList: ArrayList<ModelPdf>) : super() {
        this.context = context
        this.booksArrayList = booksArrayList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderPdfFavorite {
        binding = RowPdfFavoriteBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderPdfFavorite(binding.root)
    }

    override fun onBindViewHolder(holder: HolderPdfFavorite, position: Int) {
        val model = booksArrayList[position]

        // LoadBookDetails giờ đây sẽ điền dữ liệu vào model và holder
        loadBookDetails(model, holder)

        // handle click, open pdf details page, pass book id to load details
        holder.itemView.setOnClickListener {
            val intent = Intent(context, PdfDetailActivity::class.java)
            intent.putExtra("bookId", model.id)
            context.startActivity(intent)
        }

        // handle click, remove from favorite
        holder.removeFavBtn.setOnClickListener {
            MyApplication.removeFromFavorite(context, model.id)
        }
    }

    private fun loadBookDetails(model: ModelPdf, holder: HolderPdfFavorite) {
        val bookId = model.id

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy dữ liệu cũ
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val description = "${snapshot.child("description").value}"
                    val downloadsCount = "${snapshot.child("downloadsCount").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    val title = "${snapshot.child("title").value}"
                    val uid = "${snapshot.child("uid").value}"
                    val url = "${snapshot.child("url").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"

                    // LẤY DỮ LIỆU MỚI TỪ MODEL ĐÃ TÍNH TOÁN
                    val fileSize = "${snapshot.child("fileSize").value}"
                    val pagesCount = "${snapshot.child("pagesCount").value}"
                    val imageUrl = "${snapshot.child("imageUrl").value}"


                    // CẬP NHẬT MODEL (Quan trọng khi sử dụng cùng ArrayList)
                    model.isFavorite = true
                    model.title = title
                    model.description = description
                    model.categoryId = categoryId
                    model.timestamp = timestamp.toLongOrNull() ?: 0L
                    model.uid = uid
                    model.url = url
                    model.viewsCount = viewsCount.toLongOrNull() ?: 0L
                    model.downloadsCount = downloadsCount.toLongOrNull() ?: 0L
                    model.fileSize = fileSize.toLongOrNull() ?: 0L // <-- CẬP NHẬT SIZE
                    model.pagesCount = pagesCount.toIntOrNull() ?: 0 // <-- CẬP NHẬT PAGES
                    model.imageUrl = imageUrl // <-- CẬP NHẬT IMAGE URL


                    // Format date
                    val date = MyApplication.formatTimeStamp(model.timestamp)

                    // 1. Load Category (Giữ nguyên)
                    MyApplication.loadCategory(model.categoryId, holder.categoryTv)

                    // 2. Load Ảnh Bìa (thay thế cho PDFView)
                    holder.progressBar.visibility = View.VISIBLE
                    try {
                        Glide.with(context)
                            .load(model.imageUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_book_white)
                            .into(holder.coverIv) // Sử dụng ImageView mới
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi tải ảnh bìa: ${e.message}")
                        holder.coverIv.setImageResource(R.drawable.ic_book_white)
                    } finally {
                        holder.progressBar.visibility = View.GONE
                    }

                    // 3. HIỂN THỊ DỮ LIỆU
                    holder.titleTv.text = title
                    holder.descriptionTv.text = description
                    holder.dateTv.text = date

                    // Sử dụng hàm formatFileSize để hiển thị kích thước
                    holder.sizeTv.text = formatFileSize(model.fileSize)
                    holder.pagesTv.text = "${model.pagesCount} trang" // Hiển thị số trang

                    // LOẠI BỎ CÁC HÀM TẢI NẶNG:
                    // BỎ: MyApplication.loadPdfFromUrlSinglePage(...)
                    // BỎ: MyApplication.loadPdfSizeFromCloudinary(...)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý lỗi
                }
            })
    }

    /**
     * Hàm tiện ích để chuyển đổi byte sang KB/MB/GB
     */
    private fun formatFileSize(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    override fun getItemCount(): Int {
        return booksArrayList.size // return size of list | number of items in list
    }

    /*View holder class to manage UI views of row_pdf_favorite.xml*/
    inner class HolderPdfFavorite(itemView: View) : RecyclerView.ViewHolder(itemView){
        // CẬP NHẬT: Thay pdfView bằng coverIv và thêm pagesTv
        var coverIv = binding.coverIv        // ImageView
        var progressBar = binding.progressBar
        var titleTv = binding.titleTv
        var removeFavBtn = binding.removeFavBtn
        var descriptionTv = binding.descriptionTv
        var categoryTv = binding.categoryTv
        var sizeTv = binding.sizeTv
        var dateTv = binding.dateTv
        var pagesTv = binding.pagesTv        // TextView mới cho số trang
    }

}