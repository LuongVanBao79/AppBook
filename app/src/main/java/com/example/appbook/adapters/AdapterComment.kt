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

class AdapterComment: RecyclerView.Adapter<AdapterComment.HolderComment> {
    //context
    val context: Context

    //arraylist to hold comments
    val commentArrayList: ArrayList<ModelComment>

    //view binding row_comment.xml => RowCommentBinding
    private lateinit var binding: RowCommentBinding

    //firebase auth
    private lateinit var firebaseAuth: FirebaseAuth


    //constructor
    constructor(context: Context, commentArrayList: ArrayList<ModelComment>) : super() {
        this.context = context
        this.commentArrayList = commentArrayList

        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderComment {
        //bind/inflate row_comment.xml
        binding = RowCommentBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderComment(binding.root)
    }

    override fun onBindViewHolder(holder: HolderComment, position: Int) {
        // Lấy dữ liệu từ danh sách
        val model = commentArrayList[position]
        val id = model.id
        val bookId = model.bookId
        val comment = model.comment
        val uid = model.uid
        val timestamp = model.timestamp

        // Định dạng ngày từ timestamp
        val date = MyApplication.formatTimeStamp(timestamp.toLong())

        // Hiển thị dữ liệu lên giao diện
        holder.dateTv.text = date
        holder.commentTv.text = comment

        // Tải thông tin người dùng theo uid
        loadUserDetails(model, holder)

        // Xử lý khi click vào comment (xóa nếu có quyền)
        holder.itemView.setOnClickListener {
            if (firebaseAuth.currentUser != null) {
                val currentUid = firebaseAuth.uid

                val userRef = FirebaseDatabase.getInstance().getReference("Users")
                userRef.child(currentUid!!)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val userType = snapshot.child("userType").value.toString()

                            // Nếu là admin hoặc là người đăng bình luận thì cho phép xóa
                            if (userType == "admin" || currentUid == uid) {
                                deleteCommmentDialog(model, holder)
                            } else {
                                Toast.makeText(context, "Bạn không có quyền xóa bình luận này", Toast.LENGTH_SHORT).show()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(context, "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        }
    }


    private fun deleteCommmentDialog(model: ModelComment, holder: HolderComment) {
        //alert dialog
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Delete Comment")
            .setMessage("Bạn có chắc chắn muốn xóa bình luận này không?")
            .setPositiveButton("Xoá") { d, e ->
                val bookId = model.bookId
                val commentId = model.id

                //delete comment
                val ref = FirebaseDatabase.getInstance().getReference("Books")
                ref.child(bookId).child("Comments").child(commentId)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Bình luận đã được xóa...", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e->
                        //failed to delete
                        Toast.makeText(context, "Xóa bình luận thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Huỷ") { d, e ->
                d.dismiss()
            }
            .show()
    }

    private fun loadUserDetails(model: ModelComment, holder: AdapterComment.HolderComment) {
        val uid = model.uid
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //get name, profile image
                    val name = "${snapshot.child("name").value}"
                    val profileImage = "${snapshot.child("profileImage").value}"

                    //set data
                    holder.nameTv.text = name
                    try {
                        Glide.with(context)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(holder.profileIv)
                    }
                    catch (e: Exception){
                        //in case of exception duw to image is empty or null or other reason, set default image
                        holder.profileIv.setImageResource(R.drawable.ic_person_gray)
                    }
                }
                override fun onCancelled(error: DatabaseError) {

                }
            })
    }


    override fun getItemCount(): Int {
        return commentArrayList.size //return list size || number of items in list
    }

    /*ViewHolder class for row_comment.xml*/
    inner class HolderComment(itemView: View): RecyclerView.ViewHolder(itemView){
        //init ui views of row_comment.xml
        val profileIv = binding.profileIv
        val nameTv = binding.nameTv
        val dateTv = binding.dateTv
        val commentTv = binding.commentTv
    }
}