package com.example.appbook.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appbook.R
import com.example.appbook.adapters.AdapterPdfUser
import com.example.appbook.models.ModelPdf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.math.log10

class RecommendFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: AdapterPdfUser
    private lateinit var pdfList: ArrayList<ModelPdf>

    private val dbRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("Books")
    private val usersRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("Users")
    private val currentUser get() = FirebaseAuth.getInstance().currentUser

    // Data class lưu điểm từng nguồn
    data class BookScoreDetail(
        val cf: Double = 0.0,
        val category: Double = 0.0,
        val popular: Double = 0.0,
        val bonus: Double = 0.0
    ) {
        val total: Double get() = cf + category + popular + bonus
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_books_user, container, false)
        recyclerView = view.findViewById(R.id.booksRv)
        emptyText = view.findViewById(R.id.searchEt) // tạm thời

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        pdfList = ArrayList()
        adapter = AdapterPdfUser(requireContext(), pdfList)
        recyclerView.adapter = adapter

        loadRecommendations()
        return view
    }

    private fun loadRecommendations() {
        val uid = currentUser?.uid ?: return

        usersRef.get().addOnSuccessListener { snapshot ->

            // 👉 Lấy lịch sử của user hiện tại
            val myHistory = snapshot.child(uid).child("history")
                .children.map { it.key!! }.toSet()
            Log.d("BookCF", "My History: $myHistory")

            // --- Collaborative Filtering: Jaccard similarity ---
            val weightCF = 0.5
            val cfScores = mutableMapOf<String, Double>()

            for (userSnap in snapshot.children) {
                val otherUid = userSnap.key ?: continue
                if (otherUid == uid) continue

                val otherHistory = userSnap.child("history")
                    .children.map { it.key!! }.toSet()

                val union = myHistory union otherHistory
                val common = myHistory intersect otherHistory

                if (union.isNotEmpty()) {
                    val similarity = common.size.toDouble() / union.size.toDouble()
                    if (similarity > 0) {
                        Log.d("BookCF", "User $otherUid similarity=$similarity")

                        // cộng điểm CF cho sách mình chưa đọc
                        for (bookId in otherHistory - myHistory) {
                            cfScores[bookId] = (cfScores[bookId] ?: 0.0) + similarity * weightCF
                        }
                    }
                }
            }
            Log.d("BookCF", "CF Scores: $cfScores")

            // --- Category-based ---
            val lastCategory = snapshot.child(uid).child("lastViewedCategory").getValue(String::class.java)
            val categoryQuery = if (!lastCategory.isNullOrEmpty()) {
                dbRef.orderByChild("categoryId").equalTo(lastCategory)
            } else null

            // --- Load all books ---
            dbRef.get().addOnSuccessListener { bookSnap ->
                val bookScores = mutableMapOf<String, BookScoreDetail>()
                val weightCategory = 0.3
                val weightPopular = 0.2

                // 👉 Cập nhật CF
                for ((id, score) in cfScores) {
                    val detail = bookScores[id] ?: BookScoreDetail()
                    bookScores[id] = detail.copy(cf = detail.cf + score)
                }

                // 👉 Cập nhật Popular
                for (child in bookSnap.children) {
                    val id = child.key ?: continue
                    val views = child.child("viewsCount").getValue(Long::class.java) ?: 0L
                    val detail = bookScores[id] ?: BookScoreDetail()
                    bookScores[id] = detail.copy(popular = detail.popular + weightPopular * log10(views.toDouble() + 1))
                }

                // 👉 Cập nhật Category
                if (categoryQuery != null) {
                    categoryQuery.get().addOnSuccessListener { catSnap ->
                        val categoryBooks = catSnap.children.mapNotNull { it.key }.toSet()
                        for (id in categoryBooks) {
                            val detail = bookScores[id] ?: BookScoreDetail()
                            bookScores[id] = detail.copy(category = detail.category + weightCategory)
                        }
                        applyBonus(bookScores)
                        logScores(bookScores, bookSnap)
                        showBooks(bookScores, bookSnap)
                    }
                } else {
                    applyBonus(bookScores)
                    logScores(bookScores, bookSnap)
                    showBooks(bookScores, bookSnap)
                }
            }
        }
    }

    private fun applyBonus(scores: MutableMap<String, BookScoreDetail>) {
        for ((id, detail) in scores) {
            if (detail.cf > 0 || detail.category > 0 || detail.popular > 0) {
                // sách xuất hiện ít nhất 1 nguồn, cộng bonus nếu tổng > 0.5
                if (detail.total > 0.5) scores[id] = detail.copy(bonus = detail.bonus + 0.1)
            }
        }
    }

    private fun logScores(scores: Map<String, BookScoreDetail>, snapshot: DataSnapshot) {
        Log.d("BookScoreDetail", "===== Book Scores =====")
        for ((id, detail) in scores) {
            val title = snapshot.child(id).child("title").getValue(String::class.java) ?: "Unknown"
            val views = snapshot.child(id).child("viewsCount").getValue(Long::class.java) ?: 0L
            Log.d("BookScoreDetail", "ID:$id, Title:$title, Views:$views, " +
                    "CF=${detail.cf}, Category=${detail.category}, Popular=${detail.popular}, Bonus=${detail.bonus}, Total=${detail.total}")
        }
    }

    private fun showBooks(scores: Map<String, BookScoreDetail>, snapshot: DataSnapshot) {
        if (scores.isEmpty()) {
            showEmpty("Không có gợi ý nào.")
            return
        }

        val sortedIds = scores.entries.sortedByDescending { it.value.total }.map { it.key }
        pdfList.clear()
        for (id in sortedIds) {
            val model = snapshot.child(id).getValue(ModelPdf::class.java)
            if (model != null) pdfList.add(model)
        }

        if (pdfList.isEmpty()) showEmpty("Không có gợi ý nào.")
        else {
            recyclerView.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
        }
    }

    private fun showEmpty(msg: String) {
        recyclerView.visibility = View.GONE
        emptyText.text = msg
        emptyText.visibility = View.VISIBLE
    }
}
