package com.example.appbook.activities

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.appbook.R
import com.example.appbook.databinding.ActivityPdfFileHandlerBinding
import com.example.appbook.databinding.DialogPasswordPromptBinding
import com.example.appbook.utils.FileEncryptionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class PdfFileHandlerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfFileHandlerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfFileHandlerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, "Không tìm thấy file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val fileName = resolveDisplayName(uri) ?: uri.lastPathSegment ?: "Tập tin"
        title = fileName

        val isEncrypted = fileName.endsWith(".pdf.enc", ignoreCase = true)
                || uri.toString().endsWith(".pdf.enc", ignoreCase = true)

        if (isEncrypted) {
            promptPassword { password ->
                loadEncryptedPdf(uri, password)
            }
        } else {
            loadPlainPdf(uri)
        }
    }

    private fun promptPassword(onPasswordConfirmed: (String) -> Unit) {
        val dialogBinding = DialogPasswordPromptBinding.inflate(LayoutInflater.from(this))
        dialogBinding.passwordLayout.hint = "Nhập mật khẩu để mở file"

        val dialog = AlertDialog.Builder(this, R.style.CustomDialog)
            .setTitle("File đã mã hóa")
            .setView(dialogBinding.root)
            .setNegativeButton("Hủy") { d, _ -> d.dismiss(); finish() }
            .setPositiveButton("Mở", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val password = dialogBinding.passwordInput.text?.toString()?.trim().orEmpty()
                if (password.length < 4) {
                    dialogBinding.passwordLayout.error = "Mật khẩu không hợp lệ"
                } else {
                    dialogBinding.passwordLayout.error = null
                    dialog.dismiss()
                    onPasswordConfirmed(password)
                }
            }
        }

        dialog.show()
    }

    private fun loadPlainPdf(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bytes = readBytesFromUri(uri)
                withContext(Dispatchers.Main) {
                    showPdf(bytes)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@PdfFileHandlerActivity, "Không thể mở file: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun loadEncryptedPdf(uri: Uri, password: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encryptedBytes = readBytesFromUri(uri)
                val plainBytes = FileEncryptionHelper.decrypt(encryptedBytes, password.toCharArray())
                withContext(Dispatchers.Main) {
                    showPdf(plainBytes)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@PdfFileHandlerActivity, "Sai mật khẩu hoặc file hỏng", Toast.LENGTH_LONG).show()
                    promptPassword { retryPassword -> loadEncryptedPdf(uri, retryPassword) }
                }
            }
        }
    }

    private suspend fun readBytesFromUri(uri: Uri): ByteArray {
        return withContext(Dispatchers.IO) {
            val resolver = contentResolver
            resolver.openInputStream(uri)?.use { input ->
                readFully(input)
            } ?: throw IllegalStateException("Không thể đọc dữ liệu từ uri")
        }
    }

    private fun readFully(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(DEFAULT_BUFFER_SIZE)
        var nRead: Int
        while (input.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return buffer.toByteArray()
    }

    private fun showPdf(bytes: ByteArray) {
        binding.progressBar.visibility = View.GONE
        binding.pdfView.fromBytes(bytes)
            .spacing(4)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .defaultPage(0)
            .onError {
                Toast.makeText(this, "Lỗi khi hiển thị PDF", Toast.LENGTH_LONG).show()
                finish()
            }
            .load()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
