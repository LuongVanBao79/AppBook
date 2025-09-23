package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.appbook.filters.FilterCategory
import com.example.appbook.models.ModelCategory
import com.example.appbook.activities.PdfListAdminActivity
import com.example.appbook.databinding.RowCategoryBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import com.google.firebase.database.DatabaseError

class AdapterCategory : RecyclerView.Adapter<AdapterCategory.HolderCategory>, Filterable {
    private val context: Context
    public var categoryArrayList: ArrayList<ModelCategory>
    private var filterList: ArrayList<ModelCategory>

    private var filter: FilterCategory? = null
    private lateinit var binding: RowCategoryBinding

    //constructor
    constructor(context: Context, categoryArrayList: ArrayList<ModelCategory>) {
        this.context = context
        this.categoryArrayList = categoryArrayList
        this.filterList = categoryArrayList
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HolderCategory {
        //inflate/bind row_category.xml
        binding = RowCategoryBinding.inflate(LayoutInflater.from(context), parent, false)

        return HolderCategory(binding.root)
    }

    override fun onBindViewHolder(
        holder: HolderCategory,
        position: Int
    ) {
        /*Get Data, Set Data, Handle clicks etc*/

        //get data
        var model = categoryArrayList[position]
        val id = model.id
        var category = model.category
        val uid = model.uid
        val timestamp = model.timestamp

        //set data
        holder.categoryTv.text = category

        //handle click, delete category
        holder.deleteBtn.setOnClickListener {
            // Kiểm tra xem có sách nào thuộc danh mục này không
            val ref = FirebaseDatabase.getInstance().getReference("Books")
            ref.orderByChild("categoryId").equalTo(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // Có sách trong danh mục này → không cho xoá
                            Toast.makeText(context, "Không thể xoá vì danh mục đang có sách!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Không có sách → hiển thị dialog xác nhận xoá
                            val builder = AlertDialog.Builder(context)
                            builder.setTitle("Xoá danh mục")
                                .setMessage("Bạn có chắc chắn muốn xoá danh mục này?")
                                .setPositiveButton("Xác nhận") { dialog, _ ->
                                    Toast.makeText(context, "Đang xoá...", Toast.LENGTH_SHORT).show()
                                    deleteCategory(model, holder)
                                }
                                .setNegativeButton("Huỷ") { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(context, "Lỗi khi kiểm tra dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }


        //handle click, start pdf list admin activity, also pas pdf id, title
        holder.itemView.setOnClickListener {
            val intent = Intent(context, PdfListAdminActivity::class.java)
            intent.putExtra("categoryId", id)
            intent.putExtra("category", category)
            context.startActivity(intent)
        }
    }

    private fun deleteCategory(model: ModelCategory, holder: HolderCategory) {
        //get id of category to delete
        val id = model.id
        //Firebase DB > Categories > categoryId
        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.child(id)
            .removeValue()
            .addOnSuccessListener {
                Toast.makeText(context, "Đã xoá", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                Toast.makeText(context, "Không thể xóa do ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    override fun getItemCount(): Int {
        return categoryArrayList.size // number of items in list
    }

    override fun getFilter(): Filter? {
        if(filter == null){
            filter = FilterCategory(filterList, this)
        }
        return filter as FilterCategory
    }


    //ViewHolder class to hold/init UI views for row_category.xml
    inner class HolderCategory(itemView: View): RecyclerView.ViewHolder(itemView){
        //init ui view
        var categoryTv: TextView = binding.categoryTv
        var deleteBtn: ImageButton = binding.deleteBtn
    }
}