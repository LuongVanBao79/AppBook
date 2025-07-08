package com.example.appbook

import android.app.Activity
import android.app.Application
import android.content.Context
import android.icu.util.Calendar
import android.net.Uri
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.github.barteksc.pdfviewer.PDFView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.HashMap
import java.util.Locale

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Khởi tạo Cloudinary khi ứng dụng bắt đầu
        val config = mapOf(
            "cloud_name" to "dak4ks7mx",
            "api_key" to "643815841764554",
            "api_secret" to "bQzV_ZZp3F9eyonEWhnn9vcqQts",
            "secure" to true
        )
        MediaManager.init(this, config)
    }

    companion object {
        private val TAG = "MyApplication"

        fun formatTimeStamp(timestamp: Long): String {
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = timestamp
            return DateFormat.format("dd/MM/yyyy", cal.time).toString()
        }

        fun loadPdfSizeFromCloudinary(pdfUrl: String, sizeTv: TextView) {
            val cloudName = "dak4ks7mx"
            val apiKey = "643815841764554"
            val apiSecret = "bQzV_ZZp3F9eyonEWhnn9vcqQts"

            // Lấy publicId từ URL Cloudinary
            fun getPublicIdFromUrl(url: String): String {
                val regex = """/upload/(?:v\d+/)?(.+)$""".toRegex() // Giữ nguyên đuôi .pdf
                val match = regex.find(url)
                return match?.groups?.get(1)?.value ?: ""
            }

            val publicId = getPublicIdFromUrl(pdfUrl)
            Log.d("Cloudinary", "Public ID: $publicId")

            if (publicId.isEmpty()) {
                Log.e("Cloudinary", "Không lấy được publicId từ URL")
                (sizeTv.context as Activity).runOnUiThread {
                    sizeTv.text = "Lỗi URL"
                }
                return
            }

            val credential = Credentials.basic(apiKey, apiSecret)
            val url = "https://api.cloudinary.com/v1_1/$cloudName/resources/raw/upload/$publicId"

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", credential)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Cloudinary", "Lỗi kết nối: ${e.message}")
                    (sizeTv.context as Activity).runOnUiThread {
                        sizeTv.text = "Lỗi kết nối"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e("Cloudinary", "Phản hồi lỗi: ${response.code}, body: $errorBody")
                        (sizeTv.context as Activity).runOnUiThread {
                            sizeTv.text = "Lỗi server"
                        }
                        return
                    }

                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val bytes = json.optLong("bytes", -1)
                    val sizeText = if (bytes != -1L) {
                        val sizeInKB = bytes / 1024.0
                        String.format("%.2f KB", sizeInKB)
                    } else {
                        "Không rõ dung lượng"
                    }

                    (sizeTv.context as Activity).runOnUiThread {
                        sizeTv.text = sizeText
                    }
                }
            })
        }

        fun loadPdfFromUrlSinglePage(
            pdfUrl: String,
            pdfTitle: String,
            pdfView: PDFView,
            progressBar: ProgressBar,
            pagesTv: TextView?
        ) {
            progressBar.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL(pdfUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    val inputStream = connection.inputStream
                    val bytes = inputStream.readBytes()
                    inputStream.close()

                    withContext(Dispatchers.Main) {
                        Log.d(TAG, "loadPdfFromUrlSinglePage: Loaded ${bytes.size} bytes")

                        pdfView.fromBytes(bytes)
                            .spacing(0)
                            .swipeHorizontal(false)
                            .enableSwipe(false) // Không cho vuốt sang trang khác
                            .onError { t ->
                                progressBar.visibility = View.INVISIBLE
                                Log.e(TAG, "loadPdfFromUrlSinglePage: ${t.message}")
                            }
                            .onPageError { page, t ->
                                progressBar.visibility = View.INVISIBLE
                                Log.e(TAG, "loadPdfFromUrlSinglePage: Page $page error ${t.message}")
                            }
                            .onLoad { nbPages ->
                                progressBar.visibility = View.INVISIBLE

                                // Hiển thị tổng số trang đúng
                                pagesTv?.text = "$nbPages"

                                // Chỉ hiển thị trang đầu tiên (sau khi đã load xong)
                                pdfView.jumpTo(0, true)
                            }
                            .load()

                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.INVISIBLE
                        Log.e(TAG, "loadPdfFromUrlSinglePage: Error ${e.message}")
                    }
                }
            }
        }

        fun loadCategory(categoryId: String, categoryTv: TextView) {
            val ref = FirebaseDatabase.getInstance().getReference("Categories")
            ref.child(categoryId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val category: String = "" + snapshot.child("category").value
                        categoryTv.text = category
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("CategoryLoad", "Failed to load category: ${error.message}")
                    }
                })
        }

        fun deleteBook(
            context: Context,
            bookId: String,
            bookTitle: String
        ) {
            val progressDialog = android.app.ProgressDialog(context)
            progressDialog.setCancelable(false)
            progressDialog.setMessage("Đang xóa sách...")
            progressDialog.show()

            val ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("Books")
            ref.child(bookId).removeValue()
                .addOnSuccessListener {
                    (context as? android.app.Activity)?.runOnUiThread {
                        progressDialog.dismiss()
                        android.widget.Toast.makeText(context, "Xóa sách thành công: $bookTitle", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    (context as? android.app.Activity)?.runOnUiThread {
                        progressDialog.dismiss()
                        android.widget.Toast.makeText(context, "Xóa sách trên Firebase thất bại: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
        }



        // KHÔNG bỏ phần mở rộng .pdf
        private fun getPublicIdFromUrl(url: String): String {
            return try {
                val uri = Uri.parse(url)
                val pathSegments = uri.pathSegments
                val uploadIndex = pathSegments.indexOf("upload")
                if (uploadIndex == -1 || uploadIndex + 2 >= pathSegments.size) return ""

                val publicIdSegments = pathSegments.subList(uploadIndex + 2, pathSegments.size)
                publicIdSegments.joinToString("/") // giữ nguyên cả .pdf
            } catch (e: Exception) {
                ""
            }
        }


        fun incrementBookViewCount(bookId: String){
            //get current book views count
            val ref = FirebaseDatabase.getInstance().getReference("Books")
            ref.child(bookId)
                .addListenerForSingleValueEvent(object : ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        //get views count
                        var viewsCount = "${snapshot.child("viewsCount").value}"

                        if(viewsCount == "" || viewsCount =="null"){
                            viewsCount = "0"
                        }

                        //2 Increment views count
                        val newViewsCount = viewsCount.toLong() + 1

                        //setup data to update in db
                        val hashMap = HashMap<String, Any>()
                        hashMap["viewsCount"] = newViewsCount

                        //set to db
                        val dbRef = FirebaseDatabase.getInstance().getReference("Books")
                        dbRef.child(bookId)
                            .updateChildren(hashMap)
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }
                })
        }

        fun removeFromFavorite(context: Context, bookId: String) {
            val TAG = "REMOVE_FAV_TAG"
            Log.d(TAG, "removeFromFavorite: Removing from fav")

            val firebaseAuth = FirebaseAuth.getInstance()

            //database ref
            val ref = FirebaseDatabase.getInstance().getReference("Users")
            ref.child(firebaseAuth.uid!!).child("Favorites").child(bookId)
                .removeValue()
                .addOnSuccessListener {
                    Log.d(TAG, "removeFromFavorite: Removed from fav")
                    Toast.makeText(context, "Đã xóa khỏi mục yêu thích", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.d(TAG, "removeFromFavorite: Failed to remove from fav due to ${e.message}")
                    Toast.makeText(context, "Xóa khỏi mục yêu thích thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
