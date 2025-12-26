package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityRegisterBinding
import com.example.appbook.utils.EncryptionHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.nulabinc.zxcvbn.Zxcvbn
import com.nulabinc.zxcvbn.Strength


class RegisterActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityRegisterBinding

    // Firebase Authentication
    private lateinit var firebaseAuth: FirebaseAuth

    // Progress dialog để hiển thị thông báo trong quá trình xử lý
    private lateinit var progressDialog: ProgressDialog

    // Khởi tạo thư viện chấm điểm mật khẩu
    private val zxcvbn = Zxcvbn()

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

        // Thêm TextWatcher để kiểm tra độ mạnh mật khẩu khi người dùng gõ
        binding.passwordEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Không cần làm gì
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkPasswordStrength(s.toString()) // Gọi hàm kiểm tra
            }

            override fun afterTextChanged(s: Editable?) {
                // Không cần làm gì
            }
        })


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
    private var passwordScore = 0 // Biến lưu điểm mạnh mật khẩu

    /**
     * Hàm kiểm tra độ mạnh mật khẩu và cập nhật giao diện
     * Điểm của thư viện zxcvbn: 0 (rất yếu) đến 4 (rất mạnh)
     */
    private fun checkPasswordStrength(password: String) {
        if (password.isEmpty()) {
            binding.passwordStrengthTv.text = "Độ mạnh: Chưa nhập"
            binding.passwordStrengthTv.setTextColor(getColor(com.google.android.material.R.color.material_on_surface_emphasis_medium)) // Màu xám
            passwordScore = 0
            return
        }

        // Thực hiện kiểm tra độ mạnh
        val result = zxcvbn.measure(password)
        passwordScore = result.score // Lưu điểm

        val (text, colorRes) = when (result.score) {
            0 -> Pair("Độ mạnh: Rất yếu", android.graphics.Color.RED)
            1 -> Pair("Độ mạnh: Yếu", android.graphics.Color.parseColor("#FFA500")) // Màu Cam
            2 -> Pair("Độ mạnh: Trung bình", android.graphics.Color.YELLOW)
            3 -> Pair("Độ mạnh: Mạnh", android.graphics.Color.parseColor("#008000")) // Màu Xanh lá
            4 -> Pair("Độ mạnh: Rất mạnh", android.graphics.Color.parseColor("#006400")) // Màu Xanh lá đậm
            else -> Pair("Độ mạnh: Không xác định", com.google.android.material.R.color.material_on_surface_emphasis_medium)
        }

        binding.passwordStrengthTv.text = text
        binding.passwordStrengthTv.setTextColor(colorRes)

    }

    // Hàm kiểm tra dữ liệu
    private fun validateData() {
        // Nhập giá trị
        name = binding.nameEt.text.toString().trim()
        email = binding.emailEt.text.toString().trim()
        password = binding.passwordEt.text.toString().trim()
        val cPassword = binding.cPasswordEt.text.toString().trim()

        val htmlPattern = Regex("<(\"[^\"]*\"|'[^']*'|[^'\">])*>")

        // Kiểm tra dữ liệu
        if (name.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên...", Toast.LENGTH_SHORT).show()
        } else if (htmlPattern.containsMatchIn(name)) {
            Toast.makeText(
                this,
                "Tên không được chứa ký tự đặc biệt hoặc thẻ HTML!",
                Toast.LENGTH_SHORT
            ).show()
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Email không hợp lệ", Toast.LENGTH_SHORT).show()
        } else if (password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
        } else if (passwordScore < 2) { // Thêm kiểm tra điểm mạnh (ví dụ: yêu cầu điểm từ 2 trở lên)
            Toast.makeText(this, "Mật khẩu quá yếu (Điểm ${passwordScore}). Vui lòng chọn mật khẩu mạnh hơn.", Toast.LENGTH_LONG).show()
        } else if (cPassword.isEmpty()) {
            Toast.makeText(this, "Vui lòng xác nhận mật khẩu", Toast.LENGTH_SHORT).show()
        } else if (password != cPassword) {
            Toast.makeText(this, "Mật khẩu không khớp", Toast.LENGTH_SHORT).show()
        } else {
            createUserAccount() // Tạo tài khoản người dùng
        }
    }

    // Hàm tạo tài khoản người dùng
//    private fun createUserAccount() {
//        progressDialog.setMessage("Đang tạo tài khoản")
//        progressDialog.show()
//        // ... (giữ nguyên phần còn lại của createUserAccount và updateUserInfo)
//        firebaseAuth.createUserWithEmailAndPassword(email, password)
//            .addOnSuccessListener {
//                // Nếu tạo tài khoản thành công
//                updateUserInfo() // Cập nhật thông tin người dùng
//            }
//            .addOnFailureListener { e ->
//                // Nếu tạo tài khoản thất bại
//                progressDialog.dismiss()
//                Toast.makeText(
//                    this,
//                    "Tạo tài khoản thất bại do ${e.message}",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//    }

    // Hàm cập nhật thông tin người dùng
//    private fun updateUserInfo() {
//        progressDialog.setMessage("Đang lưu thông tin người dùng...")
//
//        val timestamp = System.currentTimeMillis()
//        val uid = firebaseAuth.uid
//
//        // Thiết lập dữ liệu để lưu vào database
//        val hashMap: HashMap<String, Any?> = HashMap()
//        hashMap["uid"] = uid
//        hashMap["email"] = EncryptionHelper.encrypt(email)
//        hashMap["name"] = name
//        hashMap["profileImage"] = "" // Giá trị mặc định
//        hashMap["userType"] = "user" // Giá trị mặc định
//        hashMap["timestamp"] = timestamp
//
//        // Lưu dữ liệu vào database
//        val ref = FirebaseDatabase.getInstance().getReference("Users")
//        ref.child(uid!!)
//            .setValue(hashMap)
//            .addOnSuccessListener {
//                // Nếu lưu thành công
//                progressDialog.dismiss()
//                Toast.makeText(this, "Tài khoản đã được tạo", Toast.LENGTH_SHORT).show()
//                startActivity(
//                    Intent(
//                        this@RegisterActivity,
//                        DashboardUserActivity::class.java
//                    )
//                ) // Mở DashboardUserActivity
//                finish() // Kết thúc RegisterActivity
//            }
//            .addOnFailureListener { e ->
//                // Nếu lưu thất bại
//                progressDialog.dismiss()
//                Toast.makeText(
//                    this,
//                    "Lưu thông tin người dùng thất bại do ${e.message}",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//    }

    // 1. Hàm tạo tài khoản (Giữ nguyên logic gọi hàm update)
    private fun createUserAccount() {
        progressDialog.setMessage("Đang tạo tài khoản...")
        progressDialog.show()

        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Tạo Auth thành công -> Lưu thông tin vào Database
                updateUserInfo()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Tạo tài khoản thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 2. Hàm lưu thông tin (Sửa lại: Không vào Dashboard ngay, mà gọi gửi Email)
    private fun updateUserInfo() {
        progressDialog.setMessage("Đang lưu thông tin...")

        val timestamp = System.currentTimeMillis()
        val uid = firebaseAuth.uid

        val hashMap: HashMap<String, Any?> = HashMap()
        hashMap["uid"] = uid
        hashMap["email"] = EncryptionHelper.encrypt(email) // Mã hóa email nếu cần
        hashMap["name"] = name
        hashMap["profileImage"] = ""
        hashMap["userType"] = "user"
        hashMap["timestamp"] = timestamp

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(uid!!)
            .setValue(hashMap)
            .addOnSuccessListener {
                // Lưu DB thành công -> Gửi email xác thực
                sendEmailVerification()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Lỗi lưu dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 3. Hàm mới: Gửi email xác thực và Đăng xuất
    private fun sendEmailVerification() {
        progressDialog.setMessage("Đang gửi email xác thực...")
        val user = firebaseAuth.currentUser

        user?.sendEmailVerification()
            ?.addOnSuccessListener {
                progressDialog.dismiss()

                // Quan trọng: Đăng xuất ngay lập tức để user không vào được App
                firebaseAuth.signOut()

                // Hiển thị thông báo hướng dẫn
                showVerificationDialog()
            }
            ?.addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Không gửi được email xác thực: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 4. Hiển thị Dialog thông báo và quay về Login
    private fun showVerificationDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Đăng ký thành công")
        builder.setMessage("Chúng tôi đã gửi một email xác thực đến $email.\n\nVui lòng kiểm tra hộp thư đến (và cả mục Spam) để xác thực tài khoản trước khi đăng nhập.")
        builder.setCancelable(false) // Không cho bấm ra ngoài

        builder.setPositiveButton("Về trang Đăng nhập") { _, _ ->
            // Quay lại màn hình Login (hoặc finish để quay lại màn hình trước đó)
            finish()
        }
        builder.show()
    }
}