package com.example.appbook.activities

import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityPdfEditBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PdfEditActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityPdfEditBinding

    private companion object {
        private const val TAG = "PDF_EDIT_TAG"
    }

    // Book id nhận từ intent để chỉnh sửa thông tin sách
    private var bookId = ""

    // Progress dialog để hiển thị thông báo trong quá trình xử lý
    private lateinit var progressDialog: ProgressDialog

    // ArrayList để lưu trữ danh sách tiêu đề các category
    private lateinit var categoryTitleArrayList: ArrayList<String>

    // ArrayList để lưu trữ danh sách id các category
    private lateinit var categoryIdArrayList: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nhận book id từ intent để chỉnh sửa thông tin sách
        bookId = intent.getStringExtra("bookId")!!

        // Thiết lập progress dialog
        progressDialog = ProgressDialog(this).apply {
            setTitle("Vui lòng đợi")
            setCanceledOnTouchOutside(false)
        }

        loadCategories() // Tải danh sách category
        loadBookInfo() // Tải thông tin sách

        // Xử lý sự kiện click, quay lại
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }
        // Xử lý sự kiện click, chọn category
        binding.categoryTv.setOnClickListener {
            categoryDialog()
        }
        // Xử lý sự kiện click, bắt đầu cập nhật
        binding.submitBtn.setOnClickListener {
            validateData()
        }
    }

    // Hàm tải thông tin sách
    private fun loadBookInfo() {
        Log.d(TAG, "loadBookInfo: Loading book info")

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Lấy thông tin sách
                    selectedCategoryId = snapshot.child("categoryId").value.toString()
                    val description = snapshot.child("description").value.toString()
                    val title = snapshot.child("title").value.toString()

                    // Set thông tin lên view
                    binding.titleEt.setText(title)
                    binding.descriptionEt.setText(description)

                    // Tải thông tin category của sách sử dụng categoryId
                    Log.d(TAG, "onDataChange: Loading book category info")
                    val refBookCategory = FirebaseDatabase.getInstance().getReference("Categories")
                    refBookCategory.child(selectedCategoryId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                // Lấy category
                                val category = snapshot.child("category").value
                                // Set lên textview
                                binding.categoryTv.text = category.toString()
                            }

                            override fun onCancelled(error: DatabaseError) {
                                // Xử lý khi có lỗi xảy ra
                                Log.e(TAG, "onCancelled: Lỗi khi tải thông tin category của sách", error.toException())
                                Toast.makeText(this@PdfEditActivity, "Lỗi khi tải thông tin category", Toast.LENGTH_SHORT).show()
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý khi có lỗi xảy ra
                    Log.e(TAG, "onCancelled: Lỗi khi tải thông tin sách", error.toException())
                    Toast.makeText(this@PdfEditActivity, "Lỗi khi tải thông tin sách", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private var title = ""
    private var description = ""

    // Hàm kiểm tra dữ liệu
    private fun validateData() {
        // Lấy dữ liệu
        title = binding.titleEt.text.toString().trim()
        description = binding.descriptionEt.text.toString().trim()

        // Kiểm tra dữ liệu
        if (title.isEmpty()) {
            Toast.makeText(this, "Nhập tiêu đề", Toast.LENGTH_SHORT).show()
        } else if (description.isEmpty()) {
            Toast.makeText(this, "Nhập mô tả", Toast.LENGTH_SHORT).show()
        } else if (selectedCategoryId.isEmpty()) {
            Toast.makeText(this, "Chọn category", Toast.LENGTH_SHORT).show()
        } else {
            updatePdf() // Cập nhật thông tin sách
        }
    }

    // Hàm cập nhật thông tin sách
    private fun updatePdf() {
        Log.d(TAG, "updatePdf: Starting updating pdf info ...")

        // Hiển thị progress
        progressDialog.setMessage("Đang cập nhật thông tin sách")
        progressDialog.show()

        // Thiết lập dữ liệu để cập nhật lên db, spelling của các key phải giống như trong firebase
        val hashMap = HashMap<String, Any>().apply {
            put("title", title)
            put("description", description)
            put("categoryId", selectedCategoryId)
        }

        // Bắt đầu cập nhật
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId)
            .updateChildren(hashMap)
            .addOnSuccessListener {
                progressDialog.dismiss()
                Log.d(TAG, "updatePdf: Updated successfully...")
                Toast.makeText(this, "Cập nhật thành công...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "updatePdf: Failed to update due to ${e.message}", e)
                progressDialog.dismiss()
                Toast.makeText(this, "Cập nhật thất bại do ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private var selectedCategoryId = ""
    private var selectedCategoryTitle = ""

    // Hàm hiển thị dialog chọn category
    private fun categoryDialog() {
        // Hiển thị dialog để chọn category của pdf/book, chúng ta đã có danh sách category rồi

        // Tạo string array từ arraylist string
        val categoriesArray = categoryTitleArrayList.toTypedArray()

        // Alert dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Chọn Category")
            .setItems(categoriesArray) { dialog, position ->
                // Xử lý click, lưu category id và title đã click
                selectedCategoryId = categoryIdArrayList[position]
                selectedCategoryTitle = categoryTitleArrayList[position]

                // Set lên textView
                binding.categoryTv.text = selectedCategoryTitle
            }
            .show()
    }

    // Hàm tải danh sách category
    private fun loadCategories() {
        Log.d(TAG, "loadCategories: loading categories ...")
        categoryTitleArrayList = ArrayList()
        categoryIdArrayList = ArrayList()

        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Xóa list trước khi bắt đầu thêm dữ liệu vào
                categoryIdArrayList.clear()
                categoryTitleArrayList.clear()

                for (ds in snapshot.children) {
                    val id = ds.child("id").value.toString()
                    val category = ds.child("category").value.toString()

                    categoryIdArrayList.add(id)
                    categoryTitleArrayList.add(category)

                    Log.d(TAG, "onDataChange: Category ID $id")
                    Log.d(TAG, "onDataChange: Category $category")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Xử lý khi có lỗi xảy ra
                Log.e(TAG, "onCancelled: Lỗi khi tải danh sách category", error.toException())
                Toast.makeText(this@PdfEditActivity, "Lỗi khi tải danh sách category", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
