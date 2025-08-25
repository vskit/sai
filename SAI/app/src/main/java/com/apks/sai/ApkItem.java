package com.apks.sai;

public class ApkItem {
    private String fileName;
    private boolean selected;
    private String filePath;

    public ApkItem(String fileName, String filePath,boolean selected) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.selected = selected;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}