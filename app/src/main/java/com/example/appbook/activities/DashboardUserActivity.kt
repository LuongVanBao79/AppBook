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

import com.example.appbook.databinding.ActivityDashboardUserBinding
import com.example.appbook.models.ModelCategory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import android.text.Editable
import android.text.TextWatcher

import com.example.appbook.adapters.AdapterBookSearch
import com.example.appbook.models.ModelBook

class DashboardUserActivity : AppCompatActivity() {

    // View Binding
    private lateinit var binding: ActivityDashboardUserBinding

    // Các biến cho tìm kiếm
    private lateinit var bookArrayList: ArrayList<ModelBook> // Danh sách gốc chứa tất cả sách
    private lateinit var adapterBookSearch: AdapterBookSearch

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

        loadAllBooks()

        // 2. Lắng nghe sự kiện gõ phím
        binding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    val query = s.toString()
                    if (query.isEmpty()) {
                        // Nếu ô tìm kiếm trống -> Ẩn list tìm kiếm, hiện lại nội dung chính
                        binding.searchRv.visibility = View.GONE
                        binding.contentRl.visibility = View.VISIBLE
                    } else {
                        // Nếu có chữ -> Hiện list tìm kiếm, ẩn nội dung chính
                        binding.searchRv.visibility = View.VISIBLE
                        binding.contentRl.visibility = View.GONE
                        filterBooks(query)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAllBooks() {
        bookArrayList = ArrayList()
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                bookArrayList.clear()
                for (ds in snapshot.children) {
                    val model = ds.getValue(ModelBook::class.java)
                    if (model != null) {
                        bookArrayList.add(model)
                    }
                }
                // Khởi tạo adapter với danh sách rỗng ban đầu (hoặc full tùy ý)
                // Nhưng logic của mình là chỉ hiện khi search nên chưa cần gán list full vào adapter ngay
            }

            override fun onCancelled(error: DatabaseError) {
            }
        })
    }

    private fun filterBooks(query: String) {
        val filteredList = ArrayList<ModelBook>()

        // Logic tìm kiếm thông minh: Duyệt qua tất cả sách
        for (item in bookArrayList) {
            // Kiểm tra xem Tên sách HOẶC Tên tác giả có chứa từ khóa không
            // ignoreCase = true để không phân biệt hoa thường
            if (item.title.contains(query, ignoreCase = true) ||
                item.author.contains(query, ignoreCase = true)) {
                filteredList.add(item)
            }
        }

        // Setup adapter với list đã lọc
        adapterBookSearch = AdapterBookSearch(this@DashboardUserActivity, filteredList)
        binding.searchRv.adapter = adapterBookSearch
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
        binding.viewPager.offscreenPageLimit = 4 // Giữ trạng thái cho 4 tab để tránh load lại
        binding.tabLayout.setupWithViewPager(binding.viewPager)
    }

    /**
     * Thêm các danh mục mặc định
     * ĐÃ SỬA: Dùng Named Arguments để khớp với ModelCategory mới
     */
    private fun addDefaultCategories() {
        // ModelCategory(id, category, uid, timestamp)
        val defaultCategories = listOf(
            ModelCategory(id = "01", category = "Tất cả sách", uid = "system", timestamp = 0),
            ModelCategory(id = "02", category = "Xem nhiều nhất", uid = "system", timestamp = 0),
            ModelCategory(id = "03", category = "Tải nhiều nhất", uid = "system", timestamp = 0),
            ModelCategory(id = "04", category = "Có thể bạn thích", uid = "system", timestamp = 0)
        )

        defaultCategories.forEach { model ->
            categoryArrayList.add(model)

            when (model.id) {
                "04" -> {
                    // Tab Recommend: tạo fragment trực tiếp
                    // Đảm bảo bạn đã tạo file RecommendFragment.kt
                    viewPagerAdapter.addFragment(
                        RecommendFragment(),
                        model.category
                    )
                }
                else -> {
                    // Các tab khác: tạo fragment BooksUserFragment
                    // Dùng hàm newInstance là chuẩn nhất
                    viewPagerAdapter.addFragment(
                        BooksUserFragment.newInstance(
                            categoryId = model.id,
                            category = model.category,
                            uid = model.uid
                        ),
                        model.category
                    )
                }
            }
        }
    }


    /**
     * Load danh sách danh mục từ Firebase
     */
    private fun loadCategoriesFromFirebase() {
        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Chúng ta giữ nguyên các tab mặc định, chỉ thêm category mới vào sau
                snapshot.children.forEach { ds ->
                    val model = ds.getValue(ModelCategory::class.java)
                    if (model != null) {
                        // Thêm danh mục vào danh sách quản lý
                        categoryArrayList.add(model)

                        // Thêm fragment tương ứng vào ViewPager
                        viewPagerAdapter.addFragment(
                            BooksUserFragment.newInstance(model.id, model.category, model.uid),
                            model.category
                        )
                    }
                }
                // Cập nhật giao diện ViewPager
                viewPagerAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // Có thể log lỗi ra nếu cần
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
            // Xóa session nếu cần thiết
            // MyApplication.clearUserSession(applicationContext)
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
//            binding.subTitleTv.text = "Bạn chưa đăng nhập"
            binding.profileBtn.visibility = View.GONE
            binding.logoutBtn.visibility = View.GONE
        } else {
            // Đã đăng nhập
//            binding.subTitleTv.text = firebaseUser.email
            binding.profileBtn.visibility = View.VISIBLE
            binding.logoutBtn.visibility = View.VISIBLE
        }
    }
}