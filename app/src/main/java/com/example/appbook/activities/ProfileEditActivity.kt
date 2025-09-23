package com.example.appbook.activities

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.utils.ObjectUtils
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.databinding.ActivityProfileEditBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.UUID

class ProfileEditActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityProfileEditBinding

    // Firebase Authentication
    private lateinit var firebaseAuth: FirebaseAuth

    // URI của ảnh (sẽ được chọn từ Camera hoặc Gallery)
    private var imageUri: Uri? = null

    // Progress dialog để hiển thị thông báo trong quá trình xử lý
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Thiết lập progress dialog
        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi")
            setCanceledOnTouchOutside(false)
        }

        // Khởi tạo Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance()

        // Tải thông tin người dùng
        loadUserInfo()

        // Xử lý sự kiện click, quay lại
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        // Xử lý sự kiện click, chọn ảnh từ Camera/Gallery
        binding.profileIv.setOnClickListener {
            showImageAttachMenu()
        }

        // Xử lý sự kiện click, bắt đầu cập nhật profile
        binding.updateBtn.setOnClickListener {
            validateData()
        }
    }

    private var name = ""

    // Hàm kiểm tra dữ liệu
    private fun validateData() {
        // Lấy dữ liệu
        name = binding.nameEt.text.toString().trim()

        // Kiểm tra dữ liệu
        if (name.isEmpty()) {
            // Nếu tên không được nhập
            Toast.makeText(this, "Vui lòng nhập tên", Toast.LENGTH_SHORT).show()
        } else {
            // Nếu tên đã được nhập
            if (imageUri == null) {
                // Nếu không có ảnh, cập nhật profile mà không cần ảnh
                updateProfile("")
            } else {
                // Nếu có ảnh, tải ảnh lên
                uploadImage()
            }
        }
    }

    // Hàm tải ảnh lên Cloudinary
    private fun uploadImage() {
        progressDialog.setMessage("Đang tải ảnh profile")
        progressDialog.show()

        val inputStream = contentResolver.openInputStream(imageUri!!)
        if (inputStream == null) {
            progressDialog.dismiss()
            Toast.makeText(this, "Không thể mở ảnh", Toast.LENGTH_SHORT).show()
            return
        }

        val requestId = UUID.randomUUID().toString() // Tạo ID duy nhất cho ảnh

        Thread {
            try {
                val cloudinary = MediaManager.get().cloudinary
                val uploadResult = cloudinary.uploader().upload(
                    inputStream, ObjectUtils.asMap(
                        "folder", "ProfileImages/",
                        "public_id", firebaseAuth.uid, // Sử dụng UID để ghi đè ảnh cũ
                        "resource_type", "image"
                    )
                )

                val uploadedImageUrl = uploadResult["secure_url"] as String

                runOnUiThread {
                    progressDialog.dismiss()
                    updateProfile(uploadedImageUrl)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this,
                        "Không thể tải ảnh lên do ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    // Hàm cập nhật profile lên Firebase
    private fun updateProfile(uploadedImageUrl: String) {
        progressDialog.setMessage("Đang cập nhật profile...")

        // Thiết lập thông tin để cập nhật lên database
        val hashmap: HashMap<String, Any> = HashMap()
        hashmap["name"] = name
        if (imageUri != null) {
            hashmap["profileImage"] = uploadedImageUrl
        }

        // Cập nhật lên database
        val reference = FirebaseDatabase.getInstance().getReference("Users")
        reference.child(firebaseAuth.uid!!)
            .updateChildren(hashmap)
            .addOnSuccessListener {
                // Nếu profile được cập nhật thành công
                progressDialog.dismiss()
                Toast.makeText(this, "Profile đã được cập nhật", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                // Nếu cập nhật thất bại
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Không thể cập nhật profile do ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Hàm tải thông tin người dùng
    private fun loadUserInfo() {
        // Tham chiếu đến node "Users" trong Firebase
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy thông tin người dùng
                    val name = snapshot.child("name").value.toString()
                    val profileImage = snapshot.child("profileImage").value.toString()

                    // Set dữ liệu lên view
                    binding.nameEt.setText(name)

                    // Tải ảnh
                    try {
                        Glide.with(this@ProfileEditActivity)
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

    // Hàm hiển thị menu chọn ảnh (Camera/Gallery)
    private fun showImageAttachMenu() {
        /* Hiển thị popup menu với các tùy chọn Camera, Gallery để chọn ảnh */

        // Thiết lập popup menu
        val popupMenu = PopupMenu(this, binding.profileIv)
        popupMenu.menu.add(Menu.NONE, 0, 0, "Camera")
        popupMenu.menu.add(Menu.NONE, 1, 1, "Gallery")
        popupMenu.show()

        // Xử lý sự kiện click vào item trong popup menu
        popupMenu.setOnMenuItemClickListener { item ->
            // Lấy ID của item được click
            val id = item.itemId
            if (id == 0) {
                // Camera được click
                pickImageCamera()
            } else if (id == 1) {
                // Gallery được click
                pickImageGallery()
            }
            true
        }
    }

    // Hàm chọn ảnh từ Camera
    private fun pickImageCamera() {
        // Intent để chọn ảnh từ Camera
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "Temp_Title")
        values.put(MediaStore.Images.Media.DESCRIPTION, "Temp_Description")

        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraActivityResultLauncher.launch(intent)
    }

    // Hàm chọn ảnh từ Gallery
    private fun pickImageGallery() {
        // Intent để chọn ảnh từ Gallery
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }

    // Xử lý kết quả trả về từ Camera Intent
    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            // Lấy URI của ảnh
            if (result.resultCode == Activity.RESULT_OK) {
                // Nếu kết quả là OK
                val data = result.data
                // Set ảnh lên imageview
                binding.profileIv.setImageURI(imageUri)
            } else {
                // Nếu bị hủy
                Toast.makeText(this, "Đã hủy", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // Xử lý kết quả trả về từ Gallery Intent
    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            // Lấy URI của ảnh
            if (result.resultCode == Activity.RESULT_OK) {
                // Nếu kết quả là OK
                val data = result.data
                imageUri = data!!.data
                // Set ảnh lên imageview
                binding.profileIv.setImageURI(imageUri)
            } else {
                // Nếu bị hủy
                Toast.makeText(this, "Đã hủy", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
