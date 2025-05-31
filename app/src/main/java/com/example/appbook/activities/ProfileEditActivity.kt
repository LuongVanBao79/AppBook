package com.example.appbook.activities

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ContentInfo
import android.view.Menu
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.activities.ProfileActivity
import com.example.appbook.databinding.ActivityProfileEditBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.cloudinary.android.MediaManager
import com.cloudinary.utils.ObjectUtils
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.FirebaseStorage
import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.util.SimpleTimeZone
import java.util.UUID


class ProfileEditActivity : AppCompatActivity() {

    //view binding
    private lateinit var binding: ActivityProfileEditBinding

    //firebase auth
    private lateinit var firebaseAuth: FirebaseAuth

    //image uri (which we will pick)
    private var imageUri: Uri? = null

    //progress dialog
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //setup progress dialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please wait")
        progressDialog.setCanceledOnTouchOutside(false)

        firebaseAuth = FirebaseAuth.getInstance()
        loadUserInfo()

        //handle click, go back
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        //handle click, pick image from camera/gallery
        binding.profileIv.setOnClickListener {
            showImageAttachMenu()
        }

        //handle click, begin update profile
        binding.updateBtn.setOnClickListener {
            validateData()
        }
    }

    private var name= ""
    private fun validateData() {
        //get data
        name = binding.nameEt.text.toString().trim()

        //validate data
        if(name.isEmpty()){
            //name not entered
            Toast.makeText(this, "Enter name", Toast.LENGTH_SHORT).show()
        }
        else{
            //name is entered
            if(imageUri == null){
                //need to update without image
                updateProfile("")
            }
            else{
                //need to update with image
                uploadImage()
            }
        }
    }

    /*private fun uploadImage() {
        progressDialog.setMessage("Uploading profile image")
        progressDialog.show()

        //image path and name, use uid to replace previous
        val filePathAndName = "ProfileImages/" + firebaseAuth.uid
        //storage reference
        val reference = FirebaseStorage.getInstance().getReference(filePathAndName)
        reference.putFile(imageUri!!)
            .addOnSuccessListener { taskSnapshot ->
                //image uploaded, get url of uploaded image
                val uriTask: Task<Uri> = taskSnapshot.storage.downloadUrl
                while(!uriTask.isSuccessful);
                val uploadedImageUrl = "${uriTask.result}"

                updateProfile(uploadedImageUrl)
            }
            .addOnFailureListener { e->
                //faild to upload image
                progressDialog.dismiss()
                Toast.makeText(this, "Faild to upload image due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }*/

    private fun uploadImage() {
        progressDialog.setMessage("Uploading profile image")
        progressDialog.show()

        val inputStream = contentResolver.openInputStream(imageUri!!)
        if (inputStream == null) {
            progressDialog.dismiss()
            Toast.makeText(this, "Cannot open image", Toast.LENGTH_SHORT).show()
            return
        }

        val requestId = UUID.randomUUID().toString() // unique ID for the image

        Thread {
            try {
                val cloudinary = MediaManager.get().cloudinary
                val uploadResult = cloudinary.uploader().upload(inputStream, ObjectUtils.asMap(
                    "folder", "ProfileImages/",
                    "public_id", firebaseAuth.uid, // use UID to overwrite previous
                    "resource_type", "image"
                ))

                val uploadedImageUrl = uploadResult["secure_url"] as String

                runOnUiThread {
                    progressDialog.dismiss()
                    updateProfile(uploadedImageUrl)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Failed to upload image due to ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }


    private fun updateProfile(uploadedImageUrl: String) {
        progressDialog.setMessage("Updating profile...")

        //setup info to update to db
        val hashmap: HashMap<String, Any> = HashMap()
        hashmap["name"] = "$name"
        if(imageUri != null){
            hashmap["profileImage"] = uploadedImageUrl
        }

        //update to db
        val reference = FirebaseDatabase.getInstance().getReference("Users")
        reference.child(firebaseAuth.uid!!)
            .updateChildren(hashmap)
            .addOnSuccessListener {
                //profile updated
                progressDialog.dismiss()
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {e->
                //failed to upload image
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to update profile due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserInfo() {
        //db reference to load user info
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(firebaseAuth.uid!!)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //get user info
                    val name = "${snapshot.child("name").value}"
                    val profileImage = "${snapshot.child("profileImage").value}"
                    val timestamp = "${snapshot.child("timestamp").value}"

                    //convert timestamp to peroper date format
                    val formattedDate = MyApplication.formatTimeStamp(timestamp.toLong())

                    //set data
                    binding.nameEt.setText(name)

                    //set image
                    try{
                        Glide.with(this@ProfileEditActivity)
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

    private fun showImageAttachMenu(){
        /*show popup menu with options Camera, Gallery to pick image*/

        //setup popup menu
        val popupMenu = PopupMenu(this, binding.profileIv)
        popupMenu.menu.add(Menu.NONE, 0, 0, "Camera")
        popupMenu.menu.add(Menu.NONE, 1, 1, "Gallery")
        popupMenu.show()

        //handle popup menu item click
        popupMenu.setOnMenuItemClickListener { item ->
            //get id of clicked item
            val id = item.itemId
            if(id == 0){
                //Camera clicked
                pickImageCamera()
            }
            else if(id == 1){
                //Gallery clicked
                pickImageGallery()
            }
            true
        }
    }

    private fun ProfileEditActivity.pickImageCamera() {
        //intent to pick image from camera
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "Temp_Title")
        values.put(MediaStore.Images.Media.DESCRIPTION, "Temp_Description")

        imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraActivityResultLauncher.launch(intent)
    }

    private fun ProfileEditActivity.pickImageGallery() {
        //intent to pick image from gallery
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryActivityResultLauncher.launch(intent)
    }

    //used to handle result of camera intent (new way in replacement of startactivityforresults)
    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult> { result ->
            //get uri of image
            if(result.resultCode == Activity.RESULT_OK){
                val data = result.data
                //set to imageview
                binding.profileIv.setImageURI(imageUri)
            }
            else{
                //cancelled
                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    )

    //used to handle result of gallery intent (new way in replacement of startactivityforresults)
    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult>{ result ->
            //get uri of image
            if(result.resultCode == Activity.RESULT_OK){
                val data = result.data
                imageUri = data!!.data
                //set to imageview
                binding.profileIv.setImageURI(imageUri)
            }
            else{
                //cancelled
                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    )
}





