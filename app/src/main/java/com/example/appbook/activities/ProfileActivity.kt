package com.example.appbook.activities

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.adapters.AdapterCategory
import com.example.appbook.adapters.AdapterPdfFavorite
import com.example.appbook.databinding.ActivityProfileBinding
import com.example.appbook.models.ModelPdf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileActivity : AppCompatActivity() {

    //view binding
    private lateinit var binding: ActivityProfileBinding

    //firebase auth
    private lateinit var firebaseAuth: FirebaseAuth
    //firebase current user
    private lateinit var firebaseUser: FirebaseUser

    //arraylist to hold books
    private lateinit var booksArrayList: ArrayList<ModelPdf>
    private lateinit var adapterPdfFavorite: AdapterPdfFavorite

    //progress dialog
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //reset to default values
        binding.accountTypeTv.text = "N/A"
        binding.memberDateTv.text = "N/A"
        binding.favoriteBookCountTv.text = "N/A"
        binding.accountStatusTv.text = "N/A"

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseUser = firebaseAuth.currentUser!!

        //init /setup progress dialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait...")
        progressDialog.setCanceledOnTouchOutside(false)

        loadUserInfo()
        loadFavoriteBooks()

        //handle click, go back
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        //handle click, open edit profile
        binding.profileEditBtn.setOnClickListener {
            startActivity(Intent(this, ProfileEditActivity::class.java))
        }

        //handle click, verify user if not
        binding.accountStatusTv.setOnClickListener {
            if(firebaseUser.isEmailVerified){
                //user is verified
                Toast.makeText(this, "Already verified...!", Toast.LENGTH_SHORT).show()
            }
            else{
                //User isn't verified, show confirmation dialog before verification
                emailVerificationDialog()
            }
        }
    }

    private fun emailVerificationDialog() {
        //show confirmation dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Verify Email")
            .setMessage("Are you sure you want to send email verification instructions to your email ${firebaseUser.email}")
            .setPositiveButton("SEND") { d, e->
                sendEmailVerification()
            }
            .setNegativeButton("CANCEL") { d, e ->
                d.dismiss()
            }
            .show()
    }

    private fun sendEmailVerification() {
        //show progress dialog
        progressDialog.setMessage("Sending email verification instructions to email ${firebaseUser.email}")
        progressDialog.show()

        //send instructions
        firebaseUser.sendEmailVerification()
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Instructions sent! check your email ${firebaseUser.email}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to send due to ${e.message}!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserInfo() {
        //check if user is verified or not, changes may effect after re login when you verify email
        if(firebaseUser.isEmailVerified){
            binding.accountStatusTv.text = "Verified"
        }
        else{
            binding.accountStatusTv.text = "Not Verified"
        }


        //db reference to load user info
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //get user info
                    val email = "${snapshot.child("email").value}"
                    val name = "${snapshot.child("name").value}"
                    val profileImage = "${snapshot.child("profileImage").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"
                    val uid = "${snapshot.child("uid").value}"
                    val userType = "${snapshot.child("userType").value}"

                    //convert timestamp to peroper date format
                    val formattedDate = MyApplication.formatTimeStamp(timestamp.toLong())

                    //set data
                    binding.nameTv.text = name
                    binding.emailTv.text = email
                    binding.memberDateTv.text = formattedDate
                    binding.accountTypeTv.text = userType

                    //set image
                    try{
                        Glide.with(this@ProfileActivity)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(binding.profileIv)
                    }catch (e: Exception){

                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private fun loadFavoriteBooks() {
        //init arraylist
        booksArrayList = ArrayList();

        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!).child("Favorites")
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //clear arraylist, before starting adding data
                    booksArrayList.clear()
                    for(ds in snapshot.children){
                        //get only id of the books, rest of the info we have loaded in adapter class
                        val bookId = "${ds.child("bookId").value}"

                        //set to model
                        val modelPdf = ModelPdf()
                        modelPdf.id = bookId

                        //add model to list
                        booksArrayList.add(modelPdf)
                    }
                    //set number of favorite books
                    binding.favoriteBookCountTv.text = "${booksArrayList.size}"

                    //setup adapter
                    adapterPdfFavorite = AdapterPdfFavorite(this@ProfileActivity, booksArrayList)
                    //set adapter to recyclerview
                    binding.favoriteRv.adapter = adapterPdfFavorite
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }
}