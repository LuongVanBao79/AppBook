package com.example.appbook.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Xử lý sự kiện click vào nút "Đăng nhập"
        binding.loginBtn.setOnClickListener {
            // Mở màn hình đăng nhập (LoginActivity)
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Xử lý sự kiện click vào nút "Bỏ qua" và tiếp tục đến màn hình chính
        binding.skipBtn.setOnClickListener {
            // Mở màn hình Dashboard dành cho người dùng (DashboardUserActivity)
            startActivity(Intent(this, DashboardUserActivity::class.java))
        }

        // Bây giờ, hãy kết nối với Firebase
        // (Phần này có thể sẽ được triển khai ở các Activity khác,
        //  hoặc có thể thêm code kết nối Firebase ở đây nếu cần)

    }
}
