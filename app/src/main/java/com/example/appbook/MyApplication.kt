package com.example.appbook

import android.app.Application
import android.content.Context
import android.icu.util.Calendar
import android.text.format.DateFormat
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.util.HashMap
import java.util.Locale

class MyApplication : Application() {

    private external fun getApiSecretFromNative(): String

    override fun onCreate() {
        super.onCreate()
        // Khởi tạo Cloudinary khi ứng dụng bắt đầu
        val secureSecret = getApiSecretFromNative()

        // Khởi tạo Cloudinary
        val config = mapOf(
            "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
            "api_key" to BuildConfig.CLOUDINARY_API_KEY,
            "api_secret" to secureSecret,
            "secure" to true
        )
        MediaManager.init(this, config)

        val ref = FirebaseDatabase.getInstance().reference
        Log.d("BAO_MAT_DUONG_TRUYEN", "Kết nối an toàn tới: $ref")
    }

    companion object {
        private val TAG = "MyApplication"

        init {
            // Tên thư viện phải khớp với tên trong CMakeLists.txt (app-security)
            System.loadLibrary("app-security")
        }



        fun clearUserSession(context: Context) {
            clearAllSharedPreferences(context)
//            PdfViewActivity.clearCachedPages()
//            WidgetUtils.updateContinueReadingWidget(context)
        }

        private fun clearAllSharedPreferences(context: Context) {
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.forEach { prefFile ->
                val name = prefFile.nameWithoutExtension
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }
        }

        fun formatTimestamp(timestamp: Long): String {
            val calendar = java.util.Calendar.getInstance(java.util.Locale.ENGLISH)
            calendar.timeInMillis = timestamp
            return android.text.format.DateFormat.format("dd/MM/yyyy", calendar).toString()
        }

        fun formatTimeStamp(timestamp: Long): String {
            val cal = Calendar.getInstance(Locale.ENGLISH)
            cal.timeInMillis = timestamp
            return DateFormat.format("dd/MM/yyyy", cal.time).toString()
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
