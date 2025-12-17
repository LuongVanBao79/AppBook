package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appbook.R
import com.example.appbook.activities.BookDetailActivity
// import file detail activity của bạn, ví dụ PdfDetailActivity
// import com.example.appbook.activities.PdfDetailActivity
import com.example.appbook.models.ModelBook // Giả sử bạn có model này

class AdapterBookSearch(
    val context: Context,
    var bookList: ArrayList<ModelBook>
) : RecyclerView.Adapter<AdapterBookSearch.HolderBookSearch>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderBookSearch {
        val view = LayoutInflater.from(context).inflate(R.layout.row_search_book, parent, false)
        return HolderBookSearch(view)
    }

    override fun onBindViewHolder(holder: HolderBookSearch, position: Int) {
        val model = bookList[position]
        holder.titleTv.text = model.title // Chỉ hiển thị tên sách

        // Xử lý khi click vào tên sách -> Mở chi tiết sách
        holder.itemView.setOnClickListener {
            // Chuyển sang trang chi tiết sách (Sửa lại Class đích cho đúng với project của bạn)

            val intent = Intent(context, BookDetailActivity::class.java)
            intent.putExtra("bookId", model.id)
            context.startActivity(intent)

        }
    }

    override fun getItemCount(): Int {
        return bookList.size
    }

    inner class HolderBookSearch(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTv: TextView = itemView.findViewById(R.id.titleTv)
    }
}