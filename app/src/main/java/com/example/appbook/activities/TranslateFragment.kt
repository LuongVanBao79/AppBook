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
        val view = inflater.inflate(R.layout.fragment_translate, container, false)
        val tvTranslated = view.findViewById<TextView>(R.id.tvTranslated)

        // Gọi dịch bằng hàm trong PdfViewActivity
        originalText?.let { text ->
            (activity as? PdfViewActivity)?.translateText(text) { translated ->
                tvTranslated.text = translated
            }
        }

        return view
    }
}
