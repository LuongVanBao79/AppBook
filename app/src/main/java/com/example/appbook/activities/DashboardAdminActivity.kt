package com.example.appbook.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.adapters.AdapterCategory
import com.example.appbook.databinding.ActivityDashboardAdminBinding
import com.example.appbook.models.ModelCategory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardAdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardAdminBinding
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var categoryArrayList: ArrayList<ModelCategory>
    private lateinit var adapterCategory: AdapterCategory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        checkUser()
        loadCategories()
        setupSearchFunctionality()
        setupClickListeners()
    }

    /**
     * Kiểm tra người dùng đã đăng nhập chưa
     */
    private fun checkUser() {
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser != null) {
            binding.subTitleTv.text = firebaseUser.email
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    /**
     * Tải danh sách danh mục từ Firebase Realtime Database
     */
    private fun loadCategories() {
        categoryArrayList = ArrayList()

        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoryArrayList.clear()

                for (ds in snapshot.children) {
                    try {
                        val model = ds.getValue(ModelCategory::class.java)
                        if (model != null) {
                            categoryArrayList.add(model)
                        }
                    } catch (e: Exception) {
                        showError("Lỗi khi đọc dữ liệu: ${e.message}")
                    }
                }

                adapterCategory = AdapterCategory(this@DashboardAdminActivity, categoryArrayList)
                binding.categoriesRv.adapter = adapterCategory
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Firebase Error: ${error.message}")
            }
        })
    }

    /**
     * Thiết lập chức năng tìm kiếm danh mục
     */
    private fun setupSearchFunctionality() {
        binding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    adapterCategory.filter?.filter(s)
                } catch (e: Exception) {
                    showError("Lỗi khi lọc danh mục: ${e.message}")
                }
            }
        })
    }

    /**
     * Gán sự kiện click cho các nút chức năng
     */
    private fun setupClickListeners() {
        binding.logoutBtn.setOnClickListener {
            firebaseAuth.signOut()
            checkUser()
        }

        binding.addCategoryBtn.setOnClickListener {
            startActivity(Intent(this, CategoryAddActivity::class.java))
        }

        binding.addPdfFab.setOnClickListener {
            startActivity(Intent(this, PdfAddActivity::class.java))
        }

        binding.profileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    /**
     * Hiển thị lỗi bằng Snackbar
     */
    private fun showError(message: String?) {
        Snackbar.make(binding.root, message ?: "Đã xảy ra lỗi", Snackbar.LENGTH_LONG).show()
    }
}
