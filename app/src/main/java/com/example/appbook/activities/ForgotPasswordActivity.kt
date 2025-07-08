package com.example.appbook.activities

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityForgotPasswordBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        setupClickListeners()
    }

    /**
     * Gán các sự kiện click cho nút
     */
    private fun setupClickListeners() {
        binding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.submitBtn.setOnClickListener {
            validateEmailInput()
        }
    }

    /**
     * Kiểm tra định dạng email trước khi gửi yêu cầu
     */
    private fun validateEmailInput() {
        email = binding.emailEt.text.toString().trim()

        when {
            email.isEmpty() -> {
                showMessage("Vui lòng nhập địa chỉ email.")
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showMessage("Địa chỉ email không hợp lệ.")
            }

            else -> {
                sendPasswordResetEmail()
            }
        }
    }

    /**
     * Gửi email đặt lại mật khẩu qua Firebase
     */
    private fun sendPasswordResetEmail() {
        // Hiển thị thông báo tạm thời
        showMessage("Đang gửi hướng dẫn đến $email...")

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                showMessage("Hướng dẫn đã được gửi đến:\n$email")
            }
            .addOnFailureListener { e ->
                showMessage("Gửi thất bại: ${e.message}")
            }
    }

    /**
     * Hiển thị thông báo cho người dùng bằng Snackbar
     */
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
