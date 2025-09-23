package com.example.appbook.utils;

import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.IOException;

public class LayoutTextStripper extends PDFTextStripper {

    public LayoutTextStripper() throws IOException {
        super();
        setLineSeparator(System.lineSeparator()); // giữ xuống dòng
        setWordSeparator(" "); // giữ khoảng trắng cơ bản
        setSortByPosition(true); // sắp xếp theo vị trí trên trang
    }

    @Override
    protected void writeWordSeparator() throws IOException {
        // Kiểm soát cách chèn khoảng trắng giữa các từ
        writeString(" ");
    }
}
