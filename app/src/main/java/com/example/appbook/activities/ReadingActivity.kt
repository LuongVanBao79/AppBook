package com.example.appbook.activities

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appbook.ContinueReadingWidget
import com.example.appbook.R
import com.example.appbook.adapters.ChapterAdapter
import com.example.appbook.databinding.ActivityReadingBinding
import com.example.appbook.models.ModelChapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil

class ReadingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReadingBinding
    private companion object { private const val TAG = "READING_TAG" }

    // Dữ liệu sách
    private var bookId = ""
    private var currentChapterId = ""
    private var chapterList = ArrayList<ModelChapter>()
    private var currentIndex = -1

    // MỚI: Biến lưu vị trí cuộn cần khôi phục
    private var savedScrollY = 0

    // TTS & Dịch
    private lateinit var textToSpeech: TextToSpeech
    private var ttsSpeed = 1.0f

    // Cấu hình giao diện (Lưu tạm thời)
    private var currentTextSize = 18f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Nhận dữ liệu
        bookId = intent.getStringExtra("BOOK_ID") ?: ""
        // Nhận chapterId từ Intent (nếu có), nhưng lát nữa ta sẽ ưu tiên lấy từ Firebase Progress
        currentChapterId = intent.getStringExtra("CHAPTER_ID") ?: ""

        if (bookId.isEmpty()) {
            Toast.makeText(this, "Lỗi: Không tìm thấy sách", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Khởi tạo
        setupTTS()
        setupScrollListener()

        // Load danh sách chương, sau đó sẽ tự động check progress để load nội dung
        loadAllChapters()

        // 3. Sự kiện Toolbar
        binding.backBtn.setOnClickListener { finish() }

        // Mở 3 Menu BottomSheet
        binding.btnAppearance.setOnClickListener { showAppearanceDialog() }
        binding.btnTools.setOnClickListener { showToolsDialog() }
        binding.btnToc.setOnClickListener { showTocDialog() }
    }

    // =========================================================================
    // PHẦN 1: LOGIC LƯU VÀ KHÔI PHỤC TIẾN ĐỘ (QUAN TRỌNG)
    // =========================================================================

    /**
     * MỚI: Hàm lưu tiến độ bao gồm cả ChapterId và vị trí cuộn (ScrollY)
     */
    private fun saveReadingProgress() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        // Lấy vị trí cuộn hiện tại
        val currentScrollY = binding.scrollView.scrollY

        val hashMap = HashMap<String, Any>()
        hashMap["chapterId"] = currentChapterId
        hashMap["scrollY"] = currentScrollY // Lưu vị trí pixel dọc
        hashMap["timestamp"] = System.currentTimeMillis()

        // Lưu vào node UserReadingProgress
        FirebaseDatabase.getInstance().getReference("UserReadingProgress")
            .child(user.uid).child(bookId).setValue(hashMap)

        // Lưu vào lịch sử đọc
        FirebaseDatabase.getInstance().getReference("Users")
            .child(user.uid).child("history").child(bookId).setValue(true)

        saveToWidgetPrefs()
    }

    /**
     * MỚI: Gọi hàm lưu khi người dùng thoát app hoặc tắt màn hình
     */
    override fun onPause() {
        super.onPause()
        saveReadingProgress()
    }

    // =========================================================================
    // PHẦN 2: LOGIC TẢI DỮ LIỆU & ĐIỀU HƯỚNG
    // =========================================================================

    private fun loadAllChapters() {
        binding.progressBar.visibility = View.VISIBLE
        val ref = FirebaseDatabase.getInstance().getReference("Chapters").child(bookId)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                chapterList.clear()
                for (ds in snapshot.children) {
                    ds.getValue(ModelChapter::class.java)?.let { chapterList.add(it) }
                }
                chapterList.sortBy { it.timestamp }

                if (chapterList.isNotEmpty()) {
                    // Sau khi có list chương, ta đi kiểm tra xem User đang đọc dở ở đâu
                    fetchUserProgress()
                } else {
                    binding.tvContent.text = "Sách này chưa có nội dung."
                    binding.progressBar.visibility = View.GONE
                }
            }
            override fun onCancelled(error: DatabaseError) { binding.progressBar.visibility = View.GONE }
        })
    }

    /**
     * MỚI: Hàm lấy dữ liệu tiến độ từ Firebase về
     */
    private fun fetchUserProgress() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Chưa đăng nhập -> Load chương đầu hoặc chương từ Intent
            initDefaultChapter()
            return
        }

        val ref = FirebaseDatabase.getInstance().getReference("UserReadingProgress")
            .child(user.uid).child(bookId)

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Lấy chapterId và scrollY đã lưu
                    val savedChapterId = snapshot.child("chapterId").value.toString()
                    val savedY = snapshot.child("scrollY").value.toString().toIntOrNull() ?: 0

                    // Cập nhật biến toàn cục
                    currentChapterId = savedChapterId
                    savedScrollY = savedY

                    // Tìm index của chương đã lưu
                    currentIndex = chapterList.indexOfFirst { it.id == currentChapterId }
                    if (currentIndex == -1) currentIndex = 0

                    // Load nội dung và yêu cầu restore scroll
                    loadChapterContent(chapterList[currentIndex], restoreScroll = true)
                } else {
                    // Không có dữ liệu lưu -> Load mặc định
                    initDefaultChapter()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                initDefaultChapter()
            }
        })
    }

    private fun initDefaultChapter() {
        // Nếu có intent truyền vào thì dùng, ko thì dùng chương 0
        currentIndex = chapterList.indexOfFirst { it.id == currentChapterId }
        if (currentIndex == -1 && chapterList.isNotEmpty()) currentIndex = 0

        if (currentIndex in chapterList.indices) {
            loadChapterContent(chapterList[currentIndex], restoreScroll = false)
        }
    }

    /**
     * SỬA: Thêm tham số restoreScroll để biết có cần cuộn xuống vị trí cũ không
     */
    private fun loadChapterContent(chapter: ModelChapter, restoreScroll: Boolean) {
        currentChapterId = chapter.id
        currentIndex = chapterList.indexOf(chapter)

        binding.tvChapterTitle.text = chapter.title
        binding.tvContent.text = chapter.content

        binding.progressBar.visibility = View.GONE

        // MỚI: Logic cuộn thông minh
        if (restoreScroll) {
            // Phải dùng post để đợi TextView render xong nội dung mới cuộn được
            binding.scrollView.post {
                binding.scrollView.scrollTo(0, savedScrollY)
                // Reset lại savedScrollY sau khi dùng xong để tránh ảnh hưởng chương sau
                savedScrollY = 0
            }
            Toast.makeText(this, "Đã trở lại vị trí cũ", Toast.LENGTH_SHORT).show()
        } else {
            // Chương mới -> Cuộn lên đầu
            binding.scrollView.scrollTo(0, 0)
        }

        // Tính toán lại tiến độ text ở footer
        binding.tvContent.post { calculateReadingProgress() }

        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
    }

    // =========================================================================
    // PHẦN 3: CÁC LOGIC KHÁC (GIỮ NGUYÊN)
    // =========================================================================

    private fun setupScrollListener() {
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            calculateReadingProgress()
        }
    }

    private fun calculateReadingProgress() {
        val scrollViewHeight = binding.scrollView.height
        val contentHeight = binding.tvContent.height
        val currentScroll = binding.scrollView.scrollY

        if (contentHeight == 0) return

        val remainingHeight = contentHeight - (currentScroll + scrollViewHeight)
        val pagesLeft = if (remainingHeight > 0) ceil(remainingHeight.toDouble() / scrollViewHeight).toInt() else 0

        val totalWords = binding.tvContent.text.length / 5
        val wordsLeft = totalWords * (remainingHeight.toFloat() / contentHeight.toFloat())
        val minutesLeft = (wordsLeft / 200).toInt()

        val timeString = if (minutesLeft < 1) "Sắp xong" else "$minutesLeft phút"
        binding.tvReadingProgress.text = "$timeString • còn $pagesLeft trang"
    }

    // --- Dialogs (Mục lục, Giao diện, Tools) ---

    private fun showTocDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_toc, null)
        dialog.setContentView(view)

        val rvToc = view.findViewById<RecyclerView>(R.id.rvToc)
        rvToc.layoutManager = LinearLayoutManager(this)

        val adapter = ChapterAdapter(chapterList, currentChapterId) { selectedChapter ->
            // Khi chọn từ mục lục -> Là chương mới -> Không restore scroll (về 0)
            loadChapterContent(selectedChapter, restoreScroll = false)
            dialog.dismiss()
        }
        rvToc.adapter = adapter
        if (currentIndex != -1) rvToc.scrollToPosition(currentIndex)
        dialog.show()
    }

    private fun showAppearanceDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_appearance, null)
        dialog.setContentView(view)

        val sbBrightness = view.findViewById<SeekBar>(R.id.sbBrightness)
        val currBrightness = window.attributes.screenBrightness
        sbBrightness.progress = if (currBrightness < 0) 50 else (currBrightness * 100).toInt()

        sbBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val lp = window.attributes
                lp.screenBrightness = if (progress <= 5) 0.05f else progress / 100f
                window.attributes = lp
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val btnDec = view.findViewById<View>(R.id.btnDecreaseSize)
        val btnInc = view.findViewById<View>(R.id.btnIncreaseSize)
        val tvSizeVal = view.findViewById<TextView>(R.id.tvTextSizeValue)
        tvSizeVal.text = currentTextSize.toInt().toString()

        btnDec.setOnClickListener {
            if (currentTextSize > 12) {
                currentTextSize -= 2
                binding.tvContent.textSize = currentTextSize
                tvSizeVal.text = currentTextSize.toInt().toString()
            }
        }
        btnInc.setOnClickListener {
            if (currentTextSize < 40) {
                currentTextSize += 2
                binding.tvContent.textSize = currentTextSize
                tvSizeVal.text = currentTextSize.toInt().toString()
            }
        }

        view.findViewById<View>(R.id.btnFontSerif).setOnClickListener {
            binding.tvContent.typeface = android.graphics.Typeface.SERIF
            binding.tvChapterTitle.typeface = android.graphics.Typeface.SERIF
        }
        view.findViewById<View>(R.id.btnFontSans).setOnClickListener {
            binding.tvContent.typeface = android.graphics.Typeface.SANS_SERIF
            binding.tvChapterTitle.typeface = android.graphics.Typeface.SANS_SERIF
        }

        view.findViewById<View>(R.id.btnThemeWhite).setOnClickListener { applyTheme(0) }
        view.findViewById<View>(R.id.btnThemeSepia).setOnClickListener { applyTheme(1) }
        view.findViewById<View>(R.id.btnThemeDark).setOnClickListener { applyTheme(2) }

        dialog.show()
    }

    private fun applyTheme(type: Int) {
        val bgWhite = ContextCompat.getColor(this, R.color.white)
        val bgSepia = 0xFFFFF9E6.toInt()
        val bgDark = 0xFF121212.toInt()

        val textBlack = ContextCompat.getColor(this, R.color.black)
        val textGray = 0xFF333333.toInt()
        val textWhite = 0xFFEEEEEE.toInt()
        val textSepia = 0xFF3E2723.toInt()

        when (type) {
            0 -> {
                binding.mainLayout.setBackgroundColor(bgWhite)
                binding.toolbarRl.setBackgroundResource(R.drawable.shape_toolbar02)
                binding.bottomInfoBar.setBackgroundColor(bgWhite)
                binding.tvContent.setTextColor(textGray)
                binding.tvChapterTitle.setTextColor(textBlack)
                binding.tvReadingProgress.setTextColor(textGray)
            }
            1 -> {
                binding.mainLayout.setBackgroundColor(bgSepia)
                binding.toolbarRl.setBackgroundColor(bgSepia)
                binding.bottomInfoBar.setBackgroundColor(bgSepia)
                binding.tvContent.setTextColor(textSepia)
                binding.tvChapterTitle.setTextColor(textSepia)
                binding.tvReadingProgress.setTextColor(0xFF8D6E63.toInt())
            }
            2 -> {
                binding.mainLayout.setBackgroundColor(bgDark)
                binding.toolbarRl.setBackgroundColor(0xFF1E1E1E.toInt())
                binding.bottomInfoBar.setBackgroundColor(0xFF1E1E1E.toInt())
                binding.tvContent.setTextColor(textWhite)
                binding.tvChapterTitle.setTextColor(textWhite)
                binding.tvReadingProgress.setTextColor(0xFFAAAAAA.toInt())
            }
        }
    }

    private fun showToolsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_tools, null)
        dialog.setContentView(view)

        val btnPlay = view.findViewById<ImageButton>(R.id.btnTtsPlay)
        val sbSpeed = view.findViewById<SeekBar>(R.id.sbTtsSpeed)
        val tvSpeed = view.findViewById<TextView>(R.id.tvTtsSpeed)

        updatePlayButtonIcon(btnPlay)
        val progress = (ttsSpeed * 10).toInt()
        sbSpeed.progress = progress
        tvSpeed.text = "Tốc độ: ${ttsSpeed}x"

        btnPlay.setOnClickListener { toggleTTS(btnPlay) }

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = if(progress < 1) 0.1f else progress / 10f
                ttsSpeed = speed
                tvSpeed.text = "Tốc độ: ${speed}x"
                if (textToSpeech.isSpeaking) {
                    textToSpeech.setSpeechRate(speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        view.findViewById<View>(R.id.btnToolTranslate).setOnClickListener {
            dialog.dismiss()
            val text = binding.tvContent.text.toString()
            if (text.isNotEmpty()) {
                val fragment = TranslateFragment.newInstance(text)
                fragment.show(supportFragmentManager, "TranslateFragment")
            }
        }
        dialog.show()
    }

    private fun updatePlayButtonIcon(btn: ImageButton) {
        if (textToSpeech.isSpeaking) {
            btn.setImageResource(R.drawable.ic_pause)
        } else {
            btn.setImageResource(R.drawable.ic_play)
        }
    }

    // --- TTS Logic ---

    private fun setupTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale("vi", "VN"))
            }
        }
    }

    private fun toggleTTS(btn: ImageButton) {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
            btn.setImageResource(R.drawable.ic_play)
        } else {
            val text = binding.tvContent.text.toString()
            if (text.isNotEmpty()) {
                textToSpeech.setSpeechRate(ttsSpeed)
                speakText(text)
                btn.setImageResource(R.drawable.ic_pause)
            }
        }
    }

    private fun speakText(text: String) {
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

    // --- Translate Logic ---
    fun translateText(text: String, callback: (String) -> Unit) {
        val detectedLang = detectLanguage(text)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(if (detectedLang == "vi") TranslateLanguage.VIETNAMESE else TranslateLanguage.ENGLISH)
            .setTargetLanguage(if (detectedLang == "vi") TranslateLanguage.ENGLISH else TranslateLanguage.VIETNAMESE)
            .build()
        val translator = Translation.getClient(options)

        val conditions = com.google.mlkit.common.model.DownloadConditions.Builder().requireWifi().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { callback(it) }
                    .addOnFailureListener { callback("Lỗi: ${it.message}") }
            }
            .addOnFailureListener { callback("Lỗi tải model: ${it.message}") }
    }

    private fun detectLanguage(text: String): String {
        val matches = Regex("[ăâđêôơưĂÂĐÊÔƠƯáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệóòỏõọốồổỗộớờởỡợúùủũụứừửữựíìỉĩịýỳỷỹỵ]").findAll(text).count()
        return if ((if (text.isNotEmpty()) matches.toDouble() / text.length else 0.0) > 0.02) "vi" else "en"
    }


    private fun saveToWidgetPrefs() {
        val prefs = getSharedPreferences("WidgetPrefs", MODE_PRIVATE)
        val editor = prefs.edit()

        // Lưu các thông tin cần thiết để Widget mở lại đúng chỗ
        editor.putString("lastBookId", bookId)
        editor.putString("lastChapterId", currentChapterId)

        // Lưu tên chương đang đọc để hiển thị cho đẹp
        val chapterTitle = binding.tvChapterTitle.text.toString()
        editor.putString("lastChapterTitle", chapterTitle)

        // Lưu thời gian để update widget
        editor.putLong("lastUpdate", System.currentTimeMillis())

        editor.apply()

        // Gửi lệnh cập nhật Widget ngay lập tức
        val intent = Intent(this, ContinueReadingWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(
            android.content.ComponentName(application, ContinueReadingWidget::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        // Lưu lại một lần nữa cho chắc chắn khi Activity bị hủy
        saveReadingProgress()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}