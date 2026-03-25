import SwiftUI

enum FileUtils {

    static func formatFileSize(_ bytes: Int64) -> String {
        if bytes <= 0 { return "0 B" }
        let units = ["B", "KB", "MB", "GB", "TB"]
        let digitGroups = Int(log10(Double(bytes)) / log10(1024.0))
        let index = min(digitGroups, units.count - 1)
        let size = Double(bytes) / pow(1024.0, Double(index))
        return String(format: "%.1f %@", size, units[index])
    }

    static func extensionColor(for ext: String) -> Color {
        switch ext.lowercased() {
        case "pdf": return Color(red: 0.90, green: 0.22, blue: 0.21)
        case "doc", "docx": return Color(red: 0.08, green: 0.40, blue: 0.75)
        case "xls", "xlsx": return Color(red: 0.18, green: 0.49, blue: 0.20)
        case "ppt", "pptx": return Color(red: 0.90, green: 0.32, blue: 0.00)
        case "jpg", "jpeg", "png", "gif", "webp", "svg": return Color(red: 0.48, green: 0.12, blue: 0.64)
        case "mp4", "mov", "avi", "mkv", "webm": return Color(red: 0.68, green: 0.08, blue: 0.34)
        case "mp3", "wav", "aac", "flac": return Color(red: 0.00, green: 0.51, blue: 0.56)
        case "zip", "rar", "7z", "tar", "gz": return Color(red: 0.30, green: 0.20, blue: 0.18)
        case "txt", "csv", "md": return Color(red: 0.33, green: 0.43, blue: 0.48)
        case "json", "xml", "html", "css", "js": return Color(red: 0.96, green: 0.50, blue: 0.09)
        default: return Color(red: 0.47, green: 0.56, blue: 0.61)
        }
    }

    static func isImage(_ ext: String) -> Bool {
        ["jpg", "jpeg", "png", "gif", "webp", "svg", "bmp"].contains(ext.lowercased())
    }

    static func isVideo(_ ext: String) -> Bool {
        ["mp4", "mov", "avi", "mkv", "webm", "m4v"].contains(ext.lowercased())
    }

    static func isAudio(_ ext: String) -> Bool {
        ["mp3", "wav", "aac", "flac", "ogg", "m4a"].contains(ext.lowercased())
    }

    static func isPDF(_ ext: String) -> Bool { ext.lowercased() == "pdf" }

    static func isOffice(_ ext: String) -> Bool {
        ["doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp"].contains(ext.lowercased())
    }

    static func isEditable(_ ext: String) -> Bool {
        ["docx", "xlsx", "pptx", "doc", "xls", "ppt", "odt", "ods", "odp", "csv", "txt"].contains(ext.lowercased())
    }

    static func fileType(for ext: String) -> String {
        let e = ext.lowercased()
        if isImage(e) { return "image" }
        if isVideo(e) { return "video" }
        if isAudio(e) { return "audio" }
        if isPDF(e) { return "pdf" }
        if isOffice(e) { return "document" }
        return "document"
    }
}
