package com.example.appbook.utils;

import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.IOException;

public class LayoutTextStripper extends PDFTextStripper {

    private StringBuilder currentLine = new StringBuilder();
    private StringBuilder output = new StringBuilder();

    public LayoutTextStripper() throws IOException {
        super();
        setSortByPosition(true);
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        String line = currentLine.toString().trim();
        if (line.isEmpty()) {
            currentLine.setLength(0);
            return;
        }

        // Kiểm tra xem dòng có kết thúc bằng dấu kết câu không
        boolean endsWithSentence = line.endsWith(".") || line.endsWith("!") || line.endsWith("?") || line.endsWith("…") || line.endsWith("\"");

        if (output.length() > 0) {
            if (endsWithSentence) {
                output.append("\n"); // xuống dòng thật
            } else {
                output.append(" "); // nối tiếp câu bị xuống dòng giữa chừng
            }
        }
        output.append(line);

        currentLine.setLength(0);
    }

    @Override
    protected void writeString(String text) throws IOException {
        currentLine.append(text);
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        currentLine.append(" ");
    }

    @Override
    public String getText(com.tom_roush.pdfbox.pdmodel.PDDocument doc) throws IOException {
        output.setLength(0);
        currentLine.setLength(0);
        super.getText(doc);

        // Dòng cuối cùng nếu còn sót
        if (currentLine.length() > 0) {
            output.append(currentLine.toString().trim());
        }

        return output.toString().trim();
    }
}
