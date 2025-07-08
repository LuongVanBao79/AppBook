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
            // 2. Tải PDF lên Firebase Storage (hoặc Cloudinary)
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
    private fun uploadPdfToCloudinary() {
        Log.d(TAG, "uploadPdfToCloudinary: uploading to Cloudinary...")

        progressDialog.setMessage("Đang tải lên Cloudinary...") // Thiết lập thông báo
        progressDialog.show() // Hiển thị ProgressDialog

        val timestamp = System.currentTimeMillis() // Lấy timestamp để tạo ID duy nhất

        // Tạo file tạm từ uri (vì Cloudinary có thể không hiểu rõ uri content://...)
        try {
            val inputStream = contentResolver.openInputStream(pdfUri!!) // Mở input stream từ URI
            val tempFile = File.createTempFile("upload_pdf_temp", ".pdf", cacheDir) // Tạo file tạm
            val outputStream = FileOutputStream(tempFile) // Tạo output stream để ghi vào file tạm

            inputStream?.copyTo(outputStream) // Sao chép dữ liệu từ input stream sang output stream
            inputStream?.close() // Đóng input stream
            outputStream.close() // Đóng output stream

            // Upload bằng File path
            MediaManager.get().upload(tempFile.absolutePath) // Tải file lên Cloudinary
                .option("resource_type", "raw") // Chỉ định loại tài nguyên là "raw" (cho PDF)
                .option("public_id", "Books/$timestamp") // Đặt public ID cho file trên Cloudinary
                .option("type", "upload") // Chỉ định loại upload
                .callback(object : UploadCallback { // Thiết lập callback để theo dõi quá trình upload
                    override fun onStart(requestId: String?) {
                        Log.d(TAG, "onStart: Upload bắt đầu")
                    }

                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                        Log.d(TAG, "onProgress: $bytes/$totalBytes")
                    }

                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        Log.d(TAG, "onSuccess: Upload thành công $resultData")
                        val uploadedPdfUrl = resultData?.get("secure_url") as? String // Lấy URL của file đã tải lên
                        if (uploadedPdfUrl != null) {
                            uploadPdfInfoToDb(uploadedPdfUrl, timestamp) // Tải thông tin PDF lên Firebase Database
                        } else {
                            Toast.makeText(this@PdfAddActivity, "Không lấy được URL", Toast.LENGTH_SHORT).show()

                        }

                        tempFile.delete() // Xóa file tạm
                        progressDialog.dismiss() // Ẩn ProgressDialog
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        progressDialog.dismiss() // Ẩn ProgressDialog
                        Toast.makeText(this@PdfAddActivity, "Upload thất bại: ${error?.description}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Upload error: ${error?.description}")
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                        Log.d(TAG, "onReschedule: $error")
                    }
                })
                .dispatch() // Bắt đầu upload

        } catch (e: Exception) {
            progressDialog.dismiss() // Ẩn ProgressDialog
            Toast.makeText(this, "Lỗi khi xử lý file PDF: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Lỗi khi xử lý file PDF", e)
        }
    }

    // Hàm tải thông tin PDF lên Firebase Database
    private fun PdfAddActivity.uploadPdfInfoToDb(uploadedPdfUrl: String, timestamp: Long) {
        // 4. Tải thông tin PDF lên Firebase Database
        Log.d(TAG, "uploadPdfInfoToDb: uploading to db")
        progressDialog.setMessage("Đang tải thông tin PDF...") // Thiết lập thông báo

        // Lấy UID của người dùng hiện tại
        val uid = firebaseAuth.uid

        // Thiết lập dữ liệu để tải lên
        val hashMap: HashMap<String, Any> = HashMap()
        hashMap["uid"] = "$uid" // UID của người dùng
        hashMap["id"] = "$timestamp" // ID của PDF (timestamp)
        hashMap["title"] = "$title" // Tiêu đề
        hashMap["description"] = "$description" // Mô tả
        hashMap["categoryId"] = "$selectedCategoryId" // ID của danh mục
        hashMap["url"] = "$uploadedPdfUrl" // URL của PDF đã tải lên
        hashMap["timestamp"] = timestamp // Timestamp
        hashMap["viewsCount"] = 0 // Số lượt xem
        hashMap["downloadsCount"] = 0 // Số lượt tải

        // Tham chiếu đến node "Books" trong Firebase Database
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child("$timestamp") // Tạo node con với ID là timestamp
            .setValue(hashMap) // Thiết lập giá trị cho node con
            .addOnSuccessListener {
                // Nếu tải lên thành công
                Log.d(TAG, "uploadPdfInfoToDb: uploaded to db")
                progressDialog.dismiss() // Ẩn ProgressDialog
                Toast.makeText(this, "Đã tải lên...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Nếu tải lên thất bại
                Log.d(TAG, "uploadPdfInfoDb: failed to upload due to ${e.message}")
                progressDialog.dismiss() // Ẩn ProgressDialog
                Toast.makeText(this, "Tải lên thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
                pdfUri = null // Đặt lại pdfUri về null
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
}
