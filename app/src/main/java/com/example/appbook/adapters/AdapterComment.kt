package com.example.appbook.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.databinding.RowCommentBinding
import com.example.appbook.models.ModelComment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit

class AdapterComment : RecyclerView.Adapter<AdapterComment.HolderComment> {

    val context: Context
    val commentArrayList: ArrayList<ModelComment>
    private lateinit var binding: RowCommentBinding
    private lateinit var firebaseAuth: FirebaseAuth

    // Constructor
    constructor(context: Context, commentArrayList: ArrayList<ModelComment>) : super() {
        this.context = context
        // Sắp xếp comment mới nhất lên đầu
        this.commentArrayList = commentArrayList
        this.commentArrayList.sortByDescending { it.timestamp }
        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderComment {
        binding = RowCommentBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderComment(binding.root)
    }

    override fun onBindViewHolder(holder: HolderComment, position: Int) {
        // Lấy dữ liệu
        val model = commentArrayList[position]

        val date = formatTimeAgo(model.timestamp) // Hàm tính thời gian tương đối
        holder.dateTv.text = date
        holder.commentTv.text = model.comment

        // XỬ LÝ HIỂN THỊ ADMIN REPLY
        if (model.adminReply.isNotEmpty()) {
            holder.adminReplyLayout.visibility = View.VISIBLE
            holder.replyTv.text = model.adminReply

            // Xử lý thời gian trả lời
            val replyTime = formatTimeAgo(model.replyTimestamp)
            holder.replyDateTv.text = "• $replyTime" // Thêm dấu chấm cho đẹp
        } else {
            holder.adminReplyLayout.visibility = View.GONE
        }

        // Tải thông tin người dùng (Avatar, Tên)
        loadUserDetails(model, holder)

        // --- 2. XỬ LÝ SỰ KIỆN CLICK (XÓA COMMENT CHÍNH CHỦ) ---
        holder.itemView.setOnClickListener {
            // Kiểm tra: Phải đăng nhập VÀ UID hiện tại trùng với UID người comment
            if (firebaseAuth.currentUser != null && firebaseAuth.uid == model.uid) {
                deleteCommentDialog(model)
            }
        }
    }

    /**
     * Chỉ cho phép xóa comment của chính mình
     */
    private fun deleteCommentDialog(model: ModelComment) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Xóa bình luận")
            .setMessage("Bạn có muốn xóa bình luận này không?")
            .setPositiveButton("Xóa") { d, e ->
                val bookId = model.bookId
                val commentId = model.id

                val ref = FirebaseDatabase.getInstance().getReference("Books")
                ref.child(bookId).child("Comments").child(commentId)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Đã xóa bình luận", Toast.LENGTH_SHORT).show()
                        // Xóa khỏi list hiển thị để update giao diện ngay
                        try {
                            commentArrayList.remove(model)
                            notifyDataSetChanged()
                        } catch (e: Exception) { }
                    }
                    .addOnFailureListener { e->
                        Toast.makeText(context, "Lỗi xóa: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Huỷ") { d, e ->
                d.dismiss()
            }
            .show()
    }

    // --- Các hàm phụ trợ giữ nguyên ---

    private fun formatTimeAgo(timestamp: Long): String {
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> "Vừa xong"
            minutes < 60 -> "$minutes phút trước"
            hours < 24 -> "$hours giờ trước"
            days < 30 -> "$days ngày trước"
            else -> "Lâu rồi"
        }
    }

    private fun loadUserDetails(model: ModelComment, holder: HolderComment) {
        val uid = model.uid
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = "${snapshot.child("name").value}"
                    val profileImage = "${snapshot.child("profileImage").value}"

                    holder.nameTv.text = name
                    try {
                        Glide.with(context)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(holder.profileIv)
                    } catch (e: Exception){
                        holder.profileIv.setImageResource(R.drawable.ic_person_gray)
                    }
                }
                override fun onCancelled(error: DatabaseError) { }
            })
    }

    override fun getItemCount(): Int {
        return commentArrayList.size
    }

    inner class HolderComment(itemView: View): RecyclerView.ViewHolder(itemView){
        val profileIv = binding.profileIv
        val nameTv = binding.nameTv
        val dateTv = binding.dateTv
        val commentTv = binding.commentTv

        // Mapping các view hiển thị Reply (nhớ cập nhật file row_comment.xml như bài trước)

        val adminReplyLayout = binding.adminReplyLayout
        val replyTv = binding.replyTv
        val replyDateTv = binding.replyDateTv
    }
}