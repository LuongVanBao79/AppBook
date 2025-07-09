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
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.adapters.AdapterComment
import com.example.appbook.databinding.ActivityPdfDetailBinding
import com.example.appbook.databinding.DialogCommentAddBinding
import com.example.appbook.models.ModelComment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class PdfDetailActivity : AppCompatActivity() {

    //view binding
    private lateinit var binding: ActivityPdfDetailBinding

    private companion object {
        //TAG
        const val TAG = "BOOK_DETAILS_TAG"
    }

    //book id, get from intent
    private var bookId = ""

    //get from firebase
    private var bookTitle = ""
    private var bookUrl = ""

    //firebase auth
    private lateinit var firebaseAuth: FirebaseAuth

    //progress dialog
    private lateinit var progressDialog: ProgressDialog

    //arraylist to hold comments
    private lateinit var commentArrayList: ArrayList<ModelComment>
    //adapter to be set to recyclerview
    private lateinit var adapterComment: AdapterComment

    //will hold a boolean value false/true to indicate either is in current user's favorite list or not
    private var isInMyFavorite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //get book id from intent
        bookId = intent.getStringExtra("bookId")!!

        //init progress bar
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait...")
        progressDialog.setCanceledOnTouchOutside(false)

        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        if (firebaseAuth.currentUser != null) {
            //user is logged in, check if book is in fav or not
            checkIsFavorite()
        }

        MyApplication.Companion.incrementBookViewCount(bookId)
        loadBookDetails()
        showComments()

        //handle back button click, go back
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        //handle click, open pdf view activity
        binding.readBookBtn.setOnClickListener {
            val intent = Intent(this, PdfViewActivity::class.java)
            intent.putExtra("bookId", bookId)
            startActivity(intent)
        }

        //handle click, download book/pdf
        binding.downloadBookBtn.setOnClickListener {
            //first check storage permission
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "onCreate: STORAGE PERMISSION is already granted")
                downloadBook()
            } else {
                Log.d(TAG, "onCreate: STORAGE PERMISSION was not granted")
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        //handle click, add/remove favorite
        binding.favoriteBtn.setOnClickListener {
            //we can add only if user is logged in
            //1 check if user is logged in or not
            if (firebaseAuth.currentUser == null) {
                //user not logged in, cant do favorite functionality
                Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show()
            } else {
                //user is logged in, we can do favorite functionality
                if (isInMyFavorite) {
                    MyApplication.removeFromFavorite(this, bookId)
                } else {
                    addToFavorite()
                }
            }
        }

        //handle click, show add comment dialog
        binding.addCommentBtn.setOnClickListener {
            /*To add a comment, user must be logged in, if not just show a message you're not logged in*/
            if(firebaseAuth.currentUser == null){
                //user not logged in, dont allow adding comment
                Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show()
            }
            else{
                //user logged in, allow adding comment
                addCommentDialog()
            }
        }

        //fix lỗi cuộn
        binding.mainScrollView.post {
            binding.mainScrollView.fullScroll(View.FOCUS_UP)
        }
    }

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

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            //lets check if granted or not
            if (isGranted) {
                Log.d(TAG, "onCreate: STORAGE PERMISSION is granted")
                downloadBook()
            } else {
                Log.d(TAG, "onCreate: STORAGE PERMISSION is denied")
                Toast.makeText(this, "Không có quyền truy cập", Toast.LENGTH_SHORT).show()
            }
        }

    private fun downloadBook() {
        Log.d(TAG, "downloadBook: Đang tải sách")

        // Hiển thị hộp thoại tiến trình
        progressDialog.setMessage("Đang tải sách")
        progressDialog.show()

        // Tạo tên file PDF mới (thêm timestamp để tránh trùng)
        val fileName = "downloaded_${System.currentTimeMillis()}.pdf"

        // Tạo file trong thư mục Download riêng của app (bộ nhớ trong)
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        // Chạy quá trình tải trong luồng nền để tránh làm đơ giao diện
        Thread {
            try {
                Log.d(TAG, "Starting download from: $bookUrl to ${file.absolutePath}")

                // Mở kết nối đến URL
                val urlConnection = URL(bookUrl).openConnection() as HttpsURLConnection
                urlConnection.connectTimeout = 10000 // Timeout kết nối (10s)
                urlConnection.readTimeout = 10000 // Timeout đọc dữ liệu
                urlConnection.requestMethod = "GET" // Phương thức HTTP GET
                urlConnection.setRequestProperty("Accept", "application/pdf") // Định dạng mong muốn
                urlConnection.connect() // Bắt đầu kết nối

                // Kiểm tra phản hồi từ server
                val responseCode = urlConnection.responseCode
                if (responseCode != HttpsURLConnection.HTTP_OK) {
                    throw Exception("Server returned code: $responseCode")
                }

                // Chuẩn bị đọc từ URL và ghi ra file
                val inputStream = urlConnection.inputStream
                val outputStream = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var bytesRead: Int

                // Đọc từng đoạn dữ liệu (1KB mỗi lần) và ghi vào file
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                // Đóng luồng
                outputStream.flush()
                outputStream.close()
                inputStream.close()
                urlConnection.disconnect()

                // Sau khi tải xong, quay lại luồng chính để cập nhật giao diện
                runOnUiThread {
                    // Kiểm tra nếu file thực sự tồn tại và không rỗng
                    if (file.exists() && file.length() > 0) {
                        // Tạo URI an toàn để mở file bằng FileProvider
                        val fileUri = FileProvider.getUriForFile(
                            this@PdfDetailActivity,
                            "com.example.appbook.fileprovider", // Authorities phải trùng trong AndroidManifest
                            file
                        )

                        // Tạo intent để mở file PDF bằng app bên ngoài
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(fileUri, "application/pdf")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Cấp quyền đọc cho app khác

                        try {
                            // Mở file bằng ứng dụng đọc PDF
                            startActivity(intent)
                            Log.d(TAG, "downloadBook: Mở file thành công với URI: $fileUri")
                            Toast.makeText(this, "Tải và mở file thành công", Toast.LENGTH_SHORT).show()

                            // Cập nhật lượt tải (nếu có chức năng thống kê)
                            incrementDownloadCount()
                        } catch (e: Exception) {
                            // Nếu thiết bị không có app đọc PDF
                            Log.e(TAG, "downloadBook: Lỗi khi mở file: ${e.message}")
                            Toast.makeText(this, "Lỗi khi mở file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // File không tồn tại hoặc bị rỗng
                        throw Exception("File not created or empty, size: ${file.length()} bytes")
                    }

                    // Ẩn hộp thoại tiến trình sau khi hoàn thành
                    progressDialog.dismiss()
                }
            } catch (e: Exception) {
                // Nếu có lỗi trong quá trình tải, hiển thị lỗi trên giao diện
                runOnUiThread {
                    Log.e(TAG, "downloadBook: Lỗi khi tải: ${e.message}")
                    Toast.makeText(this, "Tải thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                    progressDialog.dismiss()
                }
            }
        }.start() // Bắt đầu chạy luồng tải
    }


    private fun incrementDownloadCount() {
        Log.d(TAG, "incrementDownloadCount: ")

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var downloadsCount = "${snapshot.child("downloadsCount").value}"
                    Log.d(TAG, "onDataChange: Current Downloads Count: $downloadsCount")

                    if (downloadsCount == "" || downloadsCount == "null") downloadsCount = "0"

                    val newDownloadCount = downloadsCount.toLong() + 1
                    Log.d(TAG, "onDataChange: New Downloads Count: $newDownloadCount")

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

    private fun loadBookDetails() {
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val categoryId = "${snapshot.child("categoryId").value}"
                    val description = "${snapshot.child("description").value}"
                    val downloadsCount = "${snapshot.child("downloadsCount").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    bookTitle = "${snapshot.child("title").value}"
                    val uid = "${snapshot.child("uid").value}"
                    bookUrl = "${snapshot.child("url").value}"
                    val viewsCount = "${snapshot.child("viewsCount").value}"

                    val date = MyApplication.Companion.formatTimeStamp(timestamp.toLong())

                    MyApplication.Companion.loadCategory(categoryId, binding.categoryTv)
                    MyApplication.Companion.loadPdfFromUrlSinglePage(bookUrl, bookTitle, binding.pdfView, binding.progressBar, binding.pagesTv)
                    MyApplication.Companion.loadPdfSizeFromCloudinary(bookUrl, binding.sizeTv)

                    binding.titleTv.text = bookTitle
                    binding.descriptionTv.text = description
                    binding.viewsTv.text = viewsCount
                    binding.downloadsTv.text = downloadsCount
                    binding.dateTv.text = date
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

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