package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.DialogInterface
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

    // View Binding & Firebase
    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog

    // Biến cho bộ đếm thời gian xác thực
    private var verificationTimer: android.os.CountDownTimer? = null
    private var checkEmailDialog: android.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init Firebase & ProgressDialog
        firebaseAuth = FirebaseAuth.getInstance()
        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi")
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
    }

    private var email = ""
    private var password = ""

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
        progressDialog.setMessage("Đang đăng nhập...")
        progressDialog.show()

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // Lấy user hiện tại
                val user = firebaseAuth.currentUser

                // --- CHỐT CHẶN BẢO MẬT QUAN TRỌNG NHẤT ---
                if (user != null && user.isEmailVerified) {
                    // Nếu đã xác thực email -> Mới cho phép đi tiếp kiểm tra thiết bị
                    checkDeviceAndProcess()
                } else {
                    // Nếu chưa xác thực email -> CHẶN NGAY LẬP TỨC
                    progressDialog.dismiss()
                    firebaseAuth.signOut() // Đăng xuất ngay để không lưu session

                    // Hiển thị thông báo và cho phép gửi lại email
                    showUnverifiedAccountDialog()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Đăng nhập thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Hàm hiển thị thông báo khi user chưa xác thực (Kèm nút gửi lại mail)
    private fun showUnverifiedAccountDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Tài khoản chưa xác thực")
        builder.setMessage("Email này chưa được xác thực. Vui lòng kiểm tra hộp thư đến (hoặc Spam) và bấm vào link xác nhận để kích hoạt tài khoản.")
        builder.setCancelable(false)

        builder.setPositiveButton("Đã hiểu") { dialog, _ ->
            dialog.dismiss()
        }

        builder.setNeutralButton("Gửi lại Email") { _, _ ->
            resendVerificationEmail()
        }

        builder.show()
    }

    // Hàm gửi lại email xác thực (dành cho trường hợp link cũ hết hạn hoặc bị trôi)
    private fun resendVerificationEmail() {
        progressDialog.setMessage("Đang gửi lại email...")
        progressDialog.show()

        // Mẹo: Cần đăng nhập lại tạm thời để lấy object user, sau đó gửi mail rồi signout ngay
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = firebaseAuth.currentUser
                user?.sendEmailVerification()
                    ?.addOnSuccessListener {
                        progressDialog.dismiss()
                        firebaseAuth.signOut() // Gửi xong đá ra luôn
                        Toast.makeText(this, "Đã gửi lại email. Vui lòng kiểm tra hộp thư.", Toast.LENGTH_LONG).show()
                    }
                    ?.addOnFailureListener {
                        progressDialog.dismiss()
                        firebaseAuth.signOut()
                        Toast.makeText(this, "Lỗi gửi mail: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    // --- LOGIC BẢO MẬT THIẾT BỊ ---

    private fun checkDeviceAndProcess() {
        val user = firebaseAuth.currentUser ?: return
        val currentDeviceId = getAppUniqueId() // Lấy ID máy hiện tại

        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val serverDeviceId = snapshot.child("deviceId").value as? String

                if (serverDeviceId == null) {
                    // TH1: Chưa có deviceId -> Lưu và cho vào
                    userRef.child("deviceId").setValue(currentDeviceId)
                    checkUser()
                } else if (serverDeviceId == currentDeviceId) {
                    // TH2: Khớp thiết bị -> Cho vào
                    checkUser()
                } else {
                    // TH3: Thiết bị lạ -> Cảnh báo
                    progressDialog.dismiss()
                    showNewDeviceAlert(currentDeviceId)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                progressDialog.dismiss()
                Toast.makeText(this@LoginActivity, "Lỗi Server: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Hàm lấy ID thiết bị (tránh trùng tên với hàm hệ thống Android 14)
    private fun getAppUniqueId(): String {
        val prefs = getSharedPreferences("app_security_prefs", MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)

        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId!!
    }

    // Hiển thị Dialog cảnh báo thiết bị mới
    private fun showNewDeviceAlert(newDeviceId: String) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Phát hiện thiết bị mới")
        builder.setMessage("Tài khoản đang đăng nhập trên thiết bị khác. Bạn có muốn chuyển sang thiết bị này không?\n(Cần xác thực Email)")
        builder.setCancelable(false)

        builder.setPositiveButton("Gửi Email xác thực") { dialog: DialogInterface, which: Int ->
            sendVerificationEmail(newDeviceId)
        }
        builder.setNegativeButton("Hủy bỏ") { dialog: DialogInterface, which: Int ->
            firebaseAuth.signOut()
        }
        builder.show()
    }

    // Gửi email và kích hoạt Timer
    private fun sendVerificationEmail(newDeviceId: String) {
        progressDialog.setMessage("Đang gửi email xác thực...")
        progressDialog.show()

        val user = firebaseAuth.currentUser
        user?.sendEmailVerification()?.addOnSuccessListener {
            progressDialog.dismiss()
            // Gửi xong -> Bắt đầu đếm ngược ngay lập tức
            startVerificationTimer(newDeviceId)

        }?.addOnFailureListener { e ->
            progressDialog.dismiss()
            Toast.makeText(this, "Lỗi gửi mail: ${e.message}", Toast.LENGTH_SHORT).show()
            firebaseAuth.signOut()
        }
    }

    // --- LOGIC TIMER TỰ ĐỘNG KIỂM TRA ---

    private fun startVerificationTimer(newDeviceId: String) {
        // 1. Tạo Dialog hiển thị thời gian
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Đang chờ xác thực...")
        builder.setMessage("Đã gửi email. Vui lòng kiểm tra hộp thư và bấm xác nhận.\n\nThời gian còn lại: 120s")
        builder.setCancelable(false)

        // Nút Hủy nếu người dùng không muốn đợi nữa
        builder.setNegativeButton("Hủy bỏ") { _, _ ->
            stopVerificationTimer()
            firebaseAuth.signOut()
        }

        checkEmailDialog = builder.create()
        checkEmailDialog?.show()

        // 2. Chạy Timer: 120 giây, mỗi 2 giây kiểm tra 1 lần
        verificationTimer = object : android.os.CountDownTimer(120000, 2000) {

            override fun onTick(millisUntilFinished: Long) {
                // Update text thông báo
                checkEmailDialog?.setMessage("Đã gửi email. Vui lòng bấm link xác nhận trong hộp thư.\n\nThời gian còn lại: ${millisUntilFinished / 1000}s")

                // Tự động kiểm tra trạng thái Email
                val user = firebaseAuth.currentUser
                user?.reload()?.addOnSuccessListener {
                    if (user.isEmailVerified) {
                        // Đã xác thực -> Dừng timer -> Vào App
                        stopVerificationTimer()
                        checkEmailDialog?.dismiss()
                        updateDeviceIdAndLogin(newDeviceId)
                    }
                }
            }

            override fun onFinish() {
                // Hết giờ
                checkEmailDialog?.dismiss()
                android.app.AlertDialog.Builder(this@LoginActivity)
                    .setTitle("Hết thời gian")
                    .setMessage("Đã quá thời gian xác thực. Vui lòng đăng nhập lại.")
                    .setPositiveButton("Đóng") { _, _ ->
                        firebaseAuth.signOut()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
        verificationTimer?.start()
    }

    private fun stopVerificationTimer() {
        verificationTimer?.cancel()
        verificationTimer = null
    }

    // Cập nhật DeviceID mới và chuyển màn hình
    private fun updateDeviceIdAndLogin(newDeviceId: String) {
        val uid = firebaseAuth.uid!!
        progressDialog.setMessage("Đang hoàn tất thiết lập...")
        progressDialog.show()

        FirebaseDatabase.getInstance().getReference("Users").child(uid)
            .child("deviceId").setValue(newDeviceId)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Xác thực thành công!", Toast.LENGTH_SHORT).show()
                checkUser()
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Lỗi cập nhật thiết bị: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- ĐIỀU HƯỚNG NGƯỜI DÙNG ---

    private fun checkUser() {
        // Đôi khi dialog chưa kịp tắt ở bước trước
        if(progressDialog.isShowing) progressDialog.dismiss()
        progressDialog.setMessage("Đang vào ứng dụng...")
        progressDialog.show()

        val firebaseUser = firebaseAuth.currentUser!!
        val ref = FirebaseDatabase.getInstance().getReference("Users")

        ref.child(firebaseUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    progressDialog.dismiss()
                    val userType = snapshot.child("userType").value

                    if (userType == "user") {
                        startActivity(Intent(this@LoginActivity, DashboardUserActivity::class.java))
                        finish()
                    } else if (userType == "admin") {
                        startActivity(Intent(this@LoginActivity, DashboardAdminActivity::class.java))
                        finish()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressDialog.dismiss()
                    Toast.makeText(this@LoginActivity, "Lỗi lấy dữ liệu user", Toast.LENGTH_SHORT).show()
                }
            })
    }
}