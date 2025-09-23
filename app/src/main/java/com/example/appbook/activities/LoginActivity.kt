package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LoginActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityLoginBinding

    // Firebase Authentication để xác thực người dùng
    private lateinit var firebaseAuth: FirebaseAuth

    // ProgressDialog để hiển thị thông báo trong khi thực hiện các tác vụ
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance()

        // Khởi tạo ProgressDialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Vui lòng đợi") // Thiết lập tiêu đề
        progressDialog.setCanceledOnTouchOutside(false) // Ngăn người dùng tắt bằng cách chạm ra ngoài

        // Xử lý sự kiện click vào TextView "Chưa có tài khoản? Đăng ký"
        binding.noAccountTv.setOnClickListener {
            // Mở màn hình đăng ký (RegisterActivity)
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        /*
        Các bước thực hiện đăng nhập:
        1. Nhập dữ liệu (email, password)
        2. Kiểm tra dữ liệu (validate)
        3. Đăng nhập bằng Firebase Authentication
        4. Kiểm tra loại người dùng (user/admin) trong Firebase Realtime Database
            - Nếu là user: Chuyển đến màn hình DashboardUserActivity
            - Nếu là admin: Chuyển đến màn hình DashboardAdminActivity
         */

        // Xử lý sự kiện click vào nút "Đăng nhập"
        binding.loginBtn.setOnClickListener {
            validateData() // Kiểm tra dữ liệu trước khi đăng nhập
        }

        // Xử lý sự kiện click vào TextView "Quên mật khẩu?"
        binding.forgotTv.setOnClickListener {
            // Mở màn hình Quên mật khẩu (ForgotPasswordActivity)
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private var email = "" // Biến lưu trữ email
    private var password = "" // Biến lưu trữ mật khẩu

    // Hàm kiểm tra tính hợp lệ của dữ liệu nhập vào
    private fun validateData() {
        // 1. Nhập dữ liệu
        email = binding.emailEt.text.toString().trim() // Lấy email từ EditText và loại bỏ khoảng trắng
        password = binding.passwordEt.text.toString().trim() // Lấy mật khẩu từ EditText và loại bỏ khoảng trắng

        // 2. Kiểm tra dữ liệu
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            // Nếu email không đúng định dạng
            Toast.makeText(this, "Định dạng email không hợp lệ...", Toast.LENGTH_SHORT).show()
        } else if (password.isEmpty()) {
            // Nếu mật khẩu trống
            Toast.makeText(this, "Vui lòng nhập mật khẩu...", Toast.LENGTH_SHORT).show()
        } else {
            // Nếu dữ liệu hợp lệ, tiến hành đăng nhập
            loginUser()
        }
    }

    // Hàm thực hiện đăng nhập
    private fun loginUser() {
        // 3. Đăng nhập bằng Firebase Authentication

        // Hiển thị ProgressDialog
        progressDialog.setMessage("Đang đăng nhập...") // Thiết lập thông báo
        progressDialog.show() // Hiển thị ProgressDialog

        // Sử dụng Firebase Authentication để đăng nhập bằng email và mật khẩu
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Nếu đăng nhập thành công
                checkUser() // Kiểm tra loại người dùng
            }
            .addOnFailureListener { e ->
                // Nếu đăng nhập thất bại
                progressDialog.dismiss() // Ẩn ProgressDialog
                Toast.makeText(this, "Đăng nhập thất bại do ${e.message}", Toast.LENGTH_SHORT).show() // Hiển thị thông báo lỗi
            }
    }

    // Hàm kiểm tra loại người dùng (user/admin)
    private fun checkUser() {
        progressDialog.setMessage("Đang kiểm tra người dùng...") // Thiết lập thông báo

        val firebaseUser = firebaseAuth.currentUser!! // Lấy thông tin người dùng hiện tại

        // Lấy tham chiếu đến node "Users" trong Firebase Realtime Database
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        // Truy vấn thông tin người dùng dựa trên UID
        ref.child(firebaseUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Dữ liệu đã được truy xuất thành công
                    progressDialog.dismiss() // Ẩn ProgressDialog

                    // Lấy loại người dùng (user/admin) từ snapshot
                    val userType = snapshot.child("userType").value
                    if (userType == "user") {
                        // Nếu là user, chuyển đến màn hình DashboardUserActivity
                        startActivity(Intent(this@LoginActivity, DashboardUserActivity::class.java))
                        finish() // Đóng Activity hiện tại
                    } else if (userType == "admin") {
                        // Nếu là admin, chuyển đến màn hình DashboardAdminActivity
                        startActivity(
                            Intent(
                                this@LoginActivity,
                                DashboardAdminActivity::class.java
                            )
                        )
                        finish() // Đóng Activity hiện tại
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xảy ra lỗi trong quá trình truy xuất dữ liệu
                }
            })
    }
}
