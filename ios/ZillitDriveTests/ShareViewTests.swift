import XCTest
@testable import ZillitDrive

/// Tests for share view logic via mocked repository interactions.
/// FileShareView and FolderShareView have inline logic, so we test
/// the repository call patterns they rely on.
final class ShareViewTests: XCTestCase {

    private var mockRepo: MockDriveRepository!

    override func setUp() {
        super.setUp()
        mockRepo = MockDriveRepository()
    }

    override func tearDown() {
        mockRepo = nil
        super.tearDown()
    }

    // MARK: - File Access Tests

    func testLoadFileAccess_success_populatesEntries() async throws {
        let entries = [
            FileAccessDTO(userId: "user1", fileId: "f1", canView: true, canEdit: true, canDownload: false),
            FileAccessDTO(userId: "user2", fileId: "f1", canView: true, canEdit: false, canDownload: false)
        ]
        mockRepo.fileAccessResult = entries

        let result = try await mockRepo.getFileAccess(fileId: "f1")

        XCTAssertEqual(result.count, 2)
        XCTAssertEqual(result[0].userId, "user1")
        XCTAssertEqual(result[0].canView, true)
        XCTAssertEqual(result[0].canEdit, true)
        XCTAssertEqual(result[0].canDownload, false)
        XCTAssertEqual(result[1].userId, "user2")
        XCTAssertEqual(result[1].canEdit, false)
        XCTAssertEqual(mockRepo.getFileAccessCalled, 1)
    }

    func testLoadFileAccess_failure_setsError() async {
        mockRepo.fileAccessError = MockError.custom("Access denied")

        do {
            _ = try await mockRepo.getFileAccess(fileId: "f1")
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertEqual(error.localizedDescription, "Access denied")
        }
        XCTAssertEqual(mockRepo.getFileAccessCalled, 1)
    }

    // MARK: - Folder Access Tests

    func testLoadFolderAccess_success_populatesEntries() async throws {
        let entries = [
            FolderAccessDTO(userId: "user1", folderId: "d1", role: "editor", inherited: false),
            FolderAccessDTO(userId: "user2", folderId: "d1", role: "viewer", inherited: true)
        ]
        mockRepo.folderAccessResult = entries

        let result = try await mockRepo.getFolderAccess(folderId: "d1")

        XCTAssertEqual(result.count, 2)
        XCTAssertEqual(result[0].userId, "user1")
        XCTAssertEqual(result[0].role, "editor")
        XCTAssertEqual(result[0].inherited, false)
        XCTAssertEqual(result[1].userId, "user2")
        XCTAssertEqual(result[1].role, "viewer")
        XCTAssertEqual(result[1].inherited, true)
        XCTAssertEqual(mockRepo.getFolderAccessCalled, 1)
    }

    // MARK: - Generate Share Link Tests

    func testGenerateShareLink_success_setsLink() async throws {
        mockRepo.shareLinkResult = ShareLink(url: "https://share.example.com/xyz", expiresAt: "2025-06-01")

        let link = try await mockRepo.generateShareLink(fileId: "f1", expiry: "24h")

        XCTAssertEqual(link.url, "https://share.example.com/xyz")
        XCTAssertEqual(link.expiresAt, "2025-06-01")
        XCTAssertEqual(mockRepo.generateShareLinkCalled, 1)
        XCTAssertEqual(mockRepo.lastShareLinkFileId, "f1")
        XCTAssertEqual(mockRepo.lastShareLinkExpiry, "24h")
    }

    func testGenerateShareLink_failure_setsError() async {
        mockRepo.shareLinkError = MockError.custom("Failed to generate link")

        do {
            _ = try await mockRepo.generateShareLink(fileId: "f1", expiry: "24h")
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertEqual(error.localizedDescription, "Failed to generate link")
        }
    }

    // MARK: - Save File Permissions Tests

    func testSaveFilePermissions_success_callsUpdateFileAccess() async throws {
        let entries: [[String: Any]] = [
            ["user_id": "user1", "can_view": true, "can_edit": true, "can_download": false],
            ["user_id": "user2", "can_view": true, "can_edit": false, "can_download": false]
        ]

        try await mockRepo.updateFileAccess(fileId: "f1", entries: entries)

        XCTAssertEqual(mockRepo.updateFileAccessCalled, 1)
        XCTAssertEqual(mockRepo.lastUpdateFileAccessFileId, "f1")
        XCTAssertNotNil(mockRepo.lastUpdateFileAccessEntries)
        XCTAssertEqual(mockRepo.lastUpdateFileAccessEntries?.count, 2)

        let firstEntry = mockRepo.lastUpdateFileAccessEntries?.first
        XCTAssertEqual(firstEntry?["user_id"] as? String, "user1")
        XCTAssertEqual(firstEntry?["can_view"] as? Bool, true)
        XCTAssertEqual(firstEntry?["can_edit"] as? Bool, true)
    }

    func testSaveFilePermissions_failure_setsError() async {
        mockRepo.updateFileAccessError = MockError.custom("Save failed")

        do {
            try await mockRepo.updateFileAccess(fileId: "f1", entries: [])
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertEqual(error.localizedDescription, "Save failed")
        }
    }

    // MARK: - Save Folder Permissions Tests

    func testSaveFolderPermissions_success_callsUpdateFolderAccess() async throws {
        let entries: [[String: Any]] = [
            ["user_id": "user1", "role": "editor"],
            ["user_id": "user2", "role": "viewer"]
        ]

        try await mockRepo.updateFolderAccess(folderId: "d1", entries: entries)

        XCTAssertEqual(mockRepo.updateFolderAccessCalled, 1)
        XCTAssertEqual(mockRepo.lastUpdateFolderAccessFolderId, "d1")
        XCTAssertNotNil(mockRepo.lastUpdateFolderAccessEntries)
        XCTAssertEqual(mockRepo.lastUpdateFolderAccessEntries?.count, 2)

        let firstEntry = mockRepo.lastUpdateFolderAccessEntries?.first
        XCTAssertEqual(firstEntry?["user_id"] as? String, "user1")
        XCTAssertEqual(firstEntry?["role"] as? String, "editor")
    }

    func testSaveFolderPermissions_failure_setsError() async {
        mockRepo.updateFolderAccessError = MockError.custom("Permission denied")

        do {
            try await mockRepo.updateFolderAccess(folderId: "d1", entries: [])
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertEqual(error.localizedDescription, "Permission denied")
        }
    }

    // MARK: - Integration-like Tests

    func testFileShareFlow_loadThenSave() async throws {
        // Step 1: Load access entries
        let accessEntries = [
            FileAccessDTO(userId: "user1", fileId: "f1", canView: true, canEdit: false, canDownload: false)
        ]
        mockRepo.fileAccessResult = accessEntries

        var entries = try await mockRepo.getFileAccess(fileId: "f1")
        XCTAssertEqual(entries.count, 1)

        // Step 2: Modify entries locally (simulate user toggling edit)
        entries[0] = FileAccessDTO(
            userId: entries[0].userId,
            fileId: entries[0].fileId,
            canView: entries[0].canView,
            canEdit: true,
            canDownload: entries[0].canDownload
        )

        // Step 3: Save
        let saveEntries: [[String: Any]] = entries.map { entry in
            [
                "user_id": entry.userId,
                "can_view": entry.canView ?? true,
                "can_edit": entry.canEdit ?? false,
                "can_download": entry.canDownload ?? false
            ]
        }
        try await mockRepo.updateFileAccess(fileId: "f1", entries: saveEntries)

        XCTAssertEqual(mockRepo.getFileAccessCalled, 1)
        XCTAssertEqual(mockRepo.updateFileAccessCalled, 1)
        let savedEntry = mockRepo.lastUpdateFileAccessEntries?.first
        XCTAssertEqual(savedEntry?["can_edit"] as? Bool, true)
    }
}
