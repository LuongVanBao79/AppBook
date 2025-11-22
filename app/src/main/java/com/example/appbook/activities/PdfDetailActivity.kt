package com.example.appbook.activities

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.adapters.AdapterComment
import com.example.appbook.databinding.ActivityPdfDetailBinding
import com.example.appbook.databinding.DialogCommentAddBinding
import com.example.appbook.databinding.DialogPasswordPromptBinding
import com.example.appbook.models.ModelComment
import com.example.appbook.utils.FileEncryptionHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class PdfDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfDetailBinding

    private companion object {
        const val TAG = "BOOK_DETAILS_TAG"
    }

    private var bookId = ""
    private var bookTitle = ""
    private var bookUrl = ""
    private var bookFileSize: Long = 0L // <-- KHAI BÁO TRƯỜNG MỚI ĐỂ DÙNG TRONG formatFileSize
    private var shouldPromptDownloadPassword = false

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var progressDialog: ProgressDialog
    private lateinit var commentArrayList: ArrayList<ModelComment>
    private lateinit var adapterComment: AdapterComment
    private var isInMyFavorite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra("bookId")!!

        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Vui Lòng Đợi...")
        progressDialog.setCanceledOnTouchOutside(false)

        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser != null) {
            checkIsFavorite()
        }

        MyApplication.incrementBookViewCount(bookId)
        loadBookDetails()
        showComments()

        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        binding.readBookBtn.setOnClickListener {
            val intent = Intent(this, PdfViewActivity::class.java)
            intent.putExtra("bookId", bookId)
            startActivity(intent)
        }

        binding.downloadBookBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                promptDownloadPassword()
            } else {
                shouldPromptDownloadPassword = true
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        binding.favoriteBtn.setOnClickListener {
            if (firebaseAuth.currentUser == null) {
                Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show()
            } else {
                if (isInMyFavorite) {
                    MyApplication.removeFromFavorite(this, bookId)
                } else {
                    addToFavorite()
                }
            }
        }

        binding.addCommentBtn.setOnClickListener {
            if(firebaseAuth.currentUser == null){
                Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show()
            }
            else{
                addCommentDialog()
            }
        }

        // fix lỗi cuộn: đảm bảo cuộn lên đầu khi Activity khởi tạo
        binding.mainScrollView.post {
            binding.mainScrollView.scrollTo(0, 0)
        }
    }

    // [GIỮ NGUYÊN] showComments, addCommentDialog, addComment
    private fun showComments() {
        //init arraylist
        commentArrayList = ArrayList()

        //db path to load comments
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).child("Comments")
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //clear list
                    commentArrayList.clear()
                    for(ds in snapshot.children){
                        //get data s model, be carefull of spellings and data type
                        val model = ds.getValue(ModelComment::class.java)
                        //add to list
                        commentArrayList.add(model!!)
                    }
                    //setup adapter
                    adapterComment = AdapterComment(this@PdfDetailActivity, commentArrayList)

                    //set adapter to recyclerview
                    binding.commentsRv.adapter = adapterComment
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private var comment = ""

    private fun addCommentDialog() {
        //inflate/bind view for dialog dialog_comment_add.xml
        val commentAddBinding = DialogCommentAddBinding.inflate(LayoutInflater.from(this))

        //setup alert dialog
        val builder = AlertDialog.Builder(this, R.style.CustomDialog)
        builder.setView(commentAddBinding.root)

        //create and show alert dialog
        val alertDialog = builder.create()
        alertDialog.show()

        //handle click, dismiss dialog
        commentAddBinding.backBtn.setOnClickListener { alertDialog.dismiss() }

        //handle click, add comment
        commentAddBinding.submitBtn.setOnClickListener {
            //getdata
            comment = commentAddBinding.commentEt.text.toString().trim()
            //validate Data
            if(comment.isEmpty()){
                Toast.makeText(this, "Nhập bình luận...", Toast.LENGTH_SHORT).show()
            }
            else{
                alertDialog.dismiss()
                addComment()
            }
        }
    }

    private fun addComment() {
        // show progress
        progressDialog.setMessage("Đang thêm bình luận")
        progressDialog.show()

        //timestamp for comment id, comment timestamp etc
        val timestamp = "${System.currentTimeMillis()}"

        //setup data to add in db for comment
        val hashMap = HashMap<String, Any>()
        hashMap["id"] = "$timestamp"
        hashMap["bookId"] = "$bookId"
        hashMap["timestamp"] = "$timestamp"
        hashMap["comment"] = "$comment"
        hashMap["uid"] = "${firebaseAuth.uid}"

        //Db path to add data into it
        //book > bookId > Comments > commentId > commentData
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).child("Comments").child(timestamp)
            .setValue(hashMap)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Bình luận đã được thêm...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                progressDialog.dismiss()
                Toast.makeText(this, "Thêm bình luận thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // [GIỮ NGUYÊN] requestStoragePermissionLauncher

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "onCreate: STORAGE PERMISSION is granted")
                if (shouldPromptDownloadPassword) {
                    promptDownloadPassword()
                }
            } else {
                Log.d(TAG, "onCreate: STORAGE PERMISSION is denied")
                Toast.makeText(this, "Không có quyền truy cập", Toast.LENGTH_SHORT).show()
            }
            shouldPromptDownloadPassword = false
        }

    private fun promptDownloadPassword() {
        val dialogBinding = DialogPasswordPromptBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this, R.style.CustomDialog)
            .setTitle("Mật khẩu mã hóa")
            .setView(dialogBinding.root)
            .setNegativeButton("Hủy") { d, _ -> d.dismiss() }
            .setPositiveButton("Xác nhận", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val password = dialogBinding.passwordInput.text?.toString()?.trim().orEmpty()
                if (password.length < 4) {
                    dialogBinding.passwordLayout.error = "Mật khẩu phải có ít nhất 4 ký tự"
                } else {
                    dialogBinding.passwordLayout.error = null
                    dialog.dismiss()
                    downloadBook(password)
                }
            }
        }

        dialog.show()
    }

    private fun downloadBook(password: String) {
        Log.d(TAG, "downloadBook: Đang tải sách")

        progressDialog.setMessage("Đang tải sách")
        progressDialog.show()

        val fileName = "downloaded_${System.currentTimeMillis()}.pdf.enc"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        Thread {
            try {
                Log.d(TAG, "Starting download from: $bookUrl to ${file.absolutePath}")

                val urlConnection = URL(bookUrl).openConnection() as HttpsURLConnection
                urlConnection.connectTimeout = 10000
                urlConnection.readTimeout = 10000
                urlConnection.requestMethod = "GET"
                urlConnection.setRequestProperty("Accept", "application/pdf")
                urlConnection.connect()

                val responseCode = urlConnection.responseCode
                if (responseCode != HttpsURLConnection.HTTP_OK) {
                    throw Exception("Server returned code: $responseCode")
                }

                val inputStream = urlConnection.inputStream
                val byteStream = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    byteStream.write(buffer, 0, bytesRead)
                }

                val plainBytes = byteStream.toByteArray()
                byteStream.close()
                inputStream.close()
                urlConnection.disconnect()

                FileEncryptionHelper.encryptToFile(plainBytes, password.toCharArray(), file)

                runOnUiThread {
                    progressDialog.dismiss()
                    if (file.exists() && file.length() > 0) {
                        incrementDownloadCount()
                        showEncryptedDownloadDialog(file)
                    } else {
                        throw Exception("File not created or empty, size: ${file.length()} bytes")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e(TAG, "downloadBook: Lỗi khi tải: ${e.message}")
                    Toast.makeText(this, "Tải thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                    progressDialog.dismiss()
                }
            }
        }.start()
    }

    private fun showEncryptedDownloadDialog(encryptedFile: File) {
        AlertDialog.Builder(this, R.style.CustomDialog)
            .setTitle("Đã mã hóa file")
            .setMessage(
                "File đã được lưu với định dạng mã hóa:\n${encryptedFile.name}" +
                    "\n\nBạn có thể mở lại bất cứ lúc nào từ trình quản lý file (sẽ yêu cầu mật khẩu)."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // [GIỮ NGUYÊN] incrementDownloadCount
    private fun incrementDownloadCount() {
        Log.d(TAG, "incrementDownloadCount: ")

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var downloadsCount = "${snapshot.child("downloadsCount").value}"

                    if (downloadsCount == "" || downloadsCount == "null") downloadsCount = "0"

                    val newDownloadCount = downloadsCount.toLong() + 1

                    val hashMap = HashMap<String, Any>()
                    hashMap["downloadsCount"] = newDownloadCount

                    val dbRef = FirebaseDatabase.getInstance().getReference("Books")
                    dbRef.child(bookId)
                        .updateChildren(hashMap)
                        .addOnSuccessListener {
                            Log.d(TAG, "onDataChange: Downloads count incremented")
                        }
                        .addOnFailureListener { e ->
                            Log.d(TAG, "onDataChange: FAILED to increment due to ${e.message}")
                        }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * [THAY ĐỔI LỚN] Load details: Loại bỏ các hàm tải nặng (loadPdfFromUrlSinglePage, loadPdfSizeFromCloudinary)
     * và thay thế bằng việc hiển thị dữ liệu đã tính toán sẵn (imageUrl, fileSize, pagesCount).
     */
    private fun loadBookDetails() {
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy dữ liệu cũ
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val description = "${snapshot.child("description").value}"
                    val downloadsCount = "${snapshot.child("downloadsCount").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    bookTitle = "${snapshot.child("title").value}"
                    bookUrl = "${snapshot.child("url").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"

                    // Lấy dữ liệu mới đã được tính toán sẵn
                    val pagesCount = "${snapshot.child("pagesCount").value}"
                    val fileSizeString = "${snapshot.child("fileSize").value}"
                    val imageUrl = "${snapshot.child("imageUrl").value}"

                    // Chuyển đổi fileSize sang Long và lưu vào biến cục bộ
                    bookFileSize = fileSizeString.toLongOrNull() ?: 0L

                    val date = MyApplication.formatTimeStamp(timestamp.toLong())

                    // 1. Tải ảnh bìa (ImageView) thay vì PDFView
                    try {
                        // Tải ảnh bìa bằng Glide
                        Glide.with(this@PdfDetailActivity)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_book_white) // Đặt placeholder
                            .into(binding.coverIv) // Sử dụng ImageView mới
                    } catch (e: Exception) {
                        Log.e(TAG, "onDataChange: Error loading image", e)
                    }

                    // 2. Load Category (Giữ nguyên)
                    MyApplication.loadCategory(categoryId, binding.categoryTv)

                    // 3. Hiển thị dữ liệu
                    binding.titleTv.text = bookTitle
                    binding.descriptionTv.text = description
                    binding.viewsTv.text = viewsCount
                    binding.downloadsTv.text = downloadsCount
                    binding.dateTv.text = date

                    // Hiển thị các trường mới
                    binding.pagesTv.text = pagesCount

                    // Hiển thị kích thước file (Sử dụng hàm tiện ích formatFileSize từ Adapter)
                    binding.sizeTv.text = formatFileSize(bookFileSize)

                    // BỎ CÁC HÀM TẢI NẶNG (đã được thay thế):
                    // BỎ: MyApplication.Companion.loadPdfFromUrlSinglePage(bookUrl, bookTitle, binding.pdfView, binding.progressBar, binding.pagesTv)
                    // BỎ: MyApplication.Companion.loadPdfSizeFromCloudinary(bookUrl, binding.sizeTv)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Hàm tiện ích (copy từ Adapter) để định dạng kích thước file
     */
    private fun formatFileSize(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    // [GIỮ NGUYÊN] checkIsFavorite, addToFavorite
    private fun checkIsFavorite() {
        Log.d(TAG, "checkIsFavorite: Checking if book is in fav or not")

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    isInMyFavorite = snapshot.exists()
                    if (isInMyFavorite) {
                        Log.d(TAG, "onDataChange: available in favorite")
                        binding.favoriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0, R.drawable.ic_favorite_filled_white, 0, 0
                        )
                        binding.favoriteBtn.text = "Bỏ yêu thích"
                    } else {
                        Log.d(TAG, "onDataChange: not available in favorite")
                        binding.favoriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0, R.drawable.ic_favorite_white, 0, 0
                        )
                        binding.favoriteBtn.text = "Yêu thích"
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun addToFavorite() {
        Log.d(TAG, "addToFavorite: Adding to fav")
        val timestamp = System.currentTimeMillis()

        val hashMap = HashMap<String, Any>()
        hashMap["bookId"] = bookId
        hashMap["timeStamp"] = timestamp

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
            .setValue(hashMap)
            .addOnSuccessListener {
                Log.d(TAG, "addToFavorite: Added to fav")
                Toast.makeText(this, "Đã thêm vào mục yêu thích", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "addToFavorite: Failed to add to fav due to ${e.message}")
                Toast.makeText(this, "Thêm vào mục yêu thích thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}