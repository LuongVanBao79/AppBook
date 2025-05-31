package com.example.appbook.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.appbook.filters.FilterPdfAdmin
import com.example.appbook.models.ModelPdf
import com.example.appbook.MyApplication
import com.example.appbook.activities.PdfDetailActivity
import com.example.appbook.activities.PdfEditActivity
import com.example.appbook.databinding.RowPdfAdminBinding

class AdapterPdfAdmin : RecyclerView.Adapter<AdapterPdfAdmin.HolderPdfAdmin>, Filterable {

    //Context
    private var context: Context
    //Arraylist to hold pdfs
    public var pdfArrayList: ArrayList<ModelPdf>
    private val filterList: ArrayList<ModelPdf>

    //view binding
    private lateinit var binding: RowPdfAdminBinding

    //filter object
    private var filter: FilterPdfAdmin? = null

    //Constructor
    constructor(context: Context, pdfArrayList: ArrayList<ModelPdf>) : super() {
        this.context = context
        this.pdfArrayList = pdfArrayList
        this.filterList = pdfArrayList
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HolderPdfAdmin {
        //bind/inflate layout row_pdf_admin.xml
        binding = RowPdfAdminBinding.inflate(LayoutInflater.from(context), parent, false)

        return HolderPdfAdmin(binding.root)
    }

    override fun onBindViewHolder(
        holder: HolderPdfAdmin,
        position: Int
    ) {
        //Get Data, set data, handle click etc
        // get data
        val model = pdfArrayList[position]
        val pdfId = model.id
        val categoryId = model.categoryId
        val title = model.title
        val description = model.description
        val pdfUrl = model.url
        val timestamp = model.timestamp
        // convert timestamp to dd/MM/yyy format
        val formattedDate = MyApplication.Companion.formatTimeStamp(timestamp)

        //set data
        holder.titleTv.text = title
        holder.descriptionTv.text = description
        holder.dateTv.text = formattedDate

        //load further details like category, pdf from url, pdf size

        //load category
        MyApplication.Companion.loadCategory(categoryId, holder.categoryTv)
        //we don't need page number here, pas null for page number || load pdf thumbnail
        MyApplication.Companion.loadPdfFromUrlSinglePage(pdfUrl, title, holder.pdfView, holder.progressBar, null)

        //load pdfSize
        MyApplication.Companion.loadPdfSizeFromCloudinary(pdfUrl, holder.sizeTv)

        //handle click, show dialog with options 1 edit 2 delete
        holder.moreBtn.setOnClickListener {
            moreOptionsDialog(model, holder)
        }

        //handle item click, open PdfDetailActivity
        holder.itemView.setOnClickListener {
            //intent with book id
            val intent = Intent(context, PdfDetailActivity::class.java)
            intent.putExtra("bookId", pdfId)// will be used to load book details
            context.startActivity(intent)
        }
    }

    private fun moreOptionsDialog(
        model: ModelPdf,
        holder: HolderPdfAdmin
    ) {
        //get id, url, title of book
        val bookId = model.id
        val bookUrl = model.url
        val bookTitle = model.title

        //options to show in dialog
        val options = arrayOf("Edit", "Delete")

        //alert dialog
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Choose Option")
            .setItems(options){ dialog, position ->
                //hand item click
                if(position == 0){
                    //edit is clicked
                    var intent = Intent(context, PdfEditActivity::class.java)
                    intent.putExtra("bookId", bookId) //passed bookId, will be used to edit the book
                    context.startActivity(intent)
                }
                else if(position == 1){
                    //delete is clicked

                    MyApplication.Companion.deleteBook(context, bookId, bookUrl, bookTitle, "dak4ks7mx", "643815841764554", "bQzV_ZZp3F9eyonEWhnn9vcqQts")
                }
            }
            .show()
    }


    override fun getItemCount(): Int {
        return pdfArrayList.size //items count
    }

    override fun getFilter(): Filter? {
        if(filter == null){
            filter = FilterPdfAdmin(this, filterList)
        }
        return filter as FilterPdfAdmin
    }

    //View holder class for row_pdf_admin.xml
    inner class HolderPdfAdmin(itemView: View) : RecyclerView.ViewHolder(itemView){
        // UI views of row_pdf_admin.xml
        val pdfView = binding.pdfView
        val progressBar = binding.progressBar
        val titleTv = binding.titleTv
        val descriptionTv = binding.descriptionTv
        val categoryTv = binding.categoryTv
        val sizeTv = binding.sizeTv
        val dateTv = binding.dateTv
        val moreBtn = binding.moreBtn
    }
}