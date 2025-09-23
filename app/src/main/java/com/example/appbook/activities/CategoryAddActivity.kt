package com.example.appbook.activities

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.databinding.ActivityCategoryAddBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Activity dùng để thêm danh mục sách vào Firebase Database
 */
class CategoryAddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoryAddBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var loadingDialog: AlertDialog
    private var category: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        initLoadingDialog()

        binding.backBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.submitBtn.setOnClickListener {
            validateCategoryInput()
        }
    }

    /**
     * Tạo AlertDialog hiện vòng quay loading
     */
    private fun initLoadingDialog() {
        val dialogView = LayoutInflater.from(this).inflate(
            com.example.appbook.R.layout.dialog_loading,
            null
        )
        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
    }

    /**
     * Kiểm tra tên danh mục trước khi thêm
     */
    private fun validateCategoryInput() {
        category = binding.categoryEt.text.toString().trim()

        if (category.isEmpty()) {
            Snackbar.make(binding.root, "Vui lòng nhập tên danh mục", Snackbar.LENGTH_SHORT).show()
        } else {
            addCategoryToFirebase()
        }
    }

    /**
     * Thêm danh mục vào Firebase
     */
    private fun addCategoryToFirebase() {
        loadingDialog.show()

        val timestamp = System.currentTimeMillis()
        val categoryData = hashMapOf<String, Any>(
            "id" to "$timestamp",
            "category" to category,
            "timestamp" to timestamp,
            "uid" to (firebaseAuth.uid ?: "")
        )

        FirebaseDatabase.getInstance().getReference("Categories")
            .child("$timestamp")
            .setValue(categoryData)
            .addOnSuccessListener {
                loadingDialog.dismiss()
                Snackbar.make(binding.root, "Thêm danh mục thành công", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                loadingDialog.dismiss()
                Snackbar.make(binding.root, "Thêm thất bại: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }
}
