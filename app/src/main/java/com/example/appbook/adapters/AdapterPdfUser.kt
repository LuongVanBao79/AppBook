package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <-- Cần import Glide
import com.example.appbook.R // <-- Cần import R cho placeholder
import com.example.appbook.MyApplication
import com.example.appbook.activities.PdfDetailActivity
import com.example.appbook.databinding.RowPdfUserBinding
import com.example.appbook.filters.FilterPdfUser
import com.example.appbook.models.ModelPdf

class AdapterPdfUser: RecyclerView.Adapter<AdapterPdfUser.HolderPdfUser>, Filterable {

    private val TAG = "ADAPTER_PDF_USER"
    private var context: Context
    public var pdfArrayList: ArrayList<ModelPdf>
    public var filterList: ArrayList<ModelPdf>
    private lateinit var binding: RowPdfUserBinding
    private var filter: FilterPdfUser? = null

    constructor(context: Context, pdfArrayList: ArrayList<ModelPdf>) {
        this.context = context
        this.pdfArrayList = pdfArrayList
        this.filterList = pdfArrayList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderPdfUser {
        binding = RowPdfUserBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderPdfUser(binding.root)
    }

    override fun onBindViewHolder(holder: HolderPdfUser, position: Int) {

        val model = pdfArrayList[position]
        val bookId = model.id
        val categoryId = model.categoryId
        val title = model.title
        val description = model.description

        // Lấy dữ liệu đã được tính toán sẵn từ ModelPdf
        val url = model.url
        val timestamp = model.timestamp
        val fileSize = model.fileSize    // Dữ liệu mới
        val pagesCount = model.pagesCount  // Dữ liệu mới
        val imageUrl = model.imageUrl      // Dữ liệu mới

        // convert time
        val date = MyApplication.formatTimeStamp(timestamp) // Vẫn dùng MyApplication

        // set data
        holder.titleTv.text = title
        holder.descriptionTv.text = description
        holder.dateTv.text = date

        // 1. Load Category (Giữ nguyên)
        MyApplication.loadCategory(categoryId, holder.categoryTv)

        // 2. Load Ảnh Bìa (ImageView) thay vì PDF View
        holder.progressBar.visibility = View.VISIBLE

        try {
            Glide.with(context)
                .load(imageUrl) // Sử dụng URL ảnh bìa
                .centerCrop()
                .placeholder(R.drawable.ic_book_white)
                .into(holder.coverIv) // Sử dụng ImageView mới
        } catch (e: Exception) {
            Log.e(TAG, "onBindViewHolder: Error loading image", e)
            holder.coverIv.setImageResource(R.drawable.ic_book_white)
        } finally {
            holder.progressBar.visibility = View.GONE
        }

        // 3. Hiển thị Dữ liệu đã tính toán (fileSize, pagesCount)
        holder.sizeTv.text = formatFileSize(fileSize)
        holder.pagesTv.text = "$pagesCount trang"

        // BỎ CÁC LỆNH GỌI NẶNG (đã được thay thế bằng dữ liệu sẵn có):
        // BỎ: MyApplication.Companion.loadPdfFromUrlSinglePage(url, title, holder.pdfView, holder.progressBar, null)
        // BỎ: MyApplication.Companion.loadPdfSizeFromCloudinary(url, holder.sizeTv)

        // handle click, open pdf details page
        holder.itemView.setOnClickListener {
            val intent = Intent(context, PdfDetailActivity::class.java)
            intent.putExtra("bookId", bookId)
            context.startActivity(intent)
        }
    }

    /**
     * Hàm tiện ích để chuyển đổi byte sang KB/MB/GB (Copy từ AdapterPdfAdmin)
     */
    private fun formatFileSize(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    override fun getItemCount(): Int {
        return pdfArrayList.size
    }

    override fun getFilter(): Filter {
        if(filter == null){
            filter = FilterPdfUser(filterList, this)
        }
        return filter as FilterPdfUser
    }

    /*ViewHolder class row_pdf_user.xml*/
    inner class HolderPdfUser(itemView: View): RecyclerView.ViewHolder(itemView){
        // CẬP NHẬT: Thay pdfView bằng coverIv (ImageView) và thêm pagesTv
        var coverIv = binding.coverIv        // Thay thế pdfView
        var progressBar = binding.progressBar
        var titleTv = binding.titleTv
        var descriptionTv = binding.descriptionTv
        var categoryTv = binding.categoryTv
        var sizeTv = binding.sizeTv
        var dateTv = binding.dateTv
        var pagesTv = binding.pagesTv       // TextView mới cho số trang
    }
}