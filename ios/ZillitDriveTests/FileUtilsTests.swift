import XCTest
import SwiftUI
@testable import ZillitDrive

final class FileUtilsTests: XCTestCase {

    // MARK: - formatFileSize Tests

    func testFormatFileSize_bytes() {
        XCTAssertEqual(FileUtils.formatFileSize(500), "500.0 B")
        XCTAssertEqual(FileUtils.formatFileSize(1), "1.0 B")
        XCTAssertEqual(FileUtils.formatFileSize(999), "999.0 B")
    }

    func testFormatFileSize_kilobytes() {
        XCTAssertEqual(FileUtils.formatFileSize(1024), "1.0 KB")
        XCTAssertEqual(FileUtils.formatFileSize(1536), "1.5 KB")
        XCTAssertEqual(FileUtils.formatFileSize(10240), "10.0 KB")
    }

    func testFormatFileSize_megabytes() {
        XCTAssertEqual(FileUtils.formatFileSize(1_048_576), "1.0 MB")
        XCTAssertEqual(FileUtils.formatFileSize(5_242_880), "5.0 MB")
        XCTAssertEqual(FileUtils.formatFileSize(1_572_864), "1.5 MB")
    }

    func testFormatFileSize_gigabytes() {
        XCTAssertEqual(FileUtils.formatFileSize(1_073_741_824), "1.0 GB")
        XCTAssertEqual(FileUtils.formatFileSize(2_147_483_648), "2.0 GB")
    }

    func testFormatFileSize_zero() {
        XCTAssertEqual(FileUtils.formatFileSize(0), "0 B")
    }

    func testFormatFileSize_negative() {
        XCTAssertEqual(FileUtils.formatFileSize(-1), "0 B")
    }

    // MARK: - isImage Tests

    func testIsImage_jpgReturnsTrue() {
        XCTAssertTrue(FileUtils.isImage("jpg"))
        XCTAssertTrue(FileUtils.isImage("JPG"))
        XCTAssertTrue(FileUtils.isImage("jpeg"))
        XCTAssertTrue(FileUtils.isImage("JPEG"))
    }

    func testIsImage_pngReturnsTrue() {
        XCTAssertTrue(FileUtils.isImage("png"))
        XCTAssertTrue(FileUtils.isImage("PNG"))
    }

    func testIsImage_gifReturnsTrue() {
        XCTAssertTrue(FileUtils.isImage("gif"))
    }

    func testIsImage_webpReturnsTrue() {
        XCTAssertTrue(FileUtils.isImage("webp"))
    }

    func testIsImage_svgReturnsTrue() {
        XCTAssertTrue(FileUtils.isImage("svg"))
    }

    func testIsImage_bmpReturnsTrue() {
        XCTAssertTrue(FileUtils.isImage("bmp"))
    }

    func testIsImage_pdfReturnsFalse() {
        XCTAssertFalse(FileUtils.isImage("pdf"))
    }

    func testIsImage_txtReturnsFalse() {
        XCTAssertFalse(FileUtils.isImage("txt"))
    }

    // MARK: - isVideo Tests

    func testIsVideo_mp4ReturnsTrue() {
        XCTAssertTrue(FileUtils.isVideo("mp4"))
        XCTAssertTrue(FileUtils.isVideo("MP4"))
    }

    func testIsVideo_movReturnsTrue() {
        XCTAssertTrue(FileUtils.isVideo("mov"))
    }

    func testIsVideo_aviReturnsTrue() {
        XCTAssertTrue(FileUtils.isVideo("avi"))
    }

    func testIsVideo_mkvReturnsTrue() {
        XCTAssertTrue(FileUtils.isVideo("mkv"))
    }

    func testIsVideo_webmReturnsTrue() {
        XCTAssertTrue(FileUtils.isVideo("webm"))
    }

    func testIsVideo_m4vReturnsTrue() {
        XCTAssertTrue(FileUtils.isVideo("m4v"))
    }

    func testIsVideo_mp3ReturnsFalse() {
        XCTAssertFalse(FileUtils.isVideo("mp3"))
    }

    // MARK: - isAudio Tests

    func testIsAudio_mp3ReturnsTrue() {
        XCTAssertTrue(FileUtils.isAudio("mp3"))
        XCTAssertTrue(FileUtils.isAudio("MP3"))
    }

    func testIsAudio_wavReturnsTrue() {
        XCTAssertTrue(FileUtils.isAudio("wav"))
    }

    func testIsAudio_aacReturnsTrue() {
        XCTAssertTrue(FileUtils.isAudio("aac"))
    }

    func testIsAudio_flacReturnsTrue() {
        XCTAssertTrue(FileUtils.isAudio("flac"))
    }

    func testIsAudio_oggReturnsTrue() {
        XCTAssertTrue(FileUtils.isAudio("ogg"))
    }

    func testIsAudio_m4aReturnsTrue() {
        XCTAssertTrue(FileUtils.isAudio("m4a"))
    }

    func testIsAudio_mp4ReturnsFalse() {
        XCTAssertFalse(FileUtils.isAudio("mp4"))
    }

    // MARK: - isPDF Tests

    func testIsPDF_pdfReturnsTrue() {
        XCTAssertTrue(FileUtils.isPDF("pdf"))
        XCTAssertTrue(FileUtils.isPDF("PDF"))
        XCTAssertTrue(FileUtils.isPDF("Pdf"))
    }

    func testIsPDF_docReturnsFalse() {
        XCTAssertFalse(FileUtils.isPDF("doc"))
    }

    // MARK: - isOffice Tests

    func testIsOffice_docxReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("docx"))
        XCTAssertTrue(FileUtils.isOffice("DOCX"))
    }

    func testIsOffice_docReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("doc"))
    }

    func testIsOffice_xlsxReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("xlsx"))
    }

    func testIsOffice_xlsReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("xls"))
    }

    func testIsOffice_pptxReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("pptx"))
    }

    func testIsOffice_pptReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("ppt"))
    }

    func testIsOffice_odtReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("odt"))
    }

    func testIsOffice_odsReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("ods"))
    }

    func testIsOffice_odpReturnsTrue() {
        XCTAssertTrue(FileUtils.isOffice("odp"))
    }

    func testIsOffice_pdfReturnsFalse() {
        XCTAssertFalse(FileUtils.isOffice("pdf"))
    }

    func testIsOffice_txtReturnsFalse() {
        XCTAssertFalse(FileUtils.isOffice("txt"))
    }

    // MARK: - extensionColor Tests

    func testExtensionColor_differentExtensionsReturnDifferentColors() {
        let pdfColor = FileUtils.extensionColor(for: "pdf")
        let docColor = FileUtils.extensionColor(for: "doc")
        let xlsColor = FileUtils.extensionColor(for: "xls")
        let pptColor = FileUtils.extensionColor(for: "ppt")
        let jpgColor = FileUtils.extensionColor(for: "jpg")
        let mp4Color = FileUtils.extensionColor(for: "mp4")
        let mp3Color = FileUtils.extensionColor(for: "mp3")
        let zipColor = FileUtils.extensionColor(for: "zip")
        let txtColor = FileUtils.extensionColor(for: "txt")
        let jsonColor = FileUtils.extensionColor(for: "json")
        let unknownColor = FileUtils.extensionColor(for: "xyz")

        // Verify different categories have different colors
        XCTAssertNotEqual(pdfColor, docColor)
        XCTAssertNotEqual(docColor, xlsColor)
        XCTAssertNotEqual(xlsColor, pptColor)
        XCTAssertNotEqual(pptColor, jpgColor)
        XCTAssertNotEqual(jpgColor, mp4Color)
        XCTAssertNotEqual(mp4Color, mp3Color)
        XCTAssertNotEqual(mp3Color, zipColor)
        XCTAssertNotEqual(zipColor, txtColor)
        XCTAssertNotEqual(txtColor, jsonColor)

        // Same category should return same color
        let docxColor = FileUtils.extensionColor(for: "docx")
        XCTAssertEqual(docColor, docxColor)

        let jpegColor = FileUtils.extensionColor(for: "jpeg")
        XCTAssertEqual(jpgColor, jpegColor)
    }

    func testExtensionColor_caseInsensitive() {
        let lowerColor = FileUtils.extensionColor(for: "pdf")
        let upperColor = FileUtils.extensionColor(for: "PDF")
        XCTAssertEqual(lowerColor, upperColor)
    }

    func testExtensionColor_unknownReturnsDefault() {
        let color1 = FileUtils.extensionColor(for: "xyz")
        let color2 = FileUtils.extensionColor(for: "abc")
        XCTAssertEqual(color1, color2)
    }
}
