package com.example.appbook.activities

import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityPdfViewBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.math.roundToInt

class PdfViewActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityPdfViewBinding

    private companion object {
        private const val TAG = "PDF_VIEW_TAG"
    }

    // Book ID nhận từ Intent
    private var bookId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy bookId từ Intent, nếu không có thì gán giá trị mặc định là ""
        bookId = intent.getStringExtra("bookId") ?: ""
        Log.d(TAG, "Received bookId: $bookId")

        // Tải thông tin chi tiết của sách (PDF URL)
        loadBookDetails()


        // Xử lý sự kiện click vào nút "Quay lại"
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        // ẩn thanh hiển thị của điện thoại
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private var totalPages = 0
    private var currentPage = 0
    private val averageReadingTimePerPage = 1.5 // phút/trang

    // Hàm tải thông tin chi tiết của sách (PDF URL) từ Firebase
    /*private fun loadBookDetails() {
        Log.d(TAG, "loadBookDetails: Getting PDF URL from Firebase DB")
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy PDF URL từ snapshot
                    val pdfUrl = snapshot.child("url").value as? String
                    if (!pdfUrl.isNullOrEmpty()) {
                        // Nếu PDF URL không rỗng, tải PDF từ URL
                        Log.d(TAG, "PDF URL: $pdfUrl")
                        loadBookFromCloudinaryUrl(pdfUrl)
                    } else {
                        // Nếu PDF URL rỗng, log lỗi và ẩn progress bar
                        Log.e(TAG, "PDF URL is empty or null")
                        Log.d(TAG, "Received bookId: $bookId")
                        binding.progressBar.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý lỗi nếu có
                    Log.e(TAG, "loadBookDetails: ${error.message}")
                    binding.progressBar.visibility = View.GONE
                }
            })
    }*/

    private fun loadBookDetails() {
        Log.d(TAG, "loadBookDetails: Getting PDF URL and Title from Firebase DB")
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val pdfUrl = snapshot.child("url").value as? String
                    val title = snapshot.child("title").value as? String

                    binding.toolbarTitleTv.text = title ?: "Không có tiêu đề"

                    if (!pdfUrl.isNullOrEmpty()) {
                        Log.d(TAG, "PDF URL: $pdfUrl")
                        loadBookFromCloudinaryUrl(pdfUrl)
                    } else {
                        Log.e(TAG, "PDF URL is empty or null")
                        binding.progressBar.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "loadBookDetails: ${error.message}")
                    binding.progressBar.visibility = View.GONE
                }
            })
    }



    // Hàm tải PDF từ Cloudinary URL sử dụng OkHttp
    private fun loadBookFromCloudinaryUrl(pdfUrl: String) {
        Log.d(TAG, "Downloading PDF from Cloudinary URL")

        val client = OkHttpClient()
        val request = Request.Builder().url(pdfUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to load PDF: ${e.message}")
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val pdfBytes = response.body?.bytes()
                    if (pdfBytes != null) {
                        runOnUiThread {
                            binding.pdfView.fromBytes(pdfBytes)
                                .defaultPage(0)
                                .enableAnnotationRendering(true)
                                .swipeHorizontal(false)
                                .onLoad { pageCount ->
                                    totalPages = pageCount
                                    currentPage = 1
                                    updateReadingStatus()
                                    binding.progressBar.visibility = View.GONE
                                }
                                .onPageChange { page, pageCount ->
                                    totalPages = pageCount
                                    currentPage = page + 1
                                    updateReadingStatus()
                                }
                                .onError { t ->
                                    Log.e(TAG, "PDF load error: ${t.message}")
                                    binding.progressBar.visibility = View.GONE
                                }
                                .onPageError { page, t ->
                                    Log.e(TAG, "Page error on page $page: ${t.message}")
                                    binding.progressBar.visibility = View.GONE
                                }
                                .load()
                        }
                    } else {
                        Log.e(TAG, "Empty PDF data")
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                        }
                    }
                } else {
                    Log.e(TAG, "Response error: ${response.code}")
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun updateReadingStatus() {
        binding.toolbarSubtitleTv1.text = "Trang $currentPage/$totalPages"

        val pagesLeft = totalPages - currentPage
        val estimatedMinutes = (pagesLeft * averageReadingTimePerPage).roundToInt()

        binding.toolbarSubtitleTv2.text = "~${estimatedMinutes} phút còn lại"
    }


}
