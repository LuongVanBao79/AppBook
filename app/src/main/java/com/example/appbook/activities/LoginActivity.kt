package com.example.appbook.activities

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.appbook.databinding.ActivityLoginBinding
import com.example.appbook.utils.SecurityUtils
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    // View Binding & Firebase
    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private var email = ""
    private var password = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init Firebase & ProgressDialog
        firebaseAuth = FirebaseAuth.getInstance()
        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi")
            setMessage("Đang đăng nhập...")
            setCanceledOnTouchOutside(false)
        }

        // Chuyển màn hình Đăng ký
        binding.noAccountTv.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Click Đăng nhập
        binding.loginBtn.setOnClickListener {
            validateData()
        }

        // Quên mật khẩu
        binding.forgotTv.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // --- CẤU HÌNH VÂN TAY ---
        // Gọi hàm setupBiometric (đã được dời ra ngoài onCreate)
        setupBiometric()
    }

    // --- CÁC HÀM XỬ LÝ VÂN TAY (Sửa lỗi Unresolved reference) ---

    // 1. Cấu hình bảng quét vân tay
    private fun setupBiometric() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // QUÉT THÀNH CÔNG -> Lấy mật khẩu lưu trữ ra và đăng nhập
                    val creds = getBiometricCredentials()
                    if (creds != null) {
                        // Tự điền email/pass vào ô nhập liệu
                        email = creds.first
                        password = creds.second

                        // Gọi hàm đăng nhập
                        loginUser()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Không hiện thông báo nếu user bấm Hủy (để đỡ phiền)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(this@LoginActivity, "Lỗi: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Đăng nhập nhanh")
            .setSubtitle("Sử dụng vân tay hoặc khuôn mặt")
            .setNegativeButtonText("Dùng mật khẩu")
            .build()

        // Kiểm tra xem user đã bật tính năng này chưa để hiện nút bấm và tự động quét
        val prefs = SecurityUtils.getBiometricPrefs(this)
        if (prefs.getBoolean("IS_BIO_ENABLED", false)) {
            binding.biometricBtn.visibility = View.VISIBLE // Hiện nút vân tay

            // Tự động bật bảng quét ngay khi mở App
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                // Có thể lỗi nếu phần cứng chưa sẵn sàng, bỏ qua
            }
        }

        // Sự kiện bấm nút vân tay
        binding.biometricBtn.setOnClickListener {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    // 2. Hàm lưu thông tin đăng nhập vào EncryptedSharedPreferences (BỊ THIẾU TRƯỚC ĐÓ)
    private fun saveBiometricLogin(email: String, pass: String) {
        val prefs = SecurityUtils.getBiometricPrefs(this)
        prefs.edit()
            .putString("BIO_EMAIL", email)
            .putString("BIO_PASS", pass)
            .putBoolean("IS_BIO_ENABLED", true) // Đánh dấu là đã bật
            .apply()
    }

    // 3. Hàm lấy thông tin để tự động đăng nhập (BỊ THIẾU TRƯỚC ĐÓ)
    private fun getBiometricCredentials(): Pair<String, String>? {
        val prefs = SecurityUtils.getBiometricPrefs(this)
        val isEnabled = prefs.getBoolean("IS_BIO_ENABLED", false)
        val email = prefs.getString("BIO_EMAIL", null)
        val pass = prefs.getString("BIO_PASS", null)

        return if (isEnabled && email != null && pass != null) {
            Pair(email, pass)
        } else {
            null
        }
    }

    // --- LOGIC ĐĂNG NHẬP CHÍNH ---

    private fun validateData() {
        email = binding.emailEt.text.toString().trim()
        password = binding.passwordEt.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Định dạng email không hợp lệ", Toast.LENGTH_SHORT).show()
        } else if (password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show()
        } else {
            loginUser()
        }
    }

    private fun loginUser() {
        progressDialog.show()

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = firebaseAuth.currentUser

                if (user != null && user.isEmailVerified) {
                    progressDialog.dismiss()

                    // Đăng nhập thành công -> Kiểm tra xem đã bật vân tay chưa
                    checkAndAskForBiometric(email, password)
                } else {
                    progressDialog.dismiss()
                    firebaseAuth.signOut()
                    showUnverifiedAccountDialog()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                val errorMessage = when (e) {
                    is FirebaseTooManyRequestsException -> "Tài khoản bị tạm khóa do nhập sai quá nhiều lần."
                    is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không chính xác."
                    is FirebaseAuthInvalidUserException -> "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa."
                    is FirebaseNetworkException -> "Lỗi kết nối mạng."
                    else -> "Đăng nhập thất bại: ${e.message}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    // Hàm hỏi người dùng sau khi đăng nhập thành công
    private fun checkAndAskForBiometric(email: String, pass: String) {
        val prefs = SecurityUtils.getBiometricPrefs(this)
        val isAlreadyEnabled = prefs.getBoolean("IS_BIO_ENABLED", false)

        // Nếu đã bật rồi -> Chuyển màn hình luôn
        if (isAlreadyEnabled) {
            // Cập nhật lại mật khẩu mới nhất (phòng trường hợp user đổi pass)
            saveBiometricLogin(email, pass)
            goToDashboard()
            return
        }

        // Nếu chưa bật -> Hiện Dialog hỏi
        AlertDialog.Builder(this)
            .setTitle("Cài đặt đăng nhập nhanh")
            .setMessage("Bạn có muốn sử dụng Vân tay/Khuôn mặt cho lần đăng nhập sau không?")
            .setPositiveButton("Đồng ý") { _, _ ->
                // Người dùng đồng ý -> Lưu mật khẩu mã hóa
                saveBiometricLogin(email, pass)
                Toast.makeText(this, "Đã bật đăng nhập sinh trắc học!", Toast.LENGTH_SHORT).show()
                goToDashboard()
            }
            .setNegativeButton("Không") { dialog, _ ->
                dialog.dismiss()
                goToDashboard()
            }
            .setCancelable(false)
            .show()
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardUserActivity::class.java))
        finish()
    }

    // --- CÁC HÀM HỖ TRỢ KHÁC (Xác thực mail) ---
    private fun showUnverifiedAccountDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Tài khoản chưa xác thực")
        builder.setMessage("Vui lòng kiểm tra email để kích hoạt tài khoản.")
        builder.setPositiveButton("Đã hiểu") { dialog, _ -> dialog.dismiss() }
        builder.setNeutralButton("Gửi lại Email") { _, _ -> resendVerificationEmail() }
        builder.show()
    }

    private fun resendVerificationEmail() {
        progressDialog.setMessage("Đang gửi lại email...")
        progressDialog.show()
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                it.user?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        progressDialog.dismiss()
                        firebaseAuth.signOut()
                        Toast.makeText(this, "Đã gửi lại email.", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Không thể gửi mail.", Toast.LENGTH_SHORT).show()
            }
    }
}