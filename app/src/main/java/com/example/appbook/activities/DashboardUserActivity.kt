package com.example.appbook.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.example.appbook.BooksUserFragment
import com.example.appbook.activities.MainActivity
import com.example.appbook.models.ModelCategory
import com.example.appbook.databinding.ActivityDashboardUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DashboardUserActivity : AppCompatActivity() {

    // ViewBinding
    private lateinit var binding: ActivityDashboardUserBinding

    // Firebase
    private lateinit var firebaseAuth: FirebaseAuth

    // Danh sách Category
    private lateinit var categoryArrayList: ArrayList<ModelCategory>
    private lateinit var viewPagerAdapter: ViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()
        checkUser()

        setupWithViewPagerAdapter(binding.viewPager)
        binding.tabLayout.setupWithViewPager(binding.viewPager)

        // Logout
        binding.logoutBtn.setOnClickListener {
            firebaseAuth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        //handle click, open profile
        binding.profileBtn.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }


    private fun setupWithViewPagerAdapter(viewPager: ViewPager) {
        viewPagerAdapter = ViewPagerAdapter(
            supportFragmentManager,
            FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT,
            this
        )

        // Init danh sách
        categoryArrayList = ArrayList()

        // Các category mặc định
        val modelAll = ModelCategory("All", "01", 1, "")
        val modelMostViewed = ModelCategory("Most Viewed", "02", 1, "")
        val modelMostDownloaded = ModelCategory("Most Downloaded", "03", 1, "")

        // Thêm vào danh sách
        categoryArrayList.add(modelAll)
        categoryArrayList.add(modelMostViewed)
        categoryArrayList.add(modelMostDownloaded)

        // Thêm các fragment mặc định
        viewPagerAdapter.addFragment(
            BooksUserFragment.Companion.newInstance(modelAll.id, modelAll.category, modelAll.uid),
            modelAll.category
        )
        viewPagerAdapter.addFragment(
            BooksUserFragment.Companion.newInstance(modelMostViewed.id, modelMostViewed.category, modelMostViewed.uid),
            modelMostViewed.category
        )
        viewPagerAdapter.addFragment(
            BooksUserFragment.Companion.newInstance(modelMostDownloaded.id, modelMostDownloaded.category, modelMostDownloaded.uid),
            modelMostDownloaded.category
        )

        // Load từ Firebase
        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (ds in snapshot.children) {
                    val model = ds.getValue(ModelCategory::class.java)
                    if (model != null) {
                        categoryArrayList.add(model)
                        viewPagerAdapter.addFragment(
                            BooksUserFragment.Companion.newInstance(model.id, model.category, model.uid),
                            model.category
                        )
                    }
                }
                viewPagerAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // Xử lý lỗi nếu cần
            }
        })

        // Gán adapter cho ViewPager
        viewPager.adapter = viewPagerAdapter
    }



    class ViewPagerAdapter(
        fm: FragmentManager,
        behavior: Int,
        private val context: Context
    ) : FragmentPagerAdapter(fm, behavior) {

        private val fragmentsList: ArrayList<Fragment> = ArrayList()
        private val fragmentTitleList: ArrayList<String> = ArrayList()

        override fun getCount(): Int {
            return fragmentsList.size
        }

        override fun getItem(position: Int): Fragment {
            return fragmentsList[position]
        }

        override fun getPageTitle(position: Int): CharSequence {
            return fragmentTitleList[position]
        }

        fun addFragment(fragment: Fragment, title: String) {
            fragmentsList.add(fragment)
            fragmentTitleList.add(title)
        }
    }

    //this activity can be opened with or without login, so hide logout and profile button when user not logged in

    private fun checkUser() {
        val firebaseUser = firebaseAuth.currentUser
        if (firebaseUser == null) {
            //not logged in, user can stay in user dashboard without login too
            binding.subTitleTv.text = "Not Logged In"

            //hide profile, logout
            binding.profileBtn.visibility = View.GONE
            binding.logoutBtn.visibility = View.GONE
        } else {
            //logged in, get and show user info
            val email = firebaseUser.email
            //set to textview of toolbar
            binding.subTitleTv.text = email

            //show profile, logout
            binding.profileBtn.visibility = View.VISIBLE
            binding.logoutBtn.visibility = View.VISIBLE
        }
    }
}