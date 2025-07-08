package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.adapters.AdapterPdfFavorite
import com.example.appbook.databinding.ActivityProfileBinding
import com.example.appbook.models.ModelPdf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityProfileBinding

    // Firebase Authentication
    private lateinit var firebaseAuth: FirebaseAuth

    // Firebase Current User
    private lateinit var firebaseUser: FirebaseUser

    // ArrayList để lưu trữ danh sách các sách yêu thích
    private lateinit var booksArrayList: ArrayList<ModelPdf>

    // Adapter để hiển thị danh sách các sách yêu thích
    private lateinit var adapterPdfFavorite: AdapterPdfFavorite

    // Progress dialog để hiển thị thông báo trong quá trình xử lý
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Reset các text view về giá trị mặc định
        binding.accountTypeTv.text = "N/A"
        binding.memberDateTv.text = "N/A"
        binding.favoriteBookCountTv.text = "N/A"
        binding.accountStatusTv.text = "N/A"

        // Khởi tạo Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseUser = firebaseAuth.currentUser!!

        // Khởi tạo progress dialog
        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi...")
            setCanceledOnTouchOutside(false)
        }

        // Tải thông tin người dùng
        loadUserInfo()

        // Tải danh sách các sách yêu thích
        loadFavoriteBooks()

        // Xử lý sự kiện click, quay lại
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        // Xử lý sự kiện click, mở trang chỉnh sửa profile
        binding.profileEditBtn.setOnClickListener {
            startActivity(Intent(this, ProfileEditActivity::class.java))
        }

        // Xử lý sự kiện click, xác minh email nếu chưa xác minh
        binding.accountStatusTv.setOnClickListener {
            if (firebaseUser.isEmailVerified) {
                // Nếu email đã được xác minh
                Toast.makeText(this, "Email đã được xác minh!", Toast.LENGTH_SHORT).show()
            } else {
                // Nếu email chưa được xác minh, hiển thị dialog xác nhận
                emailVerificationDialog()
            }
        }
    }

    // Hàm hiển thị dialog xác nhận gửi email xác minh
    private fun emailVerificationDialog() {
        // Hiển thị dialog xác nhận
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Xác minh Email")
            .setMessage("Bạn có chắc chắn muốn gửi hướng dẫn xác minh email đến ${firebaseUser.email}?")
            .setPositiveButton("GỬI") { d, e ->
                sendEmailVerification() // Gửi email xác minh
            }
            .setNegativeButton("HỦY") { d, e ->
                d.dismiss() // Đóng dialog
            }
            .show()
    }

    // Hàm gửi email xác minh
    private fun sendEmailVerification() {
        // Hiển thị progress dialog
        progressDialog.setMessage("Đang gửi hướng dẫn xác minh email đến ${firebaseUser.email}")
        progressDialog.show()

        // Gửi hướng dẫn xác minh
        firebaseUser.sendEmailVerification()
            .addOnSuccessListener {
                // Nếu gửi thành công
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Hướng dẫn đã được gửi! Vui lòng kiểm tra email ${firebaseUser.email}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                // Nếu gửi thất bại
                progressDialog.dismiss()
                Toast.makeText(this, "Gửi thất bại do ${e.message}!", Toast.LENGTH_SHORT).show()
            }
    }

    // Hàm tải thông tin người dùng từ Firebase
    private fun loadUserInfo() {
        // Kiểm tra xem email đã được xác minh hay chưa
        binding.accountStatusTv.text =
            if (firebaseUser.isEmailVerified) "Đã xác minh" else "Chưa xác minh"

        // Tham chiếu đến node "Users" trong Firebase
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy thông tin người dùng từ snapshot
                    val email = snapshot.child("email").value.toString()
                    val name = snapshot.child("name").value.toString()
                    val profileImage = snapshot.child("profileImage").value.toString()
                    val timestamp = snapshot.child("timestamp").value.toString()
                    val userType = snapshot.child("userType").value.toString()

                    // Chuyển đổi timestamp sang định dạng ngày tháng
                    val formattedDate = MyApplication.formatTimeStamp(timestamp.toLong())

                    // Set thông tin lên view
                    binding.nameTv.text = name
                    binding.emailTv.text = email
                    binding.memberDateTv.text = formattedDate
                    binding.accountTypeTv.text = userType

                    // Tải ảnh profile
                    try {
                        Glide.with(this@ProfileActivity)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(binding.profileIv)
                    } catch (e: Exception) {
                        // Xử lý lỗi nếu có
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý lỗi nếu có
                }
            })
    }

    // Hàm tải danh sách các sách yêu thích từ Firebase
    private fun loadFavoriteBooks() {
        // Khởi tạo arraylist
        booksArrayList = ArrayList()

        // Tham chiếu đến node "Favorites" của người dùng trong Firebase
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Xóa arraylist trước khi thêm dữ liệu mới
                    booksArrayList.clear()
                    for (ds in snapshot.children) {
                        // Lấy bookId từ snapshot
                        val bookId = ds.child("bookId").value.toString()

                        // Tạo model
                        val modelPdf = ModelPdf()
                        modelPdf.id = bookId // Chỉ gán bookId, các thông tin khác sẽ được tải trong Adapter

                        // Thêm model vào list
                        booksArrayList.add(modelPdf)
                    }

                    // Set số lượng sách yêu thích
                    binding.favoriteBookCountTv.text = "${booksArrayList.size}"

                    // Thiết lập adapter
                    adapterPdfFavorite = AdapterPdfFavorite(this@ProfileActivity, booksArrayList)

                    // Set adapter cho recyclerview
                    binding.favoriteRv.adapter = adapterPdfFavorite
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý lỗi nếu có
                }
            })
    }
}
