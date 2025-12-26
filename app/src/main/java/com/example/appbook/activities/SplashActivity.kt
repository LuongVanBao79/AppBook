package com.example.appbook.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.appbook.R
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth

    // Thời gian hiển thị màn hình splash (2 giây) - Đủ để chạy xong animation
    private val SPLASH_DELAY: Long = 2000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 1. Cấu hình hiển thị full màn hình (Edge-to-edge) cho đẹp
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 2. Bắt đầu chạy Animation (Hiệu ứng)
        startAnimations()

        // 3. Khởi tạo Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        // 4. Sau 2 giây sẽ kiểm tra trạng thái đăng nhập
        Handler(Looper.getMainLooper()).postDelayed({ checkUser() }, SPLASH_DELAY)
    }

    /**
     * Hàm xử lý hiệu ứng: Logo bay từ dưới lên và hiện dần ra
     */
    private fun startAnimations() {
        // Ánh xạ View từ XML (Đảm bảo file XML đã có id centerContent và loadingSpinner như bước trước)
        val centerContent = findViewById<View>(R.id.centerContent)
        val loadingSpinner = findViewById<View>(R.id.loadingSpinner)

        // Thiết lập trạng thái ban đầu (Ẩn và nằm thấp hơn vị trí gốc)
        centerContent.translationY = 100f
        centerContent.alpha = 0f

        loadingSpinner.alpha = 0f // Ẩn spinner lúc đầu

        // Chạy Animation cho Logo + Tên App
        centerContent.animate()
            .translationY(0f)       // Trượt về vị trí gốc
            .alpha(1f)              // Hiện rõ dần
            .setDuration(1500)      // Chạy trong 1.5 giây
            .setInterpolator(DecelerateInterpolator()) // Chậm dần đều cho mượt
            .start()

        // Chạy Animation cho Spinner (hiện lên sau 0.5s)
        loadingSpinner.animate()
            .alpha(1f)
            .setDuration(1000)
            .setStartDelay(500)
            .start()
    }

    /**
     * Kiểm tra trạng thái người dùng:
     * - Chưa đăng nhập: chuyển đến MainActivity (để đăng nhập/đăng ký)
     * - Đã đăng nhập: chuyển thẳng vào DashboardUserActivity
     */
    private fun checkUser() {
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser == null) {
            // Người dùng chưa đăng nhập
            navigateToActivity(MainActivity::class.java)
        } else {
            // Người dùng đã đăng nhập (Bất kể là admin hay user đều vào giao diện đọc sách)
            navigateToActivity(DashboardUserActivity::class.java)
        }
    }

    /**
     * Chuyển đến Activity được chỉ định và kết thúc SplashActivity
     * @param activityClass Class của Activity đích
     */
    private fun <T : AppCompatActivity> navigateToActivity(activityClass: Class<T>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        finish() // Đóng SplashActivity
    }
}