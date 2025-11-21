package com.example.appbook.adapters


import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // <<— Cần thêm import Glide
import com.example.appbook.MyApplication
import com.example.appbook.activities.PdfDetailActivity
import com.example.appbook.activities.PdfEditActivity
import com.example.appbook.databinding.RowPdfAdminBinding
import com.example.appbook.filters.FilterPdfAdmin
import com.example.appbook.models.ModelPdf
import com.example.appbook.R
import java.util.concurrent.TimeUnit

class AdapterPdfAdmin : RecyclerView.Adapter<AdapterPdfAdmin.HolderPdfAdmin>, Filterable {


    private val TAG = "ADAPTER_PDF_ADMIN"
    private var context: Context
    public var pdfArrayList: ArrayList<ModelPdf>
    private val filterList: ArrayList<ModelPdf>

    private lateinit var binding: RowPdfAdminBinding
    private var filter: FilterPdfAdmin? = null

    // Constructor (Giữ nguyên)
    constructor(context: Context, pdfArrayList: ArrayList<ModelPdf>) : super() {
        this.context = context
        this.pdfArrayList = pdfArrayList
        this.filterList = pdfArrayList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderPdfAdmin {
        //bind/inflate layout row_pdf_admin.xml
        binding = RowPdfAdminBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderPdfAdmin(binding.root)
    }

    override fun onBindViewHolder(holder: HolderPdfAdmin, position: Int) {
        // get data
        val model = pdfArrayList[position]
        val pdfId = model.id
        val categoryId = model.categoryId
        val title = model.title
        val description = model.description

        // Lấy dữ liệu mới đã được tính toán sẵn
        val pdfUrl = model.url
        val timestamp = model.timestamp
        val fileSize = model.fileSize // Lấy dung lượng (bytes)
        val pagesCount = model.pagesCount // Lấy số trang
        val imageUrl = model.imageUrl // Lấy URL ảnh bìa

        // convert timestamp to dd/MM/yyy format
        val formattedDate = MyApplication.formatTimeStamp(timestamp) // Vẫn dùng MyApplication.Companion

        //set data
        holder.titleTv.text = title
        holder.descriptionTv.text = description
        holder.dateTv.text = formattedDate

        // =================== CẬP NHẬT LOGIC TẢI DỮ LIỆU ===================

        // 1. Load Category (Giữ nguyên)
        MyApplication.loadCategory(categoryId, holder.categoryTv)

        // 2. Load Ảnh Bìa (imageUrl) thay vì PDF View
        holder.progressBar.visibility = View.VISIBLE // Hiển thị ProgressBar khi tải ảnh

        try {
            // Sử dụng Glide để tải ảnh bìa
            Glide.with(context)
                .load(imageUrl) // Sử dụng URL ảnh bìa
                .centerCrop()
                .placeholder(R.drawable.ic_book_white) // Cần thêm placeholder image
                .into(holder.coverIv)
        } catch (e: Exception) {
            Log.e(TAG, "onBindViewHolder: Error loading image", e)
            holder.coverIv.setImageResource(R.drawable.ic_book_white)
        } finally {
            holder.progressBar.visibility = View.GONE // Ẩn ProgressBar
        }

        // 3. Hiển thị Dữ liệu đã tính toán (fileSize, pagesCount)
        holder.sizeTv.text = formatFileSize(fileSize) // Dùng hàm formatFileSize mới
        holder.pagesTv.text = "$pagesCount trang"

        // 4. LOẠI BỎ CÁC HÀM TẢI NẶNG (đã được thay thế):
        // BỎ: MyApplication.Companion.loadPdfFromUrlSinglePage(pdfUrl, title, holder.pdfView, holder.progressBar, null)
        // BỎ: MyApplication.Companion.loadPdfSizeFromCloudinary(pdfUrl, holder.sizeTv)

        // ===================================================================

        //handle click, show dialog with options 1 edit 2 delete
        holder.moreBtn.setOnClickListener {
            moreOptionsDialog(model, holder)
        }

        //handle item click, open PdfDetailActivity
        holder.itemView.setOnClickListener {
            val intent = Intent(context, PdfDetailActivity::class.java)
            intent.putExtra("bookId", pdfId)
            context.startActivity(intent)
        }
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


    private fun moreOptionsDialog(model: ModelPdf, holder: HolderPdfAdmin) {
        // ... (Giữ nguyên logic moreOptionsDialog)
        val bookId = model.id
        val bookTitle = model.title

        val options = arrayOf("Chỉnh Sửa", "Xoá")

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Chọn Tuỳ Chọn")
            .setItems(options){ dialog, position ->
                if(position == 0){
                    var intent = Intent(context, PdfEditActivity::class.java)
                    intent.putExtra("bookId", bookId)
                    context.startActivity(intent)
                }
                else if(position == 1){
                    // Gọi hàm xóa sách (Giữ nguyên cấu trúc MyApplication.deleteBook)
                    MyApplication.deleteBook(context, bookId, bookTitle)

                }
            }
            .show()
    }


    override fun getItemCount(): Int {
        return pdfArrayList.size
    }

    override fun getFilter(): Filter? {
        if(filter == null){
            filter = FilterPdfAdmin(this, filterList)
        }
        return filter as FilterPdfAdmin
    }

    // View holder class cho row_pdf_admin.xml (CẬP NHẬT)
    inner class HolderPdfAdmin(itemView: View) : RecyclerView.ViewHolder(itemView){
        // Cập nhật: pdfView -> coverIv (ImageView) và THÊM pagesTv
        val coverIv = binding.coverIv // <-- Thay thế cho pdfView
        val progressBar = binding.progressBar
        val titleTv = binding.titleTv
        val descriptionTv = binding.descriptionTv
        val categoryTv = binding.categoryTv
        val sizeTv = binding.sizeTv
        val dateTv = binding.dateTv
        val moreBtn = binding.moreBtn
        val pagesTv = binding.pagesTv // <-- TextView mới cho số trang
    }
}