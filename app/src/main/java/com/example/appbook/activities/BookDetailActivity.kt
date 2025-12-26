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
import com.example.appbook.databinding.ActivityBookDetailBinding
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

    // Chỉ giữ lại List dữ liệu để xử lý Logic nút Đọc, không cần Adapter hiển thị nữa
    private lateinit var chapterArrayList: ArrayList<ModelChapter>

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
        loadChapters() // Hàm này giờ chỉ để lấy dữ liệu ngầm
        showComments()

        // Sự kiện Click
        binding.backBtn.setOnClickListener { onBackPressed() }

        // Logic Nút Đọc Ngay
        // Trong BookDetailActivity.kt -> onCreate()

        binding.readBookBtn.setOnClickListener {
            if (::chapterArrayList.isInitialized && chapterArrayList.isNotEmpty()) {
                val user = firebaseAuth.currentUser

                if (user != null) {
                    // 1. Nếu đã đăng nhập -> Kiểm tra lịch sử đọc trên Firebase
                    val progressRef = FirebaseDatabase.getInstance().getReference("UserReadingProgress")
                        .child(user.uid).child(bookId)

                    progressRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                // 2. Nếu có lịch sử -> Lấy ID chương đang đọc dở
                                val lastChapterId = snapshot.child("chapterId").value.toString()

                                // Mở ReadingActivity với chương dở dang này
                                openReadingActivity(lastChapterId)
                            } else {
                                // 3. Nếu chưa đọc bao giờ -> Mở chương 1
                                val firstChapterId = chapterArrayList[0].id
                                openReadingActivity(firstChapterId)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            // Lỗi mạng -> Mở chương 1 cho chắc
                            val firstChapterId = chapterArrayList[0].id
                            openReadingActivity(firstChapterId)
                        }
                    })
                } else {
                    // 4. Nếu chưa đăng nhập -> Mặc định mở chương 1
                    val firstChapterId = chapterArrayList[0].id
                    openReadingActivity(firstChapterId)
                }
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
                    val author = "${snapshot.child("author").value}"

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

    // --- 2. LOAD DANH SÁCH CHƯƠNG (ĐÃ SỬA LẠI) ---
    // Hàm này giờ chỉ tải dữ liệu về để nút "Đọc Ngay" hoạt động đúng
    private fun loadChapters() {
        chapterArrayList = ArrayList()

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

                // CẬP NHẬT TRẠNG THÁI NÚT ĐỌC (Thay vì hiển thị list)
                if (chapterArrayList.isEmpty()) {
                    binding.readBookBtn.isEnabled = false
                    binding.readBookBtn.text = "Chưa có nội dung"
                    binding.readBookBtn.alpha = 0.5f // Làm mờ nút
                } else {
                    binding.readBookBtn.isEnabled = true
                    binding.readBookBtn.text = "Đọc Ngay"
                    binding.readBookBtn.alpha = 1.0f // Làm sáng nút
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

    // --- 3. COMMENT & FAVORITE (GIỮ NGUYÊN) ---

    private fun showComments() {
        commentArrayList = ArrayList()
        // Cần đảm bảo RecyclerView Comment trong XML có nestedScrollingEnabled=false nếu nằm trong ScrollView
        // Nhưng ở layout mới bạn đã cấu hình đúng rồi.
        binding.commentsRv.layoutManager = LinearLayoutManager(this)

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
                return@setOnClickListener
            }

            // Validate HTML (Bảo mật)
            val htmlPattern = Regex("<(\"[^\"]*\"|'[^']*'|[^'\">])*>")
            if (htmlPattern.containsMatchIn(comment)) {
                Toast.makeText(this, "Bình luận không được chứa ký tự đặc biệt!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            alertDialog.dismiss()
            addComment(comment)
        }
    }

    private fun addComment(comment: String) {
        progressDialog.setMessage("Đang gửi bình luận...")
        progressDialog.show()

        val timestamp = System.currentTimeMillis()
        val hashMap = HashMap<String, Any>()
        hashMap["id"] = "$timestamp"
        hashMap["bookId"] = bookId
        hashMap["timestamp"] = timestamp
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