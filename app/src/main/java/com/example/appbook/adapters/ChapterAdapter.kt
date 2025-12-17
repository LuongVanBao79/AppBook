package com.example.appbook.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appbook.R // Đảm bảo import R của dự án bạn
import com.example.appbook.models.ModelChapter

class ChapterAdapter(
    private val chapterList: ArrayList<ModelChapter>,
    private var currentChapterId: String = "", // Thêm biến này để biết chương đang đọc
    private val onItemClick: (ModelChapter) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.HolderChapter>() {

    // Hàm cập nhật ID chương đang đọc để highlight lại
    fun updateCurrentChapter(newChapterId: String) {
        currentChapterId = newChapterId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderChapter {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_chapter_toc, parent, false)
        return HolderChapter(view)
    }

    override fun onBindViewHolder(holder: HolderChapter, position: Int) {
        val model = chapterList[position]
        holder.chapterTitleTv.text = model.title

        // LOGIC HIGHLIGHT: Nếu ID trùng với currentChapterId thì đổi màu
        if (model.id == currentChapterId) {
            holder.chapterTitleTv.setTextColor(Color.parseColor("#FF5722")) // Màu cam nổi bật
            holder.chapterTitleTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else {
            holder.chapterTitleTv.setTextColor(Color.parseColor("#333333")) // Màu đen thường
            holder.chapterTitleTv.typeface = android.graphics.Typeface.DEFAULT
        }

        holder.itemView.setOnClickListener {
            onItemClick(model)
        }
    }

    override fun getItemCount(): Int {
        return chapterList.size
    }

    inner class HolderChapter(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Đảm bảo trong layout row_chapter.xml của bạn có TextView id là chapterTitleTv
        val chapterTitleTv: TextView = itemView.findViewById(R.id.tvChapterName)
    }
}