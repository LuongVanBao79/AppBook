package com.example.appbook.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.ListMenuItemView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.appbook.MyApplication
import com.example.appbook.R
import com.example.appbook.databinding.RowCommentBinding
import com.example.appbook.models.ModelComment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.concurrent.fixedRateTimer

class AdapterComment: RecyclerView.Adapter<AdapterComment.HolderComment> {
    //context
    val context: Context

    //arraylist to hold comments
    val commentArrayList: ArrayList<ModelComment>

    //view binding row_comment.xml => RowCommentBinding
    private lateinit var binding: RowCommentBinding

    //firebase auth
    private lateinit var firebaseAuth: FirebaseAuth


    //constructor
    constructor(context: Context, commentArrayList: ArrayList<ModelComment>) : super() {
        this.context = context
        this.commentArrayList = commentArrayList

        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HolderComment {
        //bind/inflate row_comment.xml
        binding = RowCommentBinding.inflate(LayoutInflater.from(context), parent, false)
        return HolderComment(binding.root)
    }

    override fun onBindViewHolder(holder: HolderComment, position: Int) {
        /*Get data, set data, handle click etc ...*/
        // get data
        val model = commentArrayList[position]

        val id = model.id
        val bookId = model.bookId
        val comment = model.comment
        val uid = model.uid
        val timestamp = model.timestamp

        //format timestamp
        val date = MyApplication.formatTimeStamp(timestamp.toLong())

        //set data
        holder.dateTv.text = date
        holder.commentTv.text = comment

        //we don't have user name, profile picture but we have user uid, so we will load using that uid
        loadUserDetails(model, holder)

        //handle click, show dialog to delete comment
        holder.itemView.setOnClickListener {
            /*Requirements to delete a comment
            * 1 User must be logged in
            * 2 uid in comment (to be deleted) must be same as uid of current user i.e. user can delete only his own comment*/
            if(firebaseAuth.currentUser !=null && firebaseAuth.uid == uid){
                deleteCommmentDialog(model, holder)
            }
        }
    }

    private fun deleteCommmentDialog(model: ModelComment, holder: HolderComment) {
        //alert dialog
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment?")
            .setPositiveButton("DELETE") { d, e ->
                val bookId = model.bookId
                val commentId = model.id

                //delete comment
                val ref = FirebaseDatabase.getInstance().getReference("Books")
                ref.child(bookId).child("Comments").child(commentId)
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Comment deleted...", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e->
                        //failed to delete
                        Toast.makeText(context, "Failed to delete comment due to ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("CANCEL") { d, e ->
                d.dismiss()
            }
            .show()
    }

    private fun loadUserDetails(model: ModelComment, holder: AdapterComment.HolderComment) {
        val uid = model.uid
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    //get name, profile image
                    val name = "${snapshot.child("name").value}"
                    val profileImage = "${snapshot.child("profileImage").value}"

                    //set data
                    holder.nameTv.text = name
                    try {
                        Glide.with(context)
                            .load(profileImage)
                            .placeholder(R.drawable.ic_person_gray)
                            .into(holder.profileIv)
                    }
                    catch (e: Exception){
                        //in case of exception duw to image is empty or null or other reason, set default image
                        holder.profileIv.setImageResource(R.drawable.ic_person_gray)
                    }
                }
                override fun onCancelled(error: DatabaseError) {

                }
            })
    }


    override fun getItemCount(): Int {
        return commentArrayList.size //return list size || number of items in list
    }

    /*ViewHolder class for row_comment.xml*/
    inner class HolderComment(itemView: View): RecyclerView.ViewHolder(itemView){
        //init ui views of row_comment.xml
        val profileIv = binding.profileIv
        val nameTv = binding.nameTv
        val dateTv = binding.dateTv
        val commentTv = binding.commentTv
    }
}