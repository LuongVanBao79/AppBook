package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Thư viện load ảnh
import com.example.appbook.activities.BookDetailActivity
import com.example.appbook.databinding.RowBookUserBinding // Tên Binding tự sinh từ XML trên
import com.example.appbook.models.ModelBook
import com.example.appbook.MyApplication // Class chứa hàm format timestamp
import java.util.*
import kotlin.collections.ArrayList
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

class AdapterBookUser(
    private val context: Context,
    public var pdfArrayList: ArrayList<ModelBook>
) : RecyclerView.Adapter<AdapterBookUser.HolderBookUser>(), Filterable {

    private var filterList: ArrayList<ModelBook> = pdfArrayList
    private lateinit var binding: RowBookUserBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderBookUser {
        binding = RowBookUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderBookUser(binding.root)
    }

    override fun onBindViewHolder(holder: HolderBookUser, position: Int) {
        // 1. Lấy dữ liệu
        val model = pdfArrayList[position]
        val title = model.title
        val author = model.author
        val description = model.description
        val timestamp = model.timestamp
        val viewsCount = model.viewsCount
        val imageUrl = model.imageUrl

        // Format ngày tháng (Giả sử bạn có hàm này trong MyApplication)
        val date = MyApplication.formatTimestamp(timestamp)

        // 2. Gán dữ liệu vào View
        holder.titleTv.text = title
        holder.descriptionTv.text = description
        holder.dateTv.text = date

        // Mới: Gán tác giả và Lượt xem
        holder.authorTv.text = if (author.isEmpty()) "N/A" else author
        holder.viewsTv.text = "$viewsCount lượt xem"

        // Load danh mục (Category) - Cần query Firebase nếu chỉ có ID
        MyApplication.loadCategory(model.categoryId, holder.categoryTv)

        // 3. Load Ảnh Bìa (Dùng Glide)
        holder.progressBar.visibility = View.VISIBLE
        try {
            Glide.with(context)
                .load(imageUrl)
                .placeholder(com.example.appbook.R.drawable.ic_book_white)
                .error(com.example.appbook.R.drawable.ic_book_white)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        TODO("Not yet implemented")
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,          // <--- KHÔNG có dấu ? (Lỗi thường ở đây)
                        target: Target<Drawable>?, // <--- Có dấu ?
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        holder.progressBar.visibility = View.GONE
                        return false
                    }
                })
                .into(holder.coverIv)
        } catch (e: Exception) {
            holder.progressBar.visibility = View.GONE
        }

        // 4. Sự kiện Click -> Mở Chi tiết Sách
        holder.itemView.setOnClickListener {
            val intent = Intent(context, BookDetailActivity::class.java)
            intent.putExtra("bookId", model.id) // Truyền ID sách sang
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return pdfArrayList.size
    }

    override fun getFilter(): Filter {
        // Logic filter tìm kiếm (Giữ nguyên logic cũ của bạn)
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                // ... code filter của bạn
                return FilterResults()
            }
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                // ... code publish
            }
        }
    }

    // ViewHolder ánh xạ các View trong XML
    inner class HolderBookUser(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTv: TextView = binding.titleTv
        val descriptionTv: TextView = binding.descriptionTv
        val categoryTv: TextView = binding.categoryTv
        val authorTv: TextView = binding.authorTv      // Mới
        val viewsTv: TextView = binding.viewsTv        // Mới
        val dateTv: TextView = binding.dateTv
        val coverIv: ImageView = binding.coverIv
        val progressBar: ProgressBar = binding.progressBar
    }
}