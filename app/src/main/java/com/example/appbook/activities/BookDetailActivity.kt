package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.adapters.AdapterComment
import com.example.appbook.adapters.ChapterAdapter // Adapter bạn đã tạo ở bước trước
import com.example.appbook.databinding.ActivityBookDetailBinding // Binding tự sinh từ layout mới
import com.example.appbook.databinding.DialogCommentAddBinding
import com.example.appbook.models.ModelComment
import com.example.appbook.models.ModelChapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class BookDetailActivity : AppCompatActivity() {

    // Binding theo layout mới (activity_book_detail.xml)
    private lateinit var binding: ActivityBookDetailBinding

    private companion object { const val TAG = "BOOK_DETAIL_TAG" }

    private var bookId = ""
    private var isInMyFavorite = false

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog

    // Lists
    private lateinit var commentArrayList: ArrayList<ModelComment>
    private lateinit var adapterComment: AdapterComment

    private lateinit var chapterArrayList: ArrayList<ModelChapter>
    private lateinit var adapterChapter: ChapterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra("bookId") ?: ""

        // Init Progress Dialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Vui lòng đợi...")
        progressDialog.setCanceledOnTouchOutside(false)

        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser != null) {
            checkIsFavorite()
        }

        // Tăng view
        MyApplication.incrementBookViewCount(bookId)

        // Load dữ liệu
        loadBookDetails()
        loadChapters() // MỚI: Tải danh sách chương
        showComments()

        // Sự kiện Click
        binding.backBtn.setOnClickListener { onBackPressed() }

        binding.readBookBtn.setOnClickListener {
            // Mặc định đọc chương đầu tiên nếu có
            if (chapterArrayList.isNotEmpty()) {
                val firstChapter = chapterArrayList[0]
                openReadingActivity(firstChapter.id)
            } else {
                Toast.makeText(this, "Sách này chưa có nội dung!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.favoriteBtn.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(this, "Bạn cần đăng nhập!", Toast.LENGTH_SHORT).show()
            } else {
                if (isInMyFavorite) {
                    MyApplication.removeFromFavorite(this, bookId)
                } else {
                    addToFavorite()
                }
            }
        }

        binding.addCommentBtn.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(this, "Bạn cần đăng nhập!", Toast.LENGTH_SHORT).show()
            } else {
                addCommentDialog()
            }
        }
    }

    // --- 1. LOAD THÔNG TIN SÁCH ---
    private fun loadBookDetails() {
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy dữ liệu
                    val title = "${snapshot.child("title").value}"
                    val description = "${snapshot.child("description").value}"
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"
                    val imageUrl = "${snapshot.child("imageUrl").value}"
                    val author = "${snapshot.child("author").value}" // Lấy tên tác giả

                    // Gán vào View
                    binding.titleTv.text = title
                    binding.descriptionTv.text = description
                    binding.viewsTv.text = "Lượt xem: $viewsCount"
                    binding.authorTv.text = "Tác giả: ${if(author == "null" || author.isEmpty()) "Không rõ" else author}"

                    // Load Danh mục
                    MyApplication.loadCategory(categoryId, binding.categoryTv)

                    // Load Ảnh bìa
                    try {
                        Glide.with(this@BookDetailActivity)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_book_white)
                            .into(binding.coverIv)
                    } catch (e: Exception) { }
                }

                override fun onCancelled(error: DatabaseError) { }
            })
    }

    // --- 2. LOAD DANH SÁCH CHƯƠNG (QUAN TRỌNG) ---
    private fun loadChapters() {
        chapterArrayList = ArrayList()
        // Khởi tạo Adapter trước
        adapterChapter = ChapterAdapter(chapterArrayList) { chapter ->
            // Sự kiện khi bấm vào 1 dòng chương
            openReadingActivity(chapter.id)
        }
        binding.rvChapters.layoutManager = LinearLayoutManager(this)
        binding.rvChapters.adapter = adapterChapter

        val ref = FirebaseDatabase.getInstance().getReference("Chapters").child(bookId)
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chapterArrayList.clear()
                for (ds in snapshot.children) {
                    val model = ds.getValue(ModelChapter::class.java)
                    if (model != null) {
                        chapterArrayList.add(model)
                    }
                }

                // Sắp xếp chương theo thời gian
                chapterArrayList.sortBy { it.timestamp }

                adapterChapter.notifyDataSetChanged()

                // Ẩn hiện giao diện
                if (chapterArrayList.isEmpty()) {
                    binding.rvChapters.visibility = View.GONE
                    binding.tvNoChapters.visibility = View.VISIBLE
                    binding.readBookBtn.isEnabled = false
                    binding.readBookBtn.text = "Chưa có nội dung"
                } else {
                    binding.rvChapters.visibility = View.VISIBLE
                    binding.tvNoChapters.visibility = View.GONE
                    binding.readBookBtn.isEnabled = true
                    binding.readBookBtn.text = "Đọc Ngay"
                }
            }

            override fun onCancelled(error: DatabaseError) { }
        })
    }

    private fun openReadingActivity(chapterId: String) {
        val intent = Intent(this, ReadingActivity::class.java)
        intent.putExtra("BOOK_ID", bookId)
        intent.putExtra("CHAPTER_ID", chapterId)
        startActivity(intent)
    }

    // --- 3. CÁC HÀM CŨ (COMMENT, FAVORITE) ---
    // (Giữ nguyên logic của bạn nhưng đã dọn dẹp gọn gàng)

    private fun showComments() {
        commentArrayList = ArrayList()
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).child("Comments")
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    commentArrayList.clear()
                    for(ds in snapshot.children){
                        val model = ds.getValue(ModelComment::class.java)
                        if (model != null) commentArrayList.add(model)
                    }
                    adapterComment = AdapterComment(this@BookDetailActivity, commentArrayList)
                    binding.commentsRv.adapter = adapterComment
                }
                override fun onCancelled(error: DatabaseError) { }
            })
    }

    private fun addCommentDialog() {
        val commentAddBinding = DialogCommentAddBinding.inflate(LayoutInflater.from(this))
        val builder = AlertDialog.Builder(this, R.style.CustomDialog)
        builder.setView(commentAddBinding.root)
        val alertDialog = builder.create()
        alertDialog.show()

        commentAddBinding.backBtn.setOnClickListener { alertDialog.dismiss() }
        commentAddBinding.submitBtn.setOnClickListener {
            val comment = commentAddBinding.commentEt.text.toString().trim()
            if(comment.isEmpty()){
                Toast.makeText(this, "Nhập nội dung...", Toast.LENGTH_SHORT).show()
            } else {
                alertDialog.dismiss()
                addComment(comment)
            }
        }
    }

    private fun addComment(comment: String) {
        progressDialog.setMessage("Đang gửi bình luận...")
        progressDialog.show()

        val timestamp = System.currentTimeMillis() // Dùng Long thay vì String cho timestamp
        val hashMap = HashMap<String, Any>()
        hashMap["id"] = "$timestamp"
        hashMap["bookId"] = bookId
        hashMap["timestamp"] = timestamp // Lưu dạng số Long
        hashMap["comment"] = comment
        hashMap["uid"] = "${firebaseAuth.uid}"

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).child("Comments").child("$timestamp")
            .setValue(hashMap)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Đã gửi bình luận!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                progressDialog.dismiss()
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkIsFavorite() {
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    isInMyFavorite = snapshot.exists()
                    if (isInMyFavorite) {
                        binding.favoriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_favorite_filled_white, 0, 0, 0)
                    } else {
                        binding.favoriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_favorite_white, 0, 0, 0)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun addToFavorite() {
        val timestamp = System.currentTimeMillis()
        val hashMap = HashMap<String, Any>()
        hashMap["bookId"] = bookId
        hashMap["timeStamp"] = timestamp

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
            .setValue(hashMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi thêm yêu thích", Toast.LENGTH_SHORT).show()
            }
    }
}