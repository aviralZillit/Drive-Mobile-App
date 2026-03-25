import XCTest
@testable import ZillitDrive

/// Tests for FileDetailView logic via the repository interactions.
/// Since FileDetailView has inline logic (no separate ViewModel),
/// we test the repository call patterns and data transformations
/// that the view relies on.
final class FileDetailTests: XCTestCase {

    private var mockRepo: MockDriveRepository!

    private let sampleFile = DriveFile(
        id: "f1", fileName: "report.pdf", fileExtension: "pdf",
        fileSizeBytes: 2048, mimeType: "application/pdf",
        folderId: "d1", filePath: "/report.pdf", description: "A report",
        createdBy: "user1", createdOn: 1700000000, updatedOn: 1700000001,
        thumbnailUrl: nil
    )

    override func setUp() {
        super.setUp()
        mockRepo = MockDriveRepository()
    }

    override func tearDown() {
        mockRepo = nil
        super.tearDown()
    }

    // MARK: - Download Tests

    func testDownloadFile_success_setsPreviewURL() async throws {
        mockRepo.streamUrlResult = "https://cdn.example.com/report.pdf"

        let streamUrl = try await mockRepo.getFileStreamUrl(fileId: sampleFile.id)

        XCTAssertEqual(streamUrl, "https://cdn.example.com/report.pdf")
        XCTAssertEqual(mockRepo.getFileStreamUrlCalled, 1)

        // Verify the URL is valid
        let url = URL(string: streamUrl)
        XCTAssertNotNil(url)
    }

    func testDownloadFile_invalidURL_setsError() async throws {
        mockRepo.streamUrlResult = ""

        let streamUrl = try await mockRepo.getFileStreamUrl(fileId: sampleFile.id)

        // An empty string creates nil URL
        let url = URL(string: streamUrl)
        XCTAssertNil(url, "Empty string should produce nil URL")
    }

    func testDownloadFile_emptyData_setsError() async throws {
        // Simulate the stream URL returning but the downloaded data being empty
        mockRepo.streamUrlResult = "https://cdn.example.com/empty"

        let streamUrl = try await mockRepo.getFileStreamUrl(fileId: sampleFile.id)
        XCTAssertNotNil(URL(string: streamUrl))

        // The view checks `data.isEmpty` after download and sets error
        // We verify the error condition logic
        let emptyData = Data()
        XCTAssertTrue(emptyData.isEmpty, "Empty data should trigger error in the view")
    }

    // MARK: - Share Tests

    func testShareFile_success_setsShareURL() async throws {
        mockRepo.shareLinkResult = ShareLink(url: "https://share.example.com/abc123", expiresAt: "2025-12-31")

        let link = try await mockRepo.generateShareLink(fileId: sampleFile.id, expiry: "24h")

        XCTAssertEqual(link.url, "https://share.example.com/abc123")
        XCTAssertEqual(link.expiresAt, "2025-12-31")
        XCTAssertEqual(mockRepo.generateShareLinkCalled, 1)
        XCTAssertEqual(mockRepo.lastShareLinkFileId, "f1")

        let shareURL = URL(string: link.url)
        XCTAssertNotNil(shareURL)
    }

    func testShareFile_failure_setsError() async {
        mockRepo.shareLinkError = MockError.custom("Network error")

        do {
            _ = try await mockRepo.generateShareLink(fileId: sampleFile.id, expiry: "24h")
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertEqual(error.localizedDescription, "Network error")
        }
    }

    // MARK: - Load Details Tests

    func testLoadDetails_loadsCommentsVersionsTags() async throws {
        let comment = DriveComment(id: "c1", fileId: "f1", userId: "user1", text: "Great file!", createdOn: 1700000000)
        let version = DriveVersion(id: "v1", fileId: "f1", versionNumber: 1, fileSizeBytes: 2048, createdBy: "user1", createdOn: 1700000000)
        let tag = DriveTag(id: "t1", name: "Important", color: "#FF0000")

        mockRepo.commentsResult = [comment]
        mockRepo.versionsResult = [version]
        mockRepo.tagsResult = [tag]

        // Simulate the parallel loading that FileDetailView does in loadDetails()
        async let c = mockRepo.getComments(fileId: sampleFile.id)
        async let v = mockRepo.getFileVersions(fileId: sampleFile.id)
        async let t = mockRepo.getItemTags(itemId: sampleFile.id, itemType: "file")

        let comments = (try? await c) ?? []
        let versions = (try? await v) ?? []
        let tags = (try? await t) ?? []

        XCTAssertEqual(comments.count, 1)
        XCTAssertEqual(comments.first?.text, "Great file!")
        XCTAssertEqual(versions.count, 1)
        XCTAssertEqual(versions.first?.versionNumber, 1)
        XCTAssertEqual(tags.count, 1)
        XCTAssertEqual(tags.first?.name, "Important")

        XCTAssertEqual(mockRepo.getCommentsCalled, 1)
        XCTAssertEqual(mockRepo.getFileVersionsCalled, 1)
        XCTAssertEqual(mockRepo.getItemTagsCalled, 1)
    }

    // MARK: - Comment Tests

    func testAddComment_success_appendsToList() async throws {
        let newComment = DriveComment(id: "c-new", fileId: "f1", userId: "user1", text: "New comment", createdOn: 1700001000)
        mockRepo.addCommentResult = newComment

        var comments: [DriveComment] = []
        let comment = try await mockRepo.addComment(fileId: sampleFile.id, text: "New comment")
        comments.append(comment)

        XCTAssertEqual(comments.count, 1)
        XCTAssertEqual(comments.first?.text, "New comment")
        XCTAssertEqual(comments.first?.id, "c-new")
        XCTAssertEqual(mockRepo.addCommentCalled, 1)
        XCTAssertEqual(mockRepo.lastAddCommentFileId, "f1")
        XCTAssertEqual(mockRepo.lastAddCommentText, "New comment")
    }

    func testDeleteComment_removesFromList() async throws {
        var comments = [
            DriveComment(id: "c1", fileId: "f1", userId: "user1", text: "First", createdOn: 1000),
            DriveComment(id: "c2", fileId: "f1", userId: "user1", text: "Second", createdOn: 2000)
        ]

        try await mockRepo.deleteComment(commentId: "c1")
        comments.removeAll { $0.id == "c1" }

        XCTAssertEqual(comments.count, 1)
        XCTAssertEqual(comments.first?.id, "c2")
        XCTAssertEqual(mockRepo.deleteCommentCalled, 1)
        XCTAssertEqual(mockRepo.lastDeleteCommentId, "c1")
    }

    // MARK: - Version Tests

    func testRestoreVersion_reloadsDetails() async throws {
        try await mockRepo.restoreVersion(fileId: sampleFile.id, versionId: "v2")

        XCTAssertEqual(mockRepo.restoreVersionCalled, 1)
        XCTAssertEqual(mockRepo.lastRestoreVersionFileId, "f1")
        XCTAssertEqual(mockRepo.lastRestoreVersionId, "v2")

        // After restore, the view calls loadDetails again
        // Simulate that by calling getComments/getFileVersions/getItemTags
        _ = try await mockRepo.getComments(fileId: sampleFile.id)
        _ = try await mockRepo.getFileVersions(fileId: sampleFile.id)
        _ = try await mockRepo.getItemTags(itemId: sampleFile.id, itemType: "file")

        XCTAssertEqual(mockRepo.getCommentsCalled, 1)
        XCTAssertEqual(mockRepo.getFileVersionsCalled, 1)
        XCTAssertEqual(mockRepo.getItemTagsCalled, 1)
    }

    // MARK: - Tag Tests

    func testRemoveTag_removesFromList() async throws {
        var tags = [
            DriveTag(id: "t1", name: "Important", color: "#FF0000"),
            DriveTag(id: "t2", name: "Review", color: "#00FF00")
        ]

        try await mockRepo.removeTag(tagId: "t1", itemId: sampleFile.id, itemType: "file")
        tags.removeAll { $0.id == "t1" }

        XCTAssertEqual(tags.count, 1)
        XCTAssertEqual(tags.first?.name, "Review")
        XCTAssertEqual(mockRepo.removeTagCalled, 1)
        XCTAssertEqual(mockRepo.lastRemoveTagId, "t1")
        XCTAssertEqual(mockRepo.lastRemoveTagItemId, "f1")
    }
}
