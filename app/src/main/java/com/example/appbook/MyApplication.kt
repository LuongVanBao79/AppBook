package com.example.appbook

import android.app.Activity
import android.app.Application
import android.app.ProgressDialog
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
                            .pages(0)
                            .spacing(0)
                            .swipeHorizontal(false)
                            .enableSwipe(false)
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
                                pagesTv?.text = "$nbPages"
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

        /*
         * Hàm deleteBook:
         *  - context: Context để hiển thị dialog và toast
         *  - bookId: id cuốn sách trong Firebase Database
         *  - bookUrl: URL file trên Cloudinary
         *  - bookTitle: tên sách để hiển thị trên dialog
         *  - cloudName, apiKey, apiSecret: cấu hình Cloudinary để xóa file
         */
        fun deleteBook(
            context: Context,
            bookId: String,
            bookUrl: String,
            bookTitle: String,
            cloudName: String,
            apiKey: String,
            apiSecret: String
        ) {
            val TAG = "DELETE_BOOK_TAG"

            val progressDialog = ProgressDialog(context).apply {
                setTitle("Please wait")
                setMessage("Deleting $bookTitle...")
                setCanceledOnTouchOutside(false)
                show()
            }

            val publicId = getPublicIdFromUrl(bookUrl)
            Log.d(TAG, "Public ID extracted: $publicId")

            if (publicId.isEmpty()) {
                progressDialog.dismiss()
                Toast.makeText(context, "Invalid URL, unable to extract public ID", Toast.LENGTH_SHORT).show()
                return
            }

            val encodedPublicId = Uri.encode(publicId, "/") // Include extension if present
            val deleteUrl = HttpUrl.Builder()
                .scheme("https")
                .host("api.cloudinary.com")
                .addPathSegment("v1_1")
                .addPathSegment(cloudName)
                .addPathSegment("resources")
                .addPathSegment("raw") // raw chứ không phải image
                .addQueryParameter("public_ids[]", publicId)
                .addQueryParameter("invalidate", "true")
                .build()


            val credential = Credentials.basic(apiKey, apiSecret)

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(deleteUrl)
                .delete()
                .addHeader("Authorization", credential)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to delete from Cloudinary: ${e.message}")
                    (context as? Activity)?.runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Cloudinary deletion failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        Log.d(TAG, "Deleted from Cloudinary successfully: $body")

                        // Xóa khỏi Firebase
                        val ref = FirebaseDatabase.getInstance().getReference("Books")
                        ref.child(bookId).removeValue()
                            .addOnSuccessListener {
                                (context as? Activity)?.runOnUiThread {
                                    progressDialog.dismiss()
                                    Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                (context as? Activity)?.runOnUiThread {
                                    progressDialog.dismiss()
                                    Toast.makeText(context, "Failed to delete from DB: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        Log.e(TAG, "Cloudinary deletion failed: $body")
                        (context as? Activity)?.runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(context, "Cloudinary deletion failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            })
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
    }
}
