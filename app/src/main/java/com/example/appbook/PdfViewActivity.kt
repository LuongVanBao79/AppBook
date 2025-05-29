package com.example.appbook

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityPdfViewBinding
import com.google.firebase.database.*
import okhttp3.*
import java.io.IOException

class PdfViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewBinding

    companion object {
        private const val TAG = "PDF_VIEW_TAG"
    }

    private var bookId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bookId = intent.getStringExtra("bookId") ?: ""
        Log.d(TAG, "Received bookId: $bookId")
        loadBookDetails()

        binding.backBtn.setOnClickListener {
            onBackPressed()
        }
    }

    private fun loadBookDetails() {
        Log.d(TAG, "loadBookDetails: Getting PDF URL from Firebase DB")
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val pdfUrl = snapshot.child("url").value as? String
                    if (!pdfUrl.isNullOrEmpty()) {
                        Log.d(TAG, "PDF URL: $pdfUrl")
                        loadBookFromCloudinaryUrl(pdfUrl)
                    } else {
                        Log.e(TAG, "PDF URL is empty or null")
                        Log.d(TAG, "Received bookId: $bookId")
                        binding.progressBar.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "loadBookDetails: ${error.message}")
                    binding.progressBar.visibility = View.GONE
                }
            })
    }

    private fun loadBookFromCloudinaryUrl(pdfUrl: String) {
        Log.d(TAG, "Downloading PDF from Cloudinary URL")

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(pdfUrl)
            .build()

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
                                .defaultPage(0)  // hiển thị trang đầu tiên
                                .enableAnnotationRendering(true) // render annotation nếu có
                                .swipeHorizontal(false)
                                .onLoad { pageCount ->
                                    binding.toolbarSubtitleTv.text = "1/$pageCount"  // cập nhật tổng trang và trang hiện tại lúc đầu
                                    binding.progressBar.visibility = View.GONE
                                }
                                .onPageChange { page, pageCount ->
                                    val currentPage = page + 1
                                    binding.toolbarSubtitleTv.text = "$currentPage/$pageCount"
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

}
