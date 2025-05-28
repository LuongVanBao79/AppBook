package com.example.appbook

import android.app.Application
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.renderscript.Sampler
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.cloudinary.android.MediaManager
import com.example.appbook.databinding.ActivityPdfAddBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.utils.ObjectUtils
import java.io.File
import java.io.FileOutputStream


class PdfAddActivity : AppCompatActivity() {

    // setup view binding activity_pdf_add --> ActivityPdfAddBinding
    private lateinit var binding: ActivityPdfAddBinding

    //firebase auth
    private lateinit var firebaseAuth: FirebaseAuth

    //progress dialog (show while uploading pdf)
    private lateinit var progressDialog: ProgressDialog

    //arraylist to hold pdf categories
    private lateinit var categoryArrayList: ArrayList<ModelCategory>

    // uri of picked pdf
    private var pdfUri: Uri? = null

    //TAG
    private val TAG = "PDF_ADD_TAG"



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfAddBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        //init cloudinary
//        val config: HashMap<String, String> = HashMap()
//        config["cloud_name"] = "dak4ks7mx"
//        config["api_key"] = "643815841764554"
//        config["api_secret"] = "bQzV_ZZp3F9eyonEWhnn9vcqQts"
//        MediaManager.init(this, config)


        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
        loadPdfCategories()

        //setup progress dialog
        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Vui Lòng Đợi...")
        progressDialog.setCanceledOnTouchOutside(false)

        //handle click, goback
        binding.backBtn.setOnClickListener {
            onBackPressed()
        }

        //handle click, show category pick dialog
        binding.categoryTv.setOnClickListener {
            categoryPickDialog()
        }

        //handle click, show category pick dialog
        binding.attachPdfBtn.setOnClickListener {
            pdfPickIntent()
        }

        //handle click, start uploading pdf/book
        binding.submitBtn.setOnClickListener {
            //1 validate data
            //2 upload pdf to firebase storage
            //3 get url of uploaded pdf
            //4 upload pdf info to firebase db
            validateData()
        }
    }

    private var title = ""
    private var description = ""
    private var category = ""


    private fun validateData() {
        //1 validate data
        Log.d(TAG, "validateData: validating data")

        //get data
        title = binding.titleEt.text.toString().trim()
        description = binding.descriptionEt.text.toString().trim()
        category = binding.categoryTv.text.toString().trim()

        //validate data
        if(title.isEmpty()){
            Toast.makeText(this, "Enter Title...", Toast.LENGTH_SHORT).show()
        }
        else if(description.isEmpty()){
            Toast.makeText(this, "Enter Description...", Toast.LENGTH_SHORT).show()
        }
        else if(category.isEmpty()){
            Toast.makeText(this, "Enter Category...", Toast.LENGTH_SHORT).show()
        }
        else if(pdfUri == null){
            Toast.makeText(this, "Pick PDF...", Toast.LENGTH_SHORT).show()
        }
        else {
            //data validated, begin upload
            uploadPdfToCloudinary()
        }
    }

    private fun uploadPdfToCloudinary() {
        Log.d(TAG, "uploadPdfToCloudinary: uploading to Cloudinary...")

        progressDialog.setMessage("Đang tải lên Cloudinary...")
        progressDialog.show()

        val timestamp = System.currentTimeMillis()

        // Tạo file tạm từ uri (vì Cloudinary có thể không hiểu rõ uri content://...)
        try {
            val inputStream = contentResolver.openInputStream(pdfUri!!)
            val tempFile = File.createTempFile("upload_pdf_temp", ".pdf", cacheDir)
            val outputStream = FileOutputStream(tempFile)

            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            // Upload bằng File path
            MediaManager.get().upload(tempFile.absolutePath)
                .option("resource_type", "raw")
                .option("public_id", "Books/$timestamp")
                .option("type", "upload")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {
                        Log.d(TAG, "onStart: Upload bắt đầu")
                    }

                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                        Log.d(TAG, "onProgress: $bytes/$totalBytes")
                    }

                    override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                        Log.d(TAG, "onSuccess: Upload thành công $resultData")
                        val uploadedPdfUrl = resultData?.get("secure_url") as? String
                        if (uploadedPdfUrl != null) {
                            uploadPdfInfoToDb(uploadedPdfUrl, timestamp)
                        } else {
                            Toast.makeText(this@PdfAddActivity, "Không lấy được URL", Toast.LENGTH_SHORT).show()

                        }

                        tempFile.delete()
                        progressDialog.dismiss()
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        progressDialog.dismiss()
                        Toast.makeText(this@PdfAddActivity, "Upload thất bại: ${error?.description}", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Upload error: ${error?.description}")
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                        Log.d(TAG, "onReschedule: $error")
                    }
                })
                .dispatch()

        } catch (e: Exception) {
            progressDialog.dismiss()
            Toast.makeText(this, "Lỗi khi xử lý file PDF: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Lỗi khi xử lý file PDF", e)
        }


    }


    /*private fun uploadPdfToCloudinary() {
        Log.d(TAG, "uploadPdfToCloudinary: uploading to Cloudinary...")

        progressDialog.setMessage("Uploading PDF to Cloudinary...")
        progressDialog.show()

        val timestamp = System.currentTimeMillis()

        // Bắt đầu upload
        MediaManager.get().upload(pdfUri)
            .option("resource_type", "raw") // PDF là file "raw", không phải image/video
            .option("public_id", "Books/$timestamp")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d(TAG, "onStart: Upload bắt đầu")
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                    Log.d(TAG, "onProgress: $bytes/$totalBytes")
                }

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    Log.d(TAG, "onSuccess: Upload thành công $resultData")
                    val uploadedPdfUrl = resultData?.get("secure_url") as? String
                    if (uploadedPdfUrl != null) {
                        uploadPdfInfoToDb(uploadedPdfUrl, timestamp)
                    } else {
                        Toast.makeText(this@PdfAddActivity, "Không lấy được URL", Toast.LENGTH_SHORT).show()
                        progressDialog.dismiss()
                    }
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    progressDialog.dismiss()
                    Toast.makeText(this@PdfAddActivity, "Upload thất bại: ${error?.description}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Upload error: ${error?.description}")
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {
                    Log.d(TAG, "onReschedule: $error")
                }
            })
            .dispatch()
    }*/

    /*private fun uploadPdfToStorage() {
        // upload pdf to firebase storage
        Log.d(TAG, "uploadPdfToStorage: uploading to storage...")

        //show progress dialog
        progressDialog.setMessage("Uploading PDF...")
        progressDialog.show()

        //timestamp
        val timestamp = System.currentTimeMillis()

        //path of pdf in firebase storage
        val filePathAndName = "Books/$timestamp"
        //storage reference
        val storageReference = FirebaseStorage.getInstance().getReference(filePathAndName)
        storageReference.putFile(pdfUri!!)
            .addOnSuccessListener { taskSnapshot ->
                Log.d(TAG, "uploadPdfToStorage: PDF uploaded now getting url...")
                //3 get url of uploaded pdf
                val uriTask: Task<Uri> = taskSnapshot.storage.downloadUrl
                while (!uriTask.isSuccessful);
                val uploadedPdfUrl = "${uriTask.result}"

                uploadPdfInfoToDb(uploadedPdfUrl, timestamp)
            }
            .addOnFailureListener {e->
                Log.d(TAG, "uploadPdfToStorage: failed to upload due to ${e.message}")
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to upload due to ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }*/

    private fun PdfAddActivity.uploadPdfInfoToDb(uploadedPdfUrl: String, timestamp: Long) {
        //4 upload pdf info to firebase db
        Log.d(TAG, "uploadPdfInfoToDb: uploading to db")
        progressDialog.setMessage("Uploading pdf info...")

        //uid of current user
        val uid = firebaseAuth.uid

        //setup data to upload
        val hashMap: HashMap<String, Any> = HashMap()
        hashMap["uid"] = "$uid"
        hashMap["id"] = "$timestamp"
        hashMap["title"] = "$title"
        hashMap["description"] = "$description"
        hashMap["categoryId"] = "$selectedCategoryId"
        hashMap["url"] = "$uploadedPdfUrl"
        hashMap["timestamp"] = timestamp
        hashMap["viewsCount"] = 0
        hashMap["downloadsCount"] = 0

        //df reference db > books > bookid > (book Info)
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child("$timestamp")
            .setValue(hashMap)
            .addOnSuccessListener {
                Log.d(TAG, "uploadPdfInfoToDb: uploaded to db")
                progressDialog.dismiss()
                Toast.makeText(this, "Uploaded...", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e->
                Log.d(TAG, "uploadPdfInfoDb: failed to upload due to ${e.message}")
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to upload due to ${e.message}", Toast.LENGTH_SHORT).show()
                pdfUri = null
            }

    }



    private fun loadPdfCategories() {
        Log.d(TAG, "loadPdfCategories: Loading pdf categories")
        //init arraylist
        categoryArrayList = ArrayList()

        //db reference to load categories DF > Categories
        val ref = FirebaseDatabase.getInstance().getReference("Categories")
        ref.addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                //clear list before adding data
                categoryArrayList.clear()
                for(ds in snapshot.children){
                    //get data
                    val model = ds.getValue(ModelCategory::class.java)
                    //add to arraylist
                    categoryArrayList.add(model!!)
                    Log.d(TAG, "onDataChange: ${model.category}")
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    private var selectedCategoryTitle = ""
    private var selectedCategoryId = ""

    private fun categoryPickDialog(){
        Log.d(TAG, "categoryPickDialog: Showing pdf category pick dialog")

        //get string array of categories from arraylist
        val categoriesArray = arrayOfNulls<String>(categoryArrayList.size)
        for(i in categoryArrayList.indices){
            categoriesArray[i] = categoryArrayList[i].category
        }

        //alert dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Chọn Danh Mục")
            .setItems(categoriesArray){dialog, which ->
                //handle item click
                //get clicked item
                selectedCategoryTitle = categoryArrayList[which].category
                selectedCategoryId = categoryArrayList[which].id
                //set category to textview
                binding.categoryTv.text = selectedCategoryTitle

                Log.d(TAG, "categoryPickDialog: Selected Category ID: $selectedCategoryId")
                Log.d(TAG, "categoryPickDialog: Selected Category Title: $selectedCategoryTitle")
            }
            .show()
    }

    private fun pdfPickIntent(){
        Log.d(TAG, "pdfPickIntent: starting pdf pick intent")

        val intent = Intent()
        intent.type = "application/pdf"
        intent.action = Intent.ACTION_GET_CONTENT
        pdfActivityResultLauncher.launch(intent)
    }

    val pdfActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ActivityResultCallback<ActivityResult>{ result ->
            if(result.resultCode == RESULT_OK){
                Log.d(TAG, "PDF Picked ")
                pdfUri = result.data!!.data
            }
            else{
                Log.d(TAG, "PDF Picked cancelled ")
                Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    )


}




