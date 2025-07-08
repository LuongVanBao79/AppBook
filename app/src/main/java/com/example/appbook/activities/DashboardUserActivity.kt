package com.example.appbook.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.example.appbook.BooksUserFragment
import com.example.appbook.models.ModelCategory
import com.example.appbook.databinding.ActivityDashboardUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DashboardUserActivity : AppCompatActivity() {

    // View Binding
    private lateinit var binding: ActivityDashboardUserBinding

    // Firebase Authentication
    private lateinit var firebaseAuth: FirebaseAuth

    // Danh sách các danh mục
    private lateinit var categoryArrayList: ArrayList<ModelCategory>

    // Adapter cho ViewPager
    private lateinit var viewPagerAdapter: ViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        // Kiểm tra trạng thái đăng nhập
        checkUser()

        // Thiết lập ViewPager và TabLayout
        setupViewPager()

        // Xử lý sự kiện click
        setupClickListeners()
    }

    /**
     * Thiết lập ViewPager và TabLayout
     */
    private fun setupViewPager() {
        // Khởi tạo adapter cho ViewPager
        viewPagerAdapter = ViewPagerAdapter(
            supportFragmentManager,
            FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT,
            this
        )

        // Khởi tạo danh sách danh mục
        categoryArrayList = ArrayList()

        // Thêm các danh mục mặc định
        addDefaultCategories()

        // Load danh sách danh mục từ Firebase
        loadCategoriesFromFirebase()

        // Liên kết ViewPager với TabLayout
        binding.viewPager.adapter = viewPagerAdapter
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }

    /**
     * Thêm các danh mục mặc định
     */
    private fun addDefaultCategories() {
        val defaultCategories = listOf(
            ModelCategory("All", "01", 1, ""),
            ModelCategory("Most Viewed", "02", 1, ""),
            ModelCategory("Most Downloaded", "03", 1, "")
        )

        defaultCategories.forEach { model ->
            categoryArrayList.add(model)
            viewPagerAdapter.addFragment(
                BooksUserFragment.newInstance(model.id, model.category, model.uid),
                model.category
            )
        }
    }

    /**
     * Load danh sách danh mục từ Firebase
     */
    private fun loadCategoriesFromFirebase() {
        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { ds ->
                    ds.getValue(ModelCategory::class.java)?.let { model ->
                        // Thêm danh mục vào danh sách
                        categoryArrayList.add(model)

                        // Thêm fragment tương ứng
                        viewPagerAdapter.addFragment(
                            BooksUserFragment.newInstance(model.id, model.category, model.uid),
                            model.category
                        )
                    }
                }
                // Cập nhật giao diện
                viewPagerAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // TODO: Xử lý lỗi khi cần thiết
            }
        })
    }

    /**
     * Xử lý sự kiện click
     */
    private fun setupClickListeners() {
        // Đăng xuất
        binding.logoutBtn.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Mở trang profile
        binding.profileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    /**
     * Adapter cho ViewPager
     */
    class ViewPagerAdapter(
        fm: FragmentManager,
        behavior: Int,
        private val context: Context
    ) : FragmentPagerAdapter(fm, behavior) {

        private val fragmentsList = ArrayList<Fragment>()
        private val fragmentTitleList = ArrayList<String>()

        override fun getCount(): Int = fragmentsList.size

        override fun getItem(position: Int): Fragment = fragmentsList[position]

        override fun getPageTitle(position: Int): CharSequence = fragmentTitleList[position]

        /**
         * Thêm fragment vào adapter
         * @param fragment Fragment cần thêm
         * @param title Tiêu đề hiển thị trên TabLayout
         */
        fun addFragment(fragment: Fragment, title: String) {
            fragmentsList.add(fragment)
            fragmentTitleList.add(title)
        }
    }

    /**
     * Kiểm tra trạng thái đăng nhập và cập nhật giao diện
     */
    private fun checkUser() {
        val firebaseUser = firebaseAuth.currentUser

        if (firebaseUser == null) {
            // Chưa đăng nhập
            binding.subTitleTv.text = "Bạn chưa đăng nhập"
            binding.profileBtn.visibility = View.GONE
            binding.logoutBtn.visibility = View.GONE
        } else {
            // Đã đăng nhập
            binding.subTitleTv.text = firebaseUser.email
            binding.profileBtn.visibility = View.VISIBLE
            binding.logoutBtn.visibility = View.VISIBLE
        }
    }
}