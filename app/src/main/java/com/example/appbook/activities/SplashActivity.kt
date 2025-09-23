package com.example.appbook.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SplashActivity : AppCompatActivity() {

    // Firebase Authentication
    private lateinit var firebaseAuth: FirebaseAuth

    // Thời gian hiển thị màn hình splash (2 giây)
    private val SPLASH_DELAY: Long = 2000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Khởi tạo Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        // Sau 2 giây sẽ kiểm tra trạng thái đăng nhập
        Handler().postDelayed({ checkUser() }, SPLASH_DELAY)
    }

    /**
     * Kiểm tra trạng thái người dùng:
     * - Chưa đăng nhập: chuyển đến MainActivity
     * - Đã đăng nhập: kiểm tra loại tài khoản (user/admin) và chuyển đến màn hình tương ứng
     */
    private fun checkUser() {
        firebaseAuth.currentUser?.let { user ->
            // Người dùng đã đăng nhập, kiểm tra loại tài khoản
            checkUserType(user.uid)
        } ?: run {
            // Người dùng chưa đăng nhập, chuyển đến màn hình chính
            navigateToActivity(MainActivity::class.java)
        }
    }

    /**
     * Kiểm tra loại tài khoản (user/admin) từ Firebase Database
     * @param uid ID của người dùng
     */
    private fun checkUserType(uid: String) {
        FirebaseDatabase.getInstance().getReference("Users")
            .child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    when (snapshot.child("userType").value) {
                        "user" -> navigateToActivity(DashboardUserActivity::class.java)
                        "admin" -> navigateToActivity(DashboardAdminActivity::class.java)
                        else -> navigateToActivity(MainActivity::class.java) // Trường hợp không xác định
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Nếu có lỗi, chuyển về màn hình chính
                    navigateToActivity(MainActivity::class.java)
                }
            })
    }

    /**
     * Chuyển đến Activity được chỉ định và kết thúc SplashActivity
     * @param activityClass Class của Activity đích
     */
    private fun <T : AppCompatActivity> navigateToActivity(activityClass: Class<T>) {
        startActivity(Intent(this, activityClass))
        finish()
    }
}