import XCTest
@testable import ZillitDrive

final class DriveMapperTests: XCTestCase {

    // MARK: - File DTO to Domain

    func testFileMapping() {
        let dto = DriveFileDTO(
            id: "f1", fileName: "test.pdf", fileExtension: "pdf",
            fileSizeBytes: 1024, mimeType: "application/pdf",
            folderId: "fold1", filePath: "/test.pdf", description: "A test file",
            createdBy: "user1", createdOn: 1700000000, updatedOn: 1700000001,
            deletedOn: nil, attachments: nil
        )
        let file = DriveMapper.toDomain(dto, isFavorite: true)
        XCTAssertEqual(file.id, "f1")
        XCTAssertEqual(file.fileName, "test.pdf")
        XCTAssertEqual(file.fileExtension, "pdf")
        XCTAssertEqual(file.fileSizeBytes, 1024)
        XCTAssertEqual(file.mimeType, "application/pdf")
        XCTAssertEqual(file.folderId, "fold1")
        XCTAssertEqual(file.filePath, "/test.pdf")
        XCTAssertEqual(file.description, "A test file")
        XCTAssertEqual(file.createdBy, "user1")
        XCTAssertEqual(file.createdOn, 1700000000)
        XCTAssertEqual(file.updatedOn, 1700000001)
        XCTAssertTrue(file.isFavorite)
        XCTAssertNil(file.thumbnailUrl)
    }

    func testFileMapping_defaultFavoriteIsFalse() {
        let dto = DriveFileDTO(
            id: "f1", fileName: "test.pdf", fileExtension: "pdf",
            fileSizeBytes: 1024, mimeType: "application/pdf",
            folderId: nil, filePath: nil, description: nil,
            createdBy: "user1", createdOn: 0, updatedOn: 0,
            deletedOn: nil, attachments: nil
        )
        let file = DriveMapper.toDomain(dto)
        XCTAssertFalse(file.isFavorite)
    }

    func testFileMapping_withAttachmentThumbnail() {
        let attachment = AttachmentDTO(media: "https://cdn.example.com/file.pdf", thumbnail: "https://cdn.example.com/thumb.jpg")
        let dto = DriveFileDTO(
            id: "f1", fileName: "test.pdf", fileExtension: "pdf",
            fileSizeBytes: 1024, mimeType: "application/pdf",
            folderId: nil, filePath: nil, description: nil,
            createdBy: "user1", createdOn: 0, updatedOn: 0,
            deletedOn: nil, attachments: [attachment]
        )
        let file = DriveMapper.toDomain(dto)
        XCTAssertEqual(file.thumbnailUrl, "https://cdn.example.com/thumb.jpg")
    }

    func testNilFieldsDefaults() {
        let dto = DriveFileDTO(
            id: "f1", fileName: "file.bin", fileExtension: nil,
            fileSizeBytes: nil, mimeType: nil,
            folderId: nil, filePath: nil, description: nil,
            createdBy: nil, createdOn: nil, updatedOn: nil,
            deletedOn: nil, attachments: nil
        )
        let file = DriveMapper.toDomain(dto)
        XCTAssertEqual(file.fileExtension, "")
        XCTAssertEqual(file.fileSizeBytes, 0)
        XCTAssertEqual(file.mimeType, "application/octet-stream")
        XCTAssertEqual(file.createdBy, "")
        XCTAssertEqual(file.createdOn, 0)
        XCTAssertEqual(file.updatedOn, 0)
        XCTAssertNil(file.folderId)
        XCTAssertNil(file.filePath)
        XCTAssertNil(file.description)
        XCTAssertNil(file.thumbnailUrl)
    }

    // MARK: - Folder DTO to Domain

    func testFolderMapping() {
        let dto = DriveFolderDTO(
            id: "d1", folderName: "Documents", parentFolderId: nil,
            description: nil, createdBy: "user1", createdOn: 1700000000,
            updatedOn: 1700000001, deletedOn: nil, fileCount: 5, folderCount: 2
        )
        let folder = DriveMapper.toDomain(dto)
        XCTAssertEqual(folder.id, "d1")
        XCTAssertEqual(folder.folderName, "Documents")
        XCTAssertNil(folder.parentFolderId)
        XCTAssertEqual(folder.createdBy, "user1")
        XCTAssertEqual(folder.createdOn, 1700000000)
        XCTAssertEqual(folder.updatedOn, 1700000001)
        XCTAssertEqual(folder.fileCount, 5)
        XCTAssertEqual(folder.folderCount, 2)
        XCTAssertFalse(folder.isFavorite)
    }

    func testFolderMapping_withFavorite() {
        let dto = DriveFolderDTO(
            id: "d1", folderName: "Favorites", parentFolderId: "parent1",
            description: "Favorite folder", createdBy: "user1", createdOn: 0,
            updatedOn: 0, deletedOn: nil, fileCount: 10, folderCount: 3
        )
        let folder = DriveMapper.toDomain(dto, isFavorite: true)
        XCTAssertTrue(folder.isFavorite)
        XCTAssertEqual(folder.parentFolderId, "parent1")
        XCTAssertEqual(folder.description, "Favorite folder")
    }

    func testFolderMapping_nilFieldsDefaults() {
        let dto = DriveFolderDTO(
            id: "d1", folderName: "Empty", parentFolderId: nil,
            description: nil, createdBy: nil, createdOn: nil,
            updatedOn: nil, deletedOn: nil, fileCount: nil, folderCount: nil
        )
        let folder = DriveMapper.toDomain(dto)
        XCTAssertEqual(folder.createdBy, "")
        XCTAssertEqual(folder.createdOn, 0)
        XCTAssertEqual(folder.updatedOn, 0)
        XCTAssertEqual(folder.fileCount, 0)
        XCTAssertEqual(folder.folderCount, 0)
    }

    // MARK: - Contents DTO to Domain

    func testContentsMapping_withItems() {
        let folderItem = DriveContentItemDTO(
            id: "d1", type: "folder", isFolder: true, name: "MyFolder",
            dateModified: nil, size: nil,
            folderName: "MyFolder", folderPath: nil, parentFolderId: nil,
            fileName: nil, fileExtension: nil, fileSizeBytes: nil,
            mimeType: nil, folderId: nil, filePath: nil, attachments: nil,
            description: nil, createdBy: "u1", createdOn: 1000,
            updatedOn: 2000, deletedOn: nil, fileCount: 3, folderCount: 1
        )
        let fileItem = DriveContentItemDTO(
            id: "f1", type: "file", isFolder: false, name: "file.txt",
            dateModified: nil, size: 500,
            folderName: nil, folderPath: nil, parentFolderId: nil,
            fileName: "file.txt", fileExtension: "txt", fileSizeBytes: 500,
            mimeType: "text/plain", folderId: "d1", filePath: "/file.txt",
            attachments: nil, description: nil, createdBy: "u1",
            createdOn: 1000, updatedOn: 2000, deletedOn: nil,
            fileCount: nil, folderCount: nil
        )
        let dto = DriveContentsDTO(
            items: [folderItem, fileItem],
            pagination: nil,
            counts: CountsDTO(folders: 1, files: 1, total: 2)
        )

        let result = DriveMapper.toDomain(
            dto,
            favoriteFileIds: ["f1"],
            favoriteFolderIds: []
        )

        XCTAssertEqual(result.folders.count, 1)
        XCTAssertEqual(result.files.count, 1)
        XCTAssertEqual(result.totalFolders, 1)
        XCTAssertEqual(result.totalFiles, 1)
        XCTAssertTrue(result.files[0].isFavorite)
        XCTAssertFalse(result.folders[0].isFavorite)
        XCTAssertEqual(result.folders[0].folderName, "MyFolder")
        XCTAssertEqual(result.files[0].fileName, "file.txt")
    }

    func testContentsMapping_emptyItems() {
        let dto = DriveContentsDTO(items: [], pagination: nil, counts: nil)

        let result = DriveMapper.toDomain(dto)

        XCTAssertTrue(result.folders.isEmpty)
        XCTAssertTrue(result.files.isEmpty)
        XCTAssertEqual(result.totalFolders, 0)
        XCTAssertEqual(result.totalFiles, 0)
    }

    func testContentsMapping_nilItems() {
        let dto = DriveContentsDTO(items: nil, pagination: nil, counts: nil)

        let result = DriveMapper.toDomain(dto)

        XCTAssertTrue(result.folders.isEmpty)
        XCTAssertTrue(result.files.isEmpty)
    }

    func testContentsMapping_favoriteFolders() {
        let folderItem = DriveContentItemDTO(
            id: "d1", type: "folder", isFolder: true, name: "Fav Folder",
            dateModified: nil, size: nil,
            folderName: "Fav Folder", folderPath: nil, parentFolderId: nil,
            fileName: nil, fileExtension: nil, fileSizeBytes: nil,
            mimeType: nil, folderId: nil, filePath: nil, attachments: nil,
            description: nil, createdBy: nil, createdOn: nil,
            updatedOn: nil, deletedOn: nil, fileCount: nil, folderCount: nil
        )
        let dto = DriveContentsDTO(items: [folderItem], pagination: nil, counts: nil)

        let result = DriveMapper.toDomain(dto, favoriteFolderIds: ["d1"])

        XCTAssertTrue(result.folders[0].isFavorite)
    }

    // MARK: - ShareLink DTO to Domain

    func testShareLinkMapping() {
        let dto = ShareLinkDTO(url: "https://share.example.com/abc", expiresAt: "2025-12-31T00:00:00Z")

        let link = DriveMapper.toDomain(dto)

        XCTAssertEqual(link.url, "https://share.example.com/abc")
        XCTAssertEqual(link.expiresAt, "2025-12-31T00:00:00Z")
    }

    func testShareLinkMapping_nilExpiry() {
        let dto = ShareLinkDTO(url: "https://share.example.com/xyz", expiresAt: nil)

        let link = DriveMapper.toDomain(dto)

        XCTAssertEqual(link.url, "https://share.example.com/xyz")
        XCTAssertNil(link.expiresAt)
    }

    // MARK: - Comment DTO to Domain

    func testCommentMapping() {
        let dto = DriveCommentDTO(
            id: "c1", fileId: "f1", userId: "user1",
            text: "Great document!", createdOn: 1700000000, updatedOn: 1700000001
        )

        let comment = DriveMapper.toDomain(dto)

        XCTAssertEqual(comment.id, "c1")
        XCTAssertEqual(comment.fileId, "f1")
        XCTAssertEqual(comment.userId, "user1")
        XCTAssertEqual(comment.text, "Great document!")
        XCTAssertEqual(comment.createdOn, 1700000000)
    }

    func testCommentMapping_nilFields() {
        let dto = DriveCommentDTO(
            id: "c1", fileId: nil, userId: nil,
            text: "Orphan comment", createdOn: nil, updatedOn: nil
        )

        let comment = DriveMapper.toDomain(dto)

        XCTAssertEqual(comment.fileId, "")
        XCTAssertEqual(comment.userId, "")
        XCTAssertEqual(comment.createdOn, 0)
    }

    // MARK: - Version DTO to Domain

    func testVersionMapping() {
        let dto = DriveVersionDTO(
            id: "v1", fileId: "f1", versionNumber: 3,
            fileSizeBytes: 2048, filePath: "/v3/test.pdf",
            createdBy: "user1", createdOn: 1700000000
        )

        let version = DriveMapper.toDomain(dto)

        XCTAssertEqual(version.id, "v1")
        XCTAssertEqual(version.fileId, "f1")
        XCTAssertEqual(version.versionNumber, 3)
        XCTAssertEqual(version.fileSizeBytes, 2048)
        XCTAssertEqual(version.createdBy, "user1")
        XCTAssertEqual(version.createdOn, 1700000000)
    }

    func testVersionMapping_nilFields() {
        let dto = DriveVersionDTO(
            id: "v1", fileId: nil, versionNumber: nil,
            fileSizeBytes: nil, filePath: nil,
            createdBy: nil, createdOn: nil
        )

        let version = DriveMapper.toDomain(dto)

        XCTAssertEqual(version.fileId, "")
        XCTAssertEqual(version.versionNumber, 0)
        XCTAssertEqual(version.fileSizeBytes, 0)
        XCTAssertEqual(version.createdBy, "")
        XCTAssertEqual(version.createdOn, 0)
    }

    // MARK: - Tag DTO to Domain

    func testTagMapping() {
        let dto = DriveTagDTO(id: "t1", name: "Important", color: "#FF0000", projectId: "proj1")

        let tag = DriveMapper.toDomain(dto)

        XCTAssertEqual(tag.id, "t1")
        XCTAssertEqual(tag.name, "Important")
        XCTAssertEqual(tag.color, "#FF0000")
    }

    func testTagMapping_nilColor() {
        let dto = DriveTagDTO(id: "t1", name: "NoColor", color: nil, projectId: nil)

        let tag = DriveMapper.toDomain(dto)

        XCTAssertEqual(tag.color, "#999999") // default color
    }

    // MARK: - StorageUsage DTO to Domain

    func testStorageUsageMapping() {
        let dto = StorageUsageDTO(usedBytes: 5_000_000, totalBytes: 10_000_000_000, fileCount: 150)

        let storage = DriveMapper.toDomain(dto)

        XCTAssertEqual(storage.usedBytes, 5_000_000)
        XCTAssertEqual(storage.totalBytes, 10_000_000_000)
        XCTAssertEqual(storage.fileCount, 150)
    }

    func testStorageUsageMapping_nilFields() {
        let dto = StorageUsageDTO(usedBytes: nil, totalBytes: nil, fileCount: nil)

        let storage = DriveMapper.toDomain(dto)

        XCTAssertEqual(storage.usedBytes, 0)
        XCTAssertEqual(storage.totalBytes, 0)
        XCTAssertEqual(storage.fileCount, 0)
    }

    // MARK: - Activity DTO to Domain

    func testActivityMapping() {
        let dto = DriveActivityDTO(
            id: "a1", action: "upload",
            itemId: "f1", itemType: "file",
            userId: "user1", details: "Uploaded test.pdf",
            createdOn: 1700000000
        )

        let activity = DriveMapper.toDomain(dto)

        XCTAssertEqual(activity.id, "a1")
        XCTAssertEqual(activity.action, "upload")
        XCTAssertEqual(activity.itemId, "f1")
        XCTAssertEqual(activity.itemType, "file")
        XCTAssertEqual(activity.userId, "user1")
        XCTAssertEqual(activity.details, "Uploaded test.pdf")
        XCTAssertEqual(activity.createdOn, 1700000000)
    }

    func testActivityMapping_nilFields() {
        let dto = DriveActivityDTO(
            id: "a1", action: "delete",
            itemId: nil, itemType: nil,
            userId: nil, details: nil,
            createdOn: nil
        )

        let activity = DriveMapper.toDomain(dto)

        XCTAssertEqual(activity.itemId, "")
        XCTAssertEqual(activity.itemType, "")
        XCTAssertEqual(activity.userId, "")
        XCTAssertNil(activity.details)
        XCTAssertEqual(activity.createdOn, 0)
    }

    // MARK: - FileAccess DTO Tests

    func testFileAccessDTO_allFieldsPopulated() {
        let dto = FileAccessDTO(
            userId: "user1", fileId: "f1",
            canView: true, canEdit: true, canDownload: false
        )
        XCTAssertEqual(dto.userId, "user1")
        XCTAssertEqual(dto.fileId, "f1")
        XCTAssertEqual(dto.canView, true)
        XCTAssertEqual(dto.canEdit, true)
        XCTAssertEqual(dto.canDownload, false)
    }

    func testFileAccessDTO_nilPermissions() {
        let dto = FileAccessDTO(
            userId: "user1", fileId: nil,
            canView: nil, canEdit: nil, canDownload: nil
        )
        XCTAssertNil(dto.fileId)
        XCTAssertNil(dto.canView)
        XCTAssertNil(dto.canEdit)
        XCTAssertNil(dto.canDownload)
    }

    // MARK: - FolderAccess DTO Tests

    func testFolderAccessDTO_allFieldsPopulated() {
        let dto = FolderAccessDTO(
            userId: "user1", folderId: "d1",
            role: "editor", inherited: false
        )
        XCTAssertEqual(dto.userId, "user1")
        XCTAssertEqual(dto.folderId, "d1")
        XCTAssertEqual(dto.role, "editor")
        XCTAssertEqual(dto.inherited, false)
    }

    func testFolderAccessDTO_inheritedAccess() {
        let dto = FolderAccessDTO(
            userId: "user2", folderId: "d2",
            role: "viewer", inherited: true
        )
        XCTAssertTrue(dto.inherited == true)
        XCTAssertEqual(dto.role, "viewer")
    }

    func testFolderAccessDTO_nilFields() {
        let dto = FolderAccessDTO(
            userId: "user1", folderId: nil,
            role: "viewer", inherited: nil
        )
        XCTAssertNil(dto.folderId)
        XCTAssertNil(dto.inherited)
    }

    // MARK: - Trash Mapping

    func testTrashMapping_fileType() {
        let fileDTO = DriveFileDTO(
            id: "f1", fileName: "deleted.pdf", fileExtension: "pdf",
            fileSizeBytes: 1024, mimeType: "application/pdf",
            folderId: nil, filePath: nil, description: nil,
            createdBy: "user1", createdOn: 0, updatedOn: 0,
            deletedOn: 1700000000, attachments: nil
        )
        let trashItem = TrashItemDTO(
            id: "trash1", type: "file", name: "deleted.pdf",
            deletedOn: 1700000000, file: fileDTO, folder: nil
        )

        let item = DriveMapper.toDomain(trashItem)

        if case .file(let file) = item {
            XCTAssertEqual(file.id, "f1")
            XCTAssertEqual(file.fileName, "deleted.pdf")
        } else {
            XCTFail("Expected file item from trash")
        }
    }

    func testTrashMapping_folderType() {
        let folderDTO = DriveFolderDTO(
            id: "d1", folderName: "Old Folder", parentFolderId: nil,
            description: nil, createdBy: "user1", createdOn: 0,
            updatedOn: 0, deletedOn: 1700000000, fileCount: 0, folderCount: 0
        )
        let trashItem = TrashItemDTO(
            id: "trash2", type: "folder", name: "Old Folder",
            deletedOn: 1700000000, file: nil, folder: folderDTO
        )

        let item = DriveMapper.toDomain(trashItem)

        if case .folder(let folder) = item {
            XCTAssertEqual(folder.id, "d1")
            XCTAssertEqual(folder.folderName, "Old Folder")
        } else {
            XCTFail("Expected folder item from trash")
        }
    }

    func testTrashMapping_fallback() {
        // When neither file nor folder DTO is present
        let trashItem = TrashItemDTO(
            id: "trash3", type: "unknown", name: "Mystery",
            deletedOn: 1700000000, file: nil, folder: nil
        )

        let item = DriveMapper.toDomain(trashItem)

        // Should create a minimal file fallback
        if case .file(let file) = item {
            XCTAssertEqual(file.id, "trash3")
            XCTAssertEqual(file.fileName, "Mystery")
        } else {
            XCTFail("Expected fallback file item from trash")
        }
    }
}
