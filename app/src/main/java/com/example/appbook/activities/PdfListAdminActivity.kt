package com.example.appbook.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.adapters.AdapterPdfAdmin
import com.example.appbook.databinding.ActivityPdfListAdminBinding
import com.example.appbook.models.ModelPdf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PdfListAdminActivity : AppCompatActivity() {

    // View Binding để truy cập các thành phần giao diện người dùng
    private lateinit var binding: ActivityPdfListAdminBinding

    private companion object {
        const val TAG = "PDF_LIST_ADMIN_TAG"
    }

    // Adapter để hiển thị danh sách PDF
    private lateinit var adapterPdfAdmin: AdapterPdfAdmin

    // ArrayList để lưu trữ danh sách các PDF
    private lateinit var pdfArrayList: ArrayList<ModelPdf>

    // Category id và title, được truyền từ Intent
    private var categoryId = ""
    private var category = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfListAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy categoryId và category từ Intent
        val intent = intent
        categoryId = intent.getStringExtra("categoryId")!!
        category = intent.getStringExtra("category")!!

        // Set tiêu đề của category
        binding.subTitleTv.text = category

        // Tải danh sách PDF/books
        loadPdfList()

        // Tìm kiếm
        binding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Không cần xử lý trước khi text thay đổi
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Lọc dữ liệu
                try {
                    adapterPdfAdmin.filter!!.filter(s) // Gọi hàm filter trong adapter
                } catch (e: Exception) {
                    Log.d(TAG, "onTextChanged: ${e.message}") // Log lỗi nếu có
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Không cần xử lý sau khi text thay đổi
            }
        })

        // Xử lý sự kiện click, quay lại
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }
    }

    // Hàm tải danh sách PDF từ Firebase
    private fun loadPdfList() {
        // Khởi tạo ArrayList
        pdfArrayList = ArrayList()

        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.orderByChild("categoryId").equalTo(categoryId) // Lọc theo categoryId
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Xóa list trước khi thêm dữ liệu mới vào
                    pdfArrayList.clear()
                    for (ds in snapshot.children) {
                        // Lấy dữ liệu
                        val model = ds.getValue(ModelPdf::class.java)

                        // Thêm vào list
                        model?.let { pdfArrayList.add(it) }

                        Log.d(TAG, "onDataChange: ${model?.title} ${model?.categoryId}")
                    }

                    // Thiết lập adapter
                    adapterPdfAdmin = AdapterPdfAdmin(this@PdfListAdminActivity, pdfArrayList)
                    binding.booksRv.adapter = adapterPdfAdmin
                }

                override fun onCancelled(error: DatabaseError) {
                    // Xử lý lỗi nếu có
                    Log.e(TAG, "onCancelled: Lỗi khi tải danh sách PDF", error.toException())
                }
            })
    }
}
