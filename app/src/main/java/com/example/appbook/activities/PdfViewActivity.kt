package com.example.appbook.activities
import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.appbook.R
import com.example.appbook.databinding.ActivityPdfViewBinding
import com.google.firebase.database.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.util.*
import kotlin.math.roundToInt
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.example.appbook.utils.LayoutTextStripper
import com.example.appbook.utils.WidgetUtils
import com.github.barteksc.pdfviewer.PDFView
import com.google.firebase.auth.FirebaseAuth

class PdfViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewBinding
    private companion object { private const val TAG = "PDF_VIEW_TAG" }

    private var bookId = ""
    private lateinit var textToSpeech: TextToSpeech
    private var totalPages = 0
    private var currentPage = 0
    private val averageReadingTimePerPage = 1.5 // phút/trang
    private var openedFromWidget = false
    private var pdfDocument: PDDocument? = null
    private val pageCache = mutableMapOf<Int, String>() // cache text theo trang
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init PDFBox
        PDFBoxResourceLoader.init(applicationContext)
        openedFromWidget = intent.getBooleanExtra("fromWidget", false)
        // Init TTS
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale("vi", "VN"))
                if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
                    Log.e(TAG, "❌ Tiếng Việt không được hỗ trợ trên thiết bị")
                }
            }
        }

        // listener TTS
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "✅ Chunk done $utteranceId")
                runOnUiThread {
                    if (!textToSpeech.isSpeaking) {
                        binding.playBtn.setImageResource(R.drawable.ic_play) // 🔹 reset về Play
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    binding.playBtn.setImageResource(R.drawable.ic_play) // 🔹 lỗi thì cũng reset về Play
                }
            }
        })

        bookId = intent.getStringExtra("bookId") ?: ""
        Log.d(TAG, "Received bookId: $bookId")

        loadBookDetails()


        binding.backBtn.setOnClickListener { onBackPressed() }
        binding.playBtn.setOnClickListener { handlePlayPause() }
        initPageJump()
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        binding.btnSpeed.setOnClickListener {
            showSpeedDialog()
        }


        binding.btnTranslate.setOnClickListener {
            getTextForPage(currentPage) { text ->
                if (text.isNotEmpty()) {
                    val fragment = TranslateFragment.newInstance(text)
                    fragment.show(supportFragmentManager, "TranslateFragment")
                } else {
                    Toast.makeText(this, "⚠️ Không có văn bản để dịch!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }
    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "1x", "1.5x", "2x")
        val values = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)

        AlertDialog.Builder(this)
            .setTitle("Chọn tốc độ đọc")
            .setItems(speeds) { _, which ->
                val selectedSpeed = values[which]
                textToSpeech.setSpeechRate(selectedSpeed)
                binding.txtSpeed.text = speeds[which]
                Log.d(TAG, "🔊 Đã chọn tốc độ: ${speeds[which]}")
            }
            .show()
    }

// nhay trang
    private fun initPageJump() {
        // Khi bấm vào TextView chuyển sang chế độ nhập
        binding.toolbarSubtitleTv1.setOnClickListener {
            enterPageEditMode()
        }

        // Bắt sự kiện Enter / Done trên EditText
        binding.pageInput.setOnEditorActionListener { v, actionId, event ->
            val isEnter = (event?.keyCode == KeyEvent.KEYCODE_ENTER)
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnter) {
                exitPageEditModeAndJump()
                true
            } else false
        }

        // Khi mất focus (bấm ra ngoài) cũng xử lý giống Enter
        binding.pageInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && binding.pageInput.visibility == View.VISIBLE) {
                exitPageEditModeAndJump()
            }
        }
    }

    private fun enterPageEditMode() {
        // ẩn TextView, hiện EditText chứa số trang hiện tại
        binding.toolbarSubtitleTv1.visibility = View.GONE
        binding.pageInput.visibility = View.VISIBLE
        binding.pageInput.setText(currentPage.toString())
        binding.pageInput.setSelection(binding.pageInput.text.length)
        binding.pageInput.requestFocus()
        showKeyboard(binding.pageInput)
    }
// nhay trang
    private fun exitPageEditModeAndJump() {
        val input = binding.pageInput.text.toString()
        val pageNum = input.toIntOrNull()

        if (pageNum != null && totalPages > 0 && pageNum in 1..totalPages) {
            // nhảy đến trang (PDFView dùng index từ 0)
            binding.pdfView.jumpTo(pageNum - 1, true)
            currentPage = pageNum
            updateReadingStatus()
        } else {
            Toast.makeText(this, "⚠️ Trang không hợp lệ! (1 - $totalPages)", Toast.LENGTH_SHORT).show()
        }

        // ẩn EditText, hiện lại TextView
        hideKeyboard()
        binding.pageInput.visibility = View.GONE
        binding.toolbarSubtitleTv1.visibility = View.VISIBLE
        // đảm bảo text hiển thị cập nhật
        binding.toolbarSubtitleTv1.text = "Trang $currentPage/$totalPages"
    }

    private fun showKeyboard(view: View) {
        view.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }


    // play pause
    private fun handlePlayPause() {
        if (pdfDocument == null) {
            Log.e(TAG, "⚠️ PDF chưa load xong")
            return
        }

        if (!textToSpeech.isSpeaking) {
            getTextForPage(currentPage) { text ->
                if (text.isNotEmpty()) {
                    speakText(text)
                    binding.playBtn.setImageResource(R.drawable.ic_pause) // 🔹 đổi sang pause khi đang đọc
                } else {
                    Log.e(TAG, "⚠️ Không có text cho trang này")
                }
            }
        } else {
            textToSpeech.stop()
            binding.playBtn.setImageResource(R.drawable.ic_play) // 🔹 stop → quay lại play
        }
    }
// kiem tra ngon ngu
    private fun detectLanguage(text: String): String {
        val vietnamesePattern = Regex("[ăâđêôơưĂÂĐÊÔƠƯáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệóòỏõọốồổỗộớờởỡợúùủũụứừửữựíìỉĩịýỳỷỹỵ]")
        val matches = vietnamesePattern.findAll(text).count()
        val ratio = matches.toDouble() / text.length.toDouble()

        return if (ratio > 0.02) "vi" else "en"
    }

    // luu the loai cuoi cung nguoi dung doc
    private fun saveLastViewedCategory(categoryId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val ref = FirebaseDatabase.getInstance().getReference("Users")
        ref.child(userId).child("lastViewedCategory").setValue(categoryId)
    }

    private fun loadBookDetails() {
        val ref = FirebaseDatabase.getInstance().getReference("Books")
        ref.child(bookId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pdfUrl = snapshot.child("url").value as? String
                val title = snapshot.child("title").value as? String
                val categoryId = snapshot.child("categoryId").value as? String  // 🔹 Lấy thêm cate

                binding.toolbarTitleTv.text = title ?: "Không có tiêu đề"

                // 🔹 Lưu cate cho Recommend
                if (!categoryId.isNullOrEmpty()) {
                    saveBookToHistory(bookId)
                    saveLastViewedCategory(categoryId)
                }

                if (!pdfUrl.isNullOrEmpty()) {
                    Log.d(TAG, "PDF URL: $pdfUrl")
                    loadBookFromCloudinaryUrl(pdfUrl)
                } else {
                    Log.e(TAG, "PDF URL is empty or null")
                    binding.progressBar.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "loadBookDetails: ${error.message}")
                binding.progressBar.visibility = View.GONE
            }
        })
    }
// luu cac sach nguoi dung da doc
    private fun saveBookToHistory(bookId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val ref = FirebaseDatabase.getInstance().getReference("Users")
            .child(user.uid)
            .child("history")

        ref.child(bookId).setValue(true)
    }

    // ----------------- LOAD PDF -----------------
    private fun loadBookFromCloudinaryUrl(pdfUrl: String) {
        val request = Request.Builder().url(pdfUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to load PDF: ${e.message}")
                runOnUiThread { binding.progressBar.visibility = View.GONE }
            }

            override fun onResponse(call: Call, response: Response) {
                val pdfBytes = response.body?.bytes()
                if (!response.isSuccessful || pdfBytes == null) {
                    Log.e(TAG, "Response error / Empty PDF data")
                    runOnUiThread { binding.progressBar.visibility = View.GONE }
                    return
                }

                runOnUiThread {
                    binding.pdfView.fromBytes(pdfBytes)
                        .defaultPage(0)
                        .enableAnnotationRendering(true)
                        .swipeHorizontal(false)
                        .onLoad { pageCount ->
                            totalPages = pageCount
                            currentPage = 1
                            updateReadingStatus()
                            binding.progressBar.visibility = View.GONE
                            checkLastReadingPage(bookId, totalPages)

                            // 🟡 Resume từ widget: xử lý ở đây sau khi PDF đã load xong
                            val resumePage = intent.getIntExtra("resumePage", -1)
                            if (resumePage > 0 && resumePage <= totalPages) {
                                Log.d(TAG, "⏩ Resume to page $resumePage")
                                binding.pdfView.jumpTo(resumePage - 1, true)
                                currentPage = resumePage
                                updateReadingStatus()

                                // 🟢 Hiển thị chào mừng nếu mở từ widget
                                if (openedFromWidget) {
                                    Toast.makeText(
                                        this@PdfViewActivity,
                                        "👋 Chào mừng bạn quay lại, đọc tiếp nhé!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        .onPageChange { page, pageCount ->
                            currentPage = page + 1
                            totalPages = pageCount
                            updateReadingStatus()
                            saveReadingProgress(bookId, currentPage)
                            WidgetUtils.updateContinueReadingWidget(this@PdfViewActivity)
                        }
                        .load()
                }

                try {
                    pdfDocument = PDDocument.load(pdfBytes)
                    Log.d(TAG, "✅ PDDocument loaded, ready for text extraction")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading PDDocument", e)
                }
            }
        })
    }


    // kiem tra tang nguoi dung da doc toi va nhay
    private fun checkLastReadingPage(bookId: String, totalPages: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val userId = user.uid
        val ref = FirebaseDatabase.getInstance().getReference("UserReadingProgress")
        ref.child(userId).child(bookId).get().addOnSuccessListener { snapshot ->
            val lastPage = snapshot.getValue(Int::class.java) ?: 1

            if (openedFromWidget) {
                // 🟢 Nếu mở từ widget → bỏ qua dialog
                Toast.makeText(
                    this,
                    "👋 Chào mừng quay lại, tiếp tục đọc nhé!",
                    Toast.LENGTH_SHORT
                ).show()
                return@addOnSuccessListener
            }

            // 👉 Nếu không mở từ widget → xử lý như bình thường
            if (lastPage > 1 && lastPage in 1..totalPages) {
                AlertDialog.Builder(this)
                    .setTitle("Tiếp tục đọc?")
                    .setMessage("Bạn đã đọc tới trang $lastPage. Bạn muốn tiếp tục không?")
                    .setPositiveButton("Tiếp tục") { _, _ ->
                        binding.pdfView.jumpTo(lastPage - 1, true)
                    }
                    .setNegativeButton("Đọc từ đầu") { _, _ ->
                        binding.pdfView.jumpTo(0, true)
                    }
                    .show()
            }
        }
    }


    // luu trang nguoi dung da doc toi
    private fun saveReadingProgress(bookId: String, page: Int) {
    val user = FirebaseAuth.getInstance().currentUser ?: return

    val ref = FirebaseDatabase.getInstance().getReference("UserReadingProgress")
    ref.child(user.uid).child(bookId).setValue(page)

    val title = binding.toolbarTitleTv.text.toString()
    saveLastOpenedBook(bookId, title, page, totalPages)
}

    private fun saveLastOpenedBook(bookId: String, title: String, page: Int, totalPages: Int) {
        val prefs = getSharedPreferences("ReadingPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("lastBookId", bookId)
            .putString("lastBookTitle", title)
            .putInt("lastBookPage", page)
            .putInt("lastBookTotal", totalPages)
            .apply()
    }


    // lay text ra
    private fun getTextForPage(page: Int, callback: (String) -> Unit) {
        pageCache[page]?.let { cached ->
            callback(cached)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stripper = LayoutTextStripper().apply {
                    startPage = page
                    endPage = page
                }
                val text = pdfDocument?.let { stripper.getText(it).trim() } ?: ""
                pageCache[page] = text
                withContext(Dispatchers.Main) { callback(text) }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting text for page $page", e)
                withContext(Dispatchers.Main) { callback("") }
            }
        }
    }

    // ----------------- TTS -----------------
    private fun speakText(text: String) {
        val lang = detectLanguage(text)
        val locale = if (lang == "vi") Locale("vi", "VN") else Locale.US

        val result = textToSpeech.setLanguage(locale)
        if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
            Log.e(TAG, "❌ Ngôn ngữ $lang không được hỗ trợ")
            Toast.makeText(this, "Không hỗ trợ ngôn ngữ $lang", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "🗣️ Bắt đầu đọc trang bằng ngôn ngữ: $lang")

        val chunkSize = TextToSpeech.getMaxSpeechInputLength()
        var start = 0
        var chunkIndex = 0
        while (start < text.length) {
            val end = (start + chunkSize).coerceAtMost(text.length)
            val chunk = text.substring(start, end)
            textToSpeech.speak(chunk, TextToSpeech.QUEUE_ADD, null, "chunk_$chunkIndex")
            start = end
            chunkIndex++
        }
    }

    //dịch
    fun translateText(text: String, callback: (String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)   // Ngôn ngữ gốc
            .setTargetLanguage(TranslateLanguage.VIETNAMESE) // Ngôn ngữ đích
            .build()

        val translator = Translation.getClient(options)

        // Tải model dịch nếu chưa có
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        callback(translatedText)
                    }
                    .addOnFailureListener { e ->
                        callback("❌ Lỗi dịch: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                callback("❌ Không tải được model: ${e.message}")
            }
    }
    //hien thi thoi gian doc con lai
    private fun updateReadingStatus() {
        binding.toolbarSubtitleTv1.text = "Trang $currentPage/$totalPages"
        val pagesLeft = totalPages - currentPage
        val estimatedMinutes = (pagesLeft * averageReadingTimePerPage).roundToInt()
        binding.toolbarSubtitleTv2.text = "~${estimatedMinutes} phút còn lại"
    }
// khi ket thuc giai phong bo nho
    override fun onDestroy() {
    WidgetUtils.updateContinueReadingWidget(this)
        pdfDocument?.close()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}
