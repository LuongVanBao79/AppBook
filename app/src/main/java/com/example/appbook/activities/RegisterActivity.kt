package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityRegisterBinding

    // Firebase Authentication
    private lateinit var firebaseAuth: FirebaseAuth

    // Progress dialog để hiển thị thông báo trong quá trình xử lý
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance()

        // Khởi tạo progress dialog, sẽ hiển thị trong quá trình tạo tài khoản
        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi")
            setCanceledOnTouchOutside(false)
        }

        // Xử lý sự kiện click vào nút "Quay lại", quay lại màn hình trước
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        // Xử lý sự kiện click, bắt đầu đăng ký
        binding.registerBtn.setOnClickListener {
            /* Các bước:
            1 Nhập dữ liệu
            2 Kiểm tra dữ liệu
            3 Tạo tài khoản - Firebase Auth
            4 Lưu thông tin người dùng - Firebase Realtime Database
             */
            validateData()
        }
    }

    private var name = ""
    private var email = ""
    private var password = ""

    // Hàm kiểm tra dữ liệu
    private fun validateData() {
        // Nhập giá trị
        name = binding.nameEt.text.toString().trim()
        email = binding.emailEt.text.toString().trim()
        password = binding.passwordEt.text.toString().trim()
        val cPassword = binding.cPasswordEt.text.toString().trim()

        // Kiểm tra dữ liệu
        if (name.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên...", Toast.LENGTH_SHORT).show()
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show()
        } else if (password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
        } else if (cPassword.isEmpty()) {
            Toast.makeText(this, "Vui lòng xác nhận mật khẩu", Toast.LENGTH_SHORT).show()
        } else if (password != cPassword) {
            Toast.makeText(this, "Mật khẩu không khớp", Toast.LENGTH_SHORT).show()
        } else {
            createUserAccount() // Tạo tài khoản người dùng
        }
    }

    // Hàm tạo tài khoản người dùng
    private fun createUserAccount() {
        progressDialog.setMessage("Đang tạo tài khoản")
        progressDialog.show()

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Nếu tạo tài khoản thành công
                updateUserInfo() // Cập nhật thông tin người dùng
            }
            .addOnFailureListener { e ->
                // Nếu tạo tài khoản thất bại
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Tạo tài khoản thất bại do ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Hàm cập nhật thông tin người dùng
    private fun updateUserInfo() {
        progressDialog.setMessage("Đang lưu thông tin người dùng...")

        val timestamp = System.currentTimeMillis()
        val uid = firebaseAuth.uid

        // Thiết lập dữ liệu để lưu vào database
        val hashMap: HashMap<String, Any?> = HashMap()
        hashMap["uid"] = uid
        hashMap["email"] = email
        hashMap["name"] = name
        hashMap["profileImage"] = "" // Giá trị mặc định
        hashMap["userType"] = "user" // Giá trị mặc định
        hashMap["timestamp"] = timestamp

        // Lưu dữ liệu vào database
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(uid!!)
            .setValue(hashMap)
            .addOnSuccessListener {
                // Nếu lưu thành công
                progressDialog.dismiss()
                Toast.makeText(this, "Tài khoản đã được tạo", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(
                        this@RegisterActivity,
                        DashboardUserActivity::class.java
                    )
                ) // Mở DashboardUserActivity
                finish() // Kết thúc RegisterActivity
            }
            .addOnFailureListener { e ->
                // Nếu lưu thất bại
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Lưu thông tin người dùng thất bại do ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
