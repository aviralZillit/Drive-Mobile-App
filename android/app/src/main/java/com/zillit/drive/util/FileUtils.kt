package com.zillit.drive.util

import android.graphics.Color

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return "%.1f %s".format(bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    fun getExtensionColor(extension: String): Int {
        return when (extension.lowercase()) {
            "pdf" -> Color.parseColor("#E53935")
            "doc", "docx" -> Color.parseColor("#1565C0")
            "xls", "xlsx" -> Color.parseColor("#2E7D32")
            "ppt", "pptx" -> Color.parseColor("#E65100")
            "jpg", "jpeg", "png", "gif", "webp", "svg" -> Color.parseColor("#7B1FA2")
            "mp4", "mov", "avi", "mkv", "webm" -> Color.parseColor("#AD1457")
            "mp3", "wav", "aac", "flac" -> Color.parseColor("#00838F")
            "zip", "rar", "7z", "tar", "gz" -> Color.parseColor("#4E342E")
            "txt", "csv", "md" -> Color.parseColor("#546E7A")
            "json", "xml", "html", "css", "js" -> Color.parseColor("#F57F17")
            else -> Color.parseColor("#78909C")
        }
    }

    fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "zip" -> "application/zip"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }

    fun isImageFile(extension: String): Boolean =
        extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp")

    fun isVideoFile(extension: String): Boolean =
        extension.lowercase() in listOf("mp4", "mov", "avi", "mkv", "webm", "m4v")

    fun isAudioFile(extension: String): Boolean =
        extension.lowercase() in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a")

    fun isPdfFile(extension: String): Boolean =
        extension.lowercase() == "pdf"

    fun isOfficeFile(extension: String): Boolean =
        extension.lowercase() in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp")

    fun isTextFile(extension: String): Boolean =
        extension.lowercase() in listOf("txt", "csv", "md", "json", "xml", "html", "css", "js", "log")

    fun isEditableFile(extension: String): Boolean =
        extension.lowercase() in listOf("docx", "xlsx", "pptx", "doc", "xls", "ppt", "odt", "ods", "odp", "csv", "txt")
}
