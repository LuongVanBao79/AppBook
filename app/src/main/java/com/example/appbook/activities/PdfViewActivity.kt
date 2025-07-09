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



    // Hàm dùng để tải file PDF từ một URL (trên Cloudinary) và hiển thị bằng PDFView
    private fun loadBookFromCloudinaryUrl(pdfUrl: String) {
        Log.d(TAG, "Downloading PDF from Cloudinary URL") // Ghi log để debug

        // Khởi tạo OkHttpClient để gửi yêu cầu HTTP
        val client = OkHttpClient()

        // Tạo một yêu cầu HTTP GET đến đường dẫn PDF
        val request = Request.Builder().url(pdfUrl).build()

        // Gửi yêu cầu bất đồng bộ (không chặn giao diện)
        client.newCall(request).enqueue(object : Callback {

            // Gọi khi xảy ra lỗi mạng (không kết nối được server)
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to load PDF: ${e.message}") // Ghi log lỗi

                // Cập nhật giao diện từ luồng chính (UI thread)
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE // Ẩn thanh tải
                }
            }

            // Gọi khi server phản hồi thành công
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Lấy dữ liệu PDF dưới dạng mảng byte
                    val pdfBytes = response.body?.bytes()

                    // Nếu dữ liệu không null thì tiến hành hiển thị PDF
                    if (pdfBytes != null) {
                        runOnUiThread {
                            // Hiển thị PDF từ mảng byte bằng PDFView
                            binding.pdfView.fromBytes(pdfBytes)
                                .defaultPage(0) // Mở từ trang đầu tiên
                                .enableAnnotationRendering(true) // Hiển thị ghi chú (nếu có)
                                .swipeHorizontal(false) // Cuộn dọc (false = dọc, true = ngang)

                                // Khi load PDF xong, lấy số trang và cập nhật trạng thái đọc
                                .onLoad { pageCount ->
                                    totalPages = pageCount
                                    currentPage = 1
                                    updateReadingStatus()
                                    binding.progressBar.visibility = View.GONE
                                }

                                // Khi chuyển trang, cập nhật số trang đang xem
                                .onPageChange { page, pageCount ->
                                    totalPages = pageCount
                                    currentPage = page + 1 // page là chỉ số, bắt đầu từ 0
                                    updateReadingStatus()
                                }

                                // Nếu có lỗi chung khi load PDF
                                .onError { t ->
                                    Log.e(TAG, "PDF load error: ${t.message}")
                                    binding.progressBar.visibility = View.GONE
                                }

                                // Nếu có lỗi ở một trang cụ thể
                                .onPageError { page, t ->
                                    Log.e(TAG, "Page error on page $page: ${t.message}")
                                    binding.progressBar.visibility = View.GONE
                                }

                                .load() // Bắt đầu hiển thị PDF
                        }
                    } else {
                        // Trường hợp dữ liệu trả về rỗng
                        Log.e(TAG, "Empty PDF data")
                        runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                        }
                    }
                } else {
                    // Trường hợp phản hồi lỗi (ví dụ HTTP 404, 500...)
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
