package com.example.appbook.activities

import android.Manifest
import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.utils.ObjectUtils
import com.example.appbook.R
import com.example.appbook.databinding.ActivityProfileEditBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private var imageUri: Uri? = null
    private lateinit var progressDialog: ProgressDialog
    private var name = ""

    // --- Permissions Launchers ---
    // Yêu cầu quyền Camera
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImageCamera()
        } else {
            Toast.makeText(this, "Cần cấp quyền Camera để chụp ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    // Yêu cầu quyền Thư viện (Storage)
    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImageGallery()
        } else {
            Toast.makeText(this, "Cần cấp quyền truy cập thư viện", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi")
            setCanceledOnTouchOutside(false)
        }

        firebaseAuth = FirebaseAuth.getInstance()
        loadUserInfo()

        binding.backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.profileIv.setOnClickListener { showImageAttachMenu() }
        // Hoặc bấm vào nút camera nhỏ (nếu bạn đã thêm theo UI mới)
        binding.cameraBtn.setOnClickListener { showImageAttachMenu() }

        binding.updateBtn.setOnClickListener { validateData() }
    }

    private fun validateData() {
        name = binding.nameEt.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên", Toast.LENGTH_SHORT).show()
        } else {
            if (imageUri == null) {
                updateProfile("")
            } else {
                uploadImage()
            }
        }
    }

    private fun uploadImage() {
        progressDialog.setMessage("Đang tải ảnh profile...")
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Kiểm tra null safety kỹ hơn
                val currentUri = imageUri ?: return@launch
                val inputStream = contentResolver.openInputStream(currentUri)

                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(this@ProfileEditActivity, "Lỗi đọc file ảnh", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val url = inputStream.use { stream ->
                    val cloudinary = MediaManager.get().cloudinary
                    val uploadResult = cloudinary.uploader().upload(
                        stream, ObjectUtils.asMap(
                            "folder", "ProfileImages/",
                            "public_id", firebaseAuth.uid,
                            "resource_type", "image",
                            "overwrite", true
                        )
                    )
                    uploadResult["secure_url"] as String
                }

                withContext(Dispatchers.Main) {
                    // Không dismiss dialog ở đây, để nó chạy tiếp sang updateProfile
                    updateProfile(url)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@ProfileEditActivity, "Lỗi upload: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateProfile(uploadedImageUrl: String) {
        progressDialog.setMessage("Đang cập nhật dữ liệu...")

        val hashmap: HashMap<String, Any> = HashMap()
        hashmap["name"] = name
        if (uploadedImageUrl.isNotEmpty()) {
            hashmap["profileImage"] = uploadedImageUrl
        }

        val reference = FirebaseDatabase.getInstance().getReference("Users")
        reference.child(firebaseAuth.uid!!)
            .updateChildren(hashmap)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show()
                finish() // Đóng activity quay về Profile
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserInfo() {
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        // SỬ DỤNG addListenerForSingleValueEvent THAY VÌ addValueEventListener
        // Để tránh việc dữ liệu bị load lại khi đang chỉnh sửa
        ref.child(firebaseAuth.uid!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("name").value.toString()
                    val profileImage = snapshot.child("profileImage").value.toString()

                    binding.nameEt.setText(name)

                    try {
                        Glide.with(this@ProfileEditActivity)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(binding.profileIv)
                    } catch (e: Exception) {
                        // Ignored
                    }
                }

                override fun onCancelled(error: DatabaseError) { }
            })
    }

    private fun showImageAttachMenu() {
        val popupMenu = PopupMenu(this, binding.profileIv) // Hoặc anchor vào view khác
        popupMenu.menu.add(Menu.NONE, 0, 0, "Chụp ảnh (Camera)")
        popupMenu.menu.add(Menu.NONE, 1, 1, "Chọn từ thư viện")
        popupMenu.show()

        popupMenu.setOnMenuItemClickListener { item ->
            val id = item.itemId
            if (id == 0) {
                checkCameraPermission()
            } else if (id == 1) {
                checkStoragePermission()
            }
            true
        }
    }

    // --- CHECK QUYỀN CAMERA ---
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            pickImageCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // --- CHECK QUYỀN THƯ VIỆN (Xử lý Android 13+) ---
    private fun checkStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImageGallery()
        } else {
            requestStoragePermission.launch(permission)
        }
    }

    private fun pickImageCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Profile Pic")
        values.put(MediaStore.Images.Media.DESCRIPTION, "Image description")
        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraActivityResultLauncher.launch(intent)
    }

    private fun pickImageGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }

    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            binding.profileIv.setImageURI(imageUri)
        } else {
            // Nếu người dùng hủy chụp ảnh, nên xóa cái Uri rỗng vừa tạo để tránh rác bộ nhớ
            // (Optional logic handling here)
            Toast.makeText(this, "Đã hủy chụp ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            imageUri = data!!.data
            binding.profileIv.setImageURI(imageUri)
        } else {
            Toast.makeText(this, "Đã hủy chọn ảnh", Toast.LENGTH_SHORT).show()
        }
    }
}