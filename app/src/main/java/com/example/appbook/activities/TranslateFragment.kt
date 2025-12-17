package com.example.appbook.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.appbook.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TranslateFragment : BottomSheetDialogFragment() {

    private var originalText: String? = null

    companion object {
        private const val ARG_ORIGINAL = "arg_original"

        fun newInstance(text: String): TranslateFragment {
            val fragment = TranslateFragment()
            val args = Bundle()
            args.putString(ARG_ORIGINAL, text)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalText = arguments?.getString(ARG_ORIGINAL)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 1. Nạp giao diện
        val view = inflater.inflate(R.layout.fragment_translate, container, false)

        // 2. Tìm View hiển thị kết quả (Bạn đã làm đúng ở đây)
        val tvTranslated = view.findViewById<TextView>(R.id.tvTranslated)

        // (Tùy chọn) Tìm View hiển thị text gốc nếu XML của bạn có
        // val tvOriginal = view.findViewById<TextView>(R.id.tvOriginal)
        // tvOriginal?.text = originalText

        // 3. Gọi hàm dịch
        originalText?.let { text ->
            if (activity is ReadingActivity) {
                (activity as ReadingActivity).translateText(text) { translated ->
                    // --- SỬA LỖI TẠI ĐÂY ---
                    // Thay vì dùng 'binding.translatedTv' (chưa khai báo),
                    // ta dùng biến 'tvTranslated' đã tìm thấy ở trên.
                    tvTranslated.text = translated
                }
            }
        }

        return view
    }
}