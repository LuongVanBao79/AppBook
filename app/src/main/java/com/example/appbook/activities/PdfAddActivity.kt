package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.appbook.databinding.ActivityPdfAddBinding
import com.example.appbook.models.ModelCategory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfAddActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityPdfAddBinding

    // Firebase Authentication để xác thực người dùng
    private lateinit var firebaseAuth: FirebaseAuth

    // ProgressDialog để hiển thị thông báo trong khi thực hiện các tác vụ
    private lateinit var progressDialog: ProgressDialog

    // ArrayList để lưu trữ danh sách các danh mục PDF
    private lateinit var categoryArrayList: ArrayList<ModelCategory>

    // URI của file PDF đã chọn
    private var pdfUri: Uri? = null

    // TAG để log thông tin
    private val TAG = "PDF_ADD_TAG"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo Firebase Authentication
        firebaseAuth = FirebaseAuth.getInstance()
        // Tải danh sách các danh mục PDF từ Firebase
        loadPdfCategories()

        // Thiết lập ProgressDialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Vui Lòng Đợi...") // Thiết lập tiêu đề
        progressDialog.setCanceledOnTouchOutside(false) // Ngăn người dùng tắt bằng cách chạm ra ngoài

        // Xử lý sự kiện click vào nút "Quay lại"
        binding.backBtn.setOnClickListener {
            onBackPressed() // Quay lại màn hình trước đó
        }

        // Xử lý sự kiện click vào TextView "Chọn danh mục"
        binding.categoryTv.setOnClickListener {
            categoryPickDialog() // Hiển thị dialog chọn danh mục
        }

        // Xử lý sự kiện click vào nút "Đính kèm PDF"
        binding.attachPdfBtn.setOnClickListener {
            pdfPickIntent() // Mở intent để chọn file PDF
        }

        // Xử lý sự kiện click vào nút "Tải lên"
        binding.submitBtn.setOnClickListener {
            // 1. Kiểm tra dữ liệu
            // 2. Tải PDF lên Cloudinary
            // 3. Lấy URL của PDF đã tải lên
            // 4. Tải thông tin PDF lên Firebase Database
            validateData() // Kiểm tra dữ liệu trước khi tải lên
        }
    }

    private var title = "" // Biến lưu trữ tiêu đề
    private var description = "" // Biến lưu trữ mô tả
    private var category = "" // Biến lưu trữ danh mục

    // Hàm kiểm tra tính hợp lệ của dữ liệu
    private fun validateData() {
        // 1. Kiểm tra dữ liệu
        Log.d(TAG, "validateData: validating data")

        // Lấy dữ liệu từ các EditText và TextView
        title = binding.titleEt.text.toString().trim() // Lấy tiêu đề
        description = binding.descriptionEt.text.toString().trim() // Lấy mô tả
        category = binding.categoryTv.text.toString().trim() // Lấy danh mục

        // Kiểm tra dữ liệu
        if (title.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề...", Toast.LENGTH_SHORT).show()
        } else if (description.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập mô tả...", Toast.LENGTH_SHORT).show()
        } else if (category.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn danh mục...", Toast.LENGTH_SHORT).show()
        } else if (pdfUri == null) {
            Toast.makeText(this, "Vui lòng chọn file PDF...", Toast.LENGTH_SHORT).show()
        } else {
            // Dữ liệu hợp lệ, bắt đầu tải lên
            uploadPdfToCloudinary() // Tải PDF lên Cloudinary
        }
    }

    // Hàm tải PDF lên Cloudinary
    // import kotlinx.coroutines.* // Cần import Coroutine
// import com.tom_roush.pdfbox.pdmodel.PDDocument // Cần thư viện PDFBox để xử lý file cục bộ

    private fun uploadPdfToCloudinary() {
        Log.d(TAG, "uploadPdfToCloudinary: uploading to Cloudinary...")

        progressDialog.setMessage("Đang tải lên Cloudinary...")
        progressDialog.show()

        val timestamp = System.currentTimeMillis()
        var tempFile: File? = null // Khai báo ngoài try/catch để đảm bảo xóa được

        // Sử dụng Coroutine để tạo file tạm bất đồng bộ
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. TÁC VỤ I/O NẶNG: Tạo file tạm
                val inputStream = contentResolver.openInputStream(pdfUri!!)
                tempFile = File.createTempFile("upload_pdf_temp", ".pdf", cacheDir)
                val outputStream = FileOutputStream(tempFile)

                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val filePath = tempFile!!.absolutePath

                // 2. BẮT ĐẦU UPLOAD TRÊN CLOUDINARY THREAD
                MediaManager.get().upload(filePath)
                    .option("resource_type", "raw")
                    .option("public_id", "Books/$timestamp")
                    .option("type", "upload")
                    .callback(object : UploadCallback {

                        override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                            Log.d(TAG, "onSuccess: Upload thành công $resultData")
                            val uploadedPdfUrl = resultData?.get("secure_url") as? String

                            // 3. XỬ LÝ DỮ LIỆU FILE TRÊN THIẾT BỊ (trước khi ghi DB)
                            if (uploadedPdfUrl != null) {
                                // Tải thông tin file (size, pages, ảnh bìa)
                                extractPdfDataAndUploadInfo(uploadedPdfUrl, timestamp, tempFile!!)
                            } else {
                                // Quay lại Main Thread để hiển thị Toast
                                runOnUiThread {
                                    Toast.makeText(this@PdfAddActivity, "Không lấy được URL", Toast.LENGTH_SHORT).show()
                                    progressDialog.dismiss()
                                }
                            }
                            tempFile?.delete() // Xóa file tạm sau khi hoàn tất
                        }

                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            runOnUiThread {
                                progressDialog.dismiss()
                                Toast.makeText(this@PdfAddActivity, "Upload thất bại: ${error?.description}", Toast.LENGTH_SHORT).show()
                            }
                            tempFile?.delete() // Xóa file tạm khi thất bại
                            Log.e(TAG, "Upload error: ${error?.description}")
                        }

                        // ... Giữ nguyên onStart, onProgress, onReschedule ...
                        override fun onStart(requestId: String?) { Log.d(TAG, "onStart: Upload bắt đầu") }
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) { Log.d(TAG, "onProgress: $bytes/$totalBytes") }
                        override fun onReschedule(requestId: String?, error: ErrorInfo?) { Log.d(TAG, "onReschedule: $error") }
                    })
                    .dispatch()

            } catch (e: Exception) {
                tempFile?.delete() // Xóa file tạm khi có lỗi trong quá trình tạo
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@PdfAddActivity, "Lỗi I/O file: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Lỗi I/O file", e)
                }
            }
        }
    }

    // Hàm tải thông tin PDF lên Firebase Database
    private fun uploadPdfInfoToDb(
        uploadedPdfUrl: String,
        timestamp: Long,
        fileSize: Long,      // THÊM: Dung lượng
        pagesCount: Int,     // THÊM: Số trang
        imageUrl: String     // THÊM: URL ảnh bìa
    ) {
        Log.d(TAG, "uploadPdfInfoToDb: uploading to db")
        progressDialog.setMessage("Đang tải thông tin PDF...")

        val uid = firebaseAuth.uid

        // Thiết lập dữ liệu để tải lên
        val hashMap: HashMap<String, Any> = HashMap()
        hashMap["uid"] = "$uid"
        hashMap["id"] = "$timestamp"
        hashMap["title"] = "$title"
        hashMap["description"] = "$description"
        hashMap["categoryId"] = "$selectedCategoryId"
        hashMap["url"] = "$uploadedPdfUrl"
        hashMap["timestamp"] = timestamp
        hashMap["viewsCount"] = 0
        hashMap["downloadsCount"] = 0

        // THÊM CÁC TRƯỜNG DỮ LIỆU MỚI
        hashMap["fileSize"] = fileSize
        hashMap["pagesCount"] = pagesCount
        hashMap["imageUrl"] = imageUrl // URL ảnh bìa (có thể rỗng nếu upload ảnh bìa thất bại)

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child("$timestamp")
            .setValue(hashMap)
            .addOnSuccessListener {
                Log.d(TAG, "uploadPdfInfoToDb: uploaded to db")
                progressDialog.dismiss()
                Toast.makeText(this, "Đã tải lên thành công!", Toast.LENGTH_SHORT).show()
                pdfUri = null
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "uploadPdfInfoDb: failed to upload due to ${e.message}")
                progressDialog.dismiss()
                Toast.makeText(this, "Tải lên thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
                pdfUri = null
            }
    }

    // Hàm tải danh sách các danh mục PDF từ Firebase
    private fun loadPdfCategories() {
        Log.d(TAG, "loadPdfCategories: Loading pdf categories")
        // Khởi tạo ArrayList
        categoryArrayList = ArrayList()

        // Tham chiếu đến node "Categories" trong Firebase Database
        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Xóa danh sách trước khi thêm dữ liệu mới
                categoryArrayList.clear()
                for (ds in snapshot.children) {
                    // Lấy dữ liệu từ DataSnapshot
                    val model = ds.getValue(ModelCategory::class.java)
                    // Thêm vào ArrayList
                    categoryArrayList.add(model!!)
                    Log.d(TAG, "onDataChange: ${model.category}")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Xử lý lỗi nếu có
            }
        })
    }

    private var selectedCategoryTitle = "" // Biến lưu trữ tiêu đề của danh mục đã chọn
    private var selectedCategoryId = "" // Biến lưu trữ ID của danh mục đã chọn

    // Hàm hiển thị dialog chọn danh mục
    private fun categoryPickDialog() {
        Log.d(TAG, "categoryPickDialog: Showing pdf category pick dialog")

        // Tạo mảng String chứa tên các danh mục
        val categoriesArray = arrayOfNulls<String>(categoryArrayList.size)
        for (i in categoryArrayList.indices) {
            categoriesArray[i] = categoryArrayList[i].category
        }

        // Tạo AlertDialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Chọn Danh Mục")
            .setItems(categoriesArray) { dialog, which ->
                // Xử lý sự kiện click vào một danh mục
                // Lấy danh mục đã chọn
                selectedCategoryTitle = categoryArrayList[which].category
                selectedCategoryId = categoryArrayList[which].id
                // Hiển thị danh mục đã chọn lên TextView
                binding.categoryTv.text = selectedCategoryTitle

                Log.d(TAG, "categoryPickDialog: Selected Category ID: $selectedCategoryId")
                Log.d(TAG, "categoryPickDialog: Selected Category Title: $selectedCategoryTitle")
            }
            .show() // Hiển thị AlertDialog
    }

    // Hàm mở intent để chọn file PDF
    private fun pdfPickIntent() {
        Log.d(TAG, "pdfPickIntent: starting pdf pick intent")

        val intent = Intent()
        intent.type = "application/pdf" // Chỉ định loại file là PDF
        intent.action = Intent.ACTION_GET_CONTENT // Chỉ định action là lấy nội dung
        pdfActivityResultLauncher.launch(intent) // Mở intent và chờ kết quả
    }

    // ActivityResultLauncher để xử lý kết quả trả về từ intent chọn file PDF
    val pdfActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            if (result.resultCode == RESULT_OK) {
                // Nếu chọn file thành công
                Log.d(TAG, "PDF Picked ")
                pdfUri = result.data!!.data // Lấy URI của file đã chọn
            } else {
                // Nếu hủy chọn file
                Log.d(TAG, "PDF Picked cancelled ")
                Toast.makeText(this, "Đã hủy", Toast.LENGTH_SHORT).show()
            }
        }
    )


    // Yêu cầu thư viện: implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    private fun extractPdfDataAndUploadInfo(uploadedPdfUrl: String, timestamp: Long, tempFile: File) {
        Log.d(TAG, "extractPdfDataAndUploadInfo: Extracting file data...")

        // 1. TÍNH TOÁN SIZE VÀ PAGES (Sử dụng PDFBox)
        var pagesCount = 0
        var imageUrl = ""
        val fileSize = tempFile.length() // Lấy kích thước file cục bộ (bytes)

        try {
            val document = PDDocument.load(tempFile) // Tải PDF bằng PDFBox
            pagesCount = document.numberOfPages // Lấy số trang

            // 2. TRÍCH XUẤT ẢNH BÌA
            val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(document)
            val image = renderer.renderImageWithDPI(0, 100.toFloat()) // Render trang 0 với DPI 100

            // Lưu ảnh tạm thời
            val coverFile = File.createTempFile("cover_temp", ".png", cacheDir)
            val out = FileOutputStream(coverFile)
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            document.close() // Đóng tài liệu

            // 3. UPLOAD ẢNH BÌA LÊN CLOUDINARY
            MediaManager.get().upload(coverFile.absolutePath)
                .option("public_id", "Covers/$timestamp")
                .option("type", "upload")
                .callback(object : UploadCallback {
                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        imageUrl = resultData?.get("secure_url") as? String ?: ""
                        coverFile.delete() // Xóa file cover tạm

                        // 4. LƯU TẤT CẢ THÔNG TIN VÀO FIREBASE DB
                        uploadPdfInfoToDb(uploadedPdfUrl, timestamp, fileSize, pagesCount, imageUrl)
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        coverFile.delete()
                        Log.e(TAG, "Cover Upload error: ${error?.description}")
                        // Vẫn lưu thông tin vào DB nhưng không có ảnh bìa
                        uploadPdfInfoToDb(uploadedPdfUrl, timestamp, fileSize, pagesCount, "")
                    }

                    // ... onStart, onProgress, onReschedule ...
                    override fun onStart(requestId: String?) { /* ... */ }
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) { /* ... */ }
                    override fun onReschedule(requestId: String?, error: ErrorInfo?) { /* ... */ }
                })
                .dispatch()

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi trích xuất dữ liệu PDF", e)
            // Nếu lỗi, vẫn lưu thông tin cơ bản
            uploadPdfInfoToDb(uploadedPdfUrl, timestamp, fileSize, pagesCount, imageUrl)
        }
    }
}



