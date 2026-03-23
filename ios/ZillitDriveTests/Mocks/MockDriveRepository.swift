import Foundation
@testable import ZillitDrive

enum MockError: Error, LocalizedError {
    case forced
    case custom(String)

    var errorDescription: String? {
        switch self {
        case .forced: return "Forced mock error"
        case .custom(let msg): return msg
        }
    }
}

class MockDriveRepository: DriveRepository {

    // MARK: - Configurable Results

    var folderContentsResult: DriveContents = DriveContents(folders: [], files: [], totalFolders: 0, totalFiles: 0)
    var folderContentsError: Error?
    var favoriteIdsResult: FavoriteIdsDTO = FavoriteIdsDTO(fileIds: [], folderIds: [])
    var favoriteIdsError: Error?
    var toggleFavoriteError: Error?
    var deleteFileError: Error?
    var deleteFolderError: Error?
    var createFolderResult: DriveFolder?
    var createFolderError: Error?
    var shareLinkResult: ShareLink?
    var shareLinkError: Error?
    var streamUrlResult: String = "https://example.com/file.pdf"
    var streamUrlError: Error?
    var fileAccessResult: [FileAccessDTO] = []
    var fileAccessError: Error?
    var folderAccessResult: [FolderAccessDTO] = []
    var folderAccessError: Error?
    var commentsResult: [DriveComment] = []
    var commentsError: Error?
    var addCommentResult: DriveComment?
    var addCommentError: Error?
    var deleteCommentError: Error?
    var versionsResult: [DriveVersion] = []
    var versionsError: Error?
    var restoreVersionError: Error?
    var tagsResult: [DriveTag] = []
    var tagsError: Error?
    var removeTagError: Error?
    var storageResult: StorageUsage = StorageUsage(usedBytes: 0, totalBytes: 0, fileCount: 0)
    var storageError: Error?
    var activityResult: [DriveActivity] = []
    var activityError: Error?
    var filesResult: [DriveFile] = []
    var filesError: Error?
    var fileResult: DriveFile?
    var fileError: Error?
    var createFileResult: DriveFile?
    var createFileError: Error?
    var updateFileResult: DriveFile?
    var updateFileError: Error?
    var moveFileError: Error?
    var foldersResult: [DriveFolder] = []
    var foldersError: Error?
    var updateFolderResult: DriveFolder?
    var updateFolderError: Error?
    var uploadSessionResult: UploadSessionDTO?
    var uploadSessionError: Error?
    var completeUploadResult: DriveFile?
    var completeUploadError: Error?
    var abortUploadError: Error?
    var trashResult: [TrashItemDTO] = []
    var trashError: Error?
    var restoreTrashError: Error?
    var permanentDeleteTrashError: Error?
    var emptyTrashError: Error?
    var assignTagError: Error?
    var bulkDeleteError: Error?
    var bulkMoveError: Error?
    var updateFileAccessError: Error?
    var updateFolderAccessError: Error?

    // MARK: - Call Tracking

    var getFilesCalled = 0
    var getFileCalled = 0
    var createFileCalled = 0
    var updateFileCalled = 0
    var deleteFileCalled = 0
    var moveFileCalled = 0
    var getFileStreamUrlCalled = 0
    var generateShareLinkCalled = 0
    var getFoldersCalled = 0
    var getFolderContentsCalled = 0
    var forceGetFolderContentsCalled = 0
    var createFolderCalled = 0
    var updateFolderCalled = 0
    var deleteFolderCalled = 0
    var invalidateContentsCacheCalled = 0
    var invalidateAllCalled = 0
    var initiateUploadCalled = 0
    var completeUploadCalled = 0
    var abortUploadCalled = 0
    var getTrashCalled = 0
    var restoreTrashItemCalled = 0
    var permanentDeleteTrashItemCalled = 0
    var emptyTrashCalled = 0
    var toggleFavoriteCalled = 0
    var getFavoriteIdsCalled = 0
    var getTagsCalled = 0
    var assignTagCalled = 0
    var removeTagCalled = 0
    var getItemTagsCalled = 0
    var getCommentsCalled = 0
    var addCommentCalled = 0
    var deleteCommentCalled = 0
    var getFileVersionsCalled = 0
    var restoreVersionCalled = 0
    var bulkDeleteCalled = 0
    var bulkMoveCalled = 0
    var getActivityCalled = 0
    var getStorageUsageCalled = 0
    var getFolderAccessCalled = 0
    var updateFolderAccessCalled = 0
    var getFileAccessCalled = 0
    var updateFileAccessCalled = 0

    // MARK: - Captured Arguments

    var lastFolderContentsOptions: [String: String]?
    var lastCreateFolderData: [String: Any]?
    var lastToggleFavoriteItemId: String?
    var lastToggleFavoriteItemType: String?
    var lastDeleteFileId: String?
    var lastDeleteFolderId: String?
    var lastMoveFileId: String?
    var lastMoveTargetFolderId: String?
    var lastBulkMoveItems: [[String: String]]?
    var lastBulkMoveTargetFolderId: String?
    var lastShareLinkFileId: String?
    var lastShareLinkExpiry: String?
    var lastAddCommentFileId: String?
    var lastAddCommentText: String?
    var lastDeleteCommentId: String?
    var lastRestoreVersionFileId: String?
    var lastRestoreVersionId: String?
    var lastRemoveTagId: String?
    var lastRemoveTagItemId: String?
    var lastUpdateFileAccessFileId: String?
    var lastUpdateFileAccessEntries: [[String: Any]]?
    var lastUpdateFolderAccessFolderId: String?
    var lastUpdateFolderAccessEntries: [[String: Any]]?
    var lastInvalidateFolderId: String?

    // MARK: - Protocol Implementation

    func getFiles(options: [String: String]) async throws -> [DriveFile] {
        getFilesCalled += 1
        if let error = filesError { throw error }
        return filesResult
    }

    func getFile(fileId: String) async throws -> DriveFile {
        getFileCalled += 1
        if let error = fileError { throw error }
        guard let file = fileResult else { throw MockError.custom("No file configured") }
        return file
    }

    func createFile(data: [String: Any]) async throws -> DriveFile {
        createFileCalled += 1
        if let error = createFileError { throw error }
        guard let file = createFileResult else { throw MockError.custom("No file configured") }
        return file
    }

    func updateFile(fileId: String, data: [String: Any]) async throws -> DriveFile {
        updateFileCalled += 1
        if let error = updateFileError { throw error }
        guard let file = updateFileResult else { throw MockError.custom("No file configured") }
        return file
    }

    func deleteFile(fileId: String) async throws {
        deleteFileCalled += 1
        lastDeleteFileId = fileId
        if let error = deleteFileError { throw error }
    }

    func moveFile(fileId: String, targetFolderId: String?) async throws {
        moveFileCalled += 1
        lastMoveFileId = fileId
        lastMoveTargetFolderId = targetFolderId
        if let error = moveFileError { throw error }
    }

    func getFileStreamUrl(fileId: String) async throws -> String {
        getFileStreamUrlCalled += 1
        if let error = streamUrlError { throw error }
        return streamUrlResult
    }

    var getFilePreviewUrlCalled = 0
    var previewUrlResult = "https://example.com/preview"
    var previewUrlError: Error?

    func getFilePreviewUrl(fileId: String) async throws -> String {
        getFilePreviewUrlCalled += 1
        if let error = previewUrlError { throw error }
        return previewUrlResult
    }

    func generateShareLink(fileId: String, expiry: String) async throws -> ShareLink {
        generateShareLinkCalled += 1
        lastShareLinkFileId = fileId
        lastShareLinkExpiry = expiry
        if let error = shareLinkError { throw error }
        guard let link = shareLinkResult else { throw MockError.custom("No share link configured") }
        return link
    }

    func getFolders(options: [String: String]) async throws -> [DriveFolder] {
        getFoldersCalled += 1
        if let error = foldersError { throw error }
        return foldersResult
    }

    func getFolderContents(options: [String: String]) async throws -> DriveContents {
        getFolderContentsCalled += 1
        lastFolderContentsOptions = options
        if let error = folderContentsError { throw error }
        return folderContentsResult
    }

    func forceGetFolderContents(options: [String: String]) async throws -> DriveContents {
        forceGetFolderContentsCalled += 1
        lastFolderContentsOptions = options
        if let error = folderContentsError { throw error }
        return folderContentsResult
    }

    func createFolder(data: [String: Any]) async throws -> DriveFolder {
        createFolderCalled += 1
        lastCreateFolderData = data
        if let error = createFolderError { throw error }
        guard let folder = createFolderResult else {
            return DriveFolder(
                id: "new-folder", folderName: data["folder_name"] as? String ?? "",
                parentFolderId: data["parent_folder_id"] as? String,
                description: nil, createdBy: "user1", createdOn: 0, updatedOn: 0,
                isFavorite: false, fileCount: 0, folderCount: 0
            )
        }
        return folder
    }

    func updateFolder(folderId: String, data: [String: Any]) async throws -> DriveFolder {
        updateFolderCalled += 1
        if let error = updateFolderError { throw error }
        guard let folder = updateFolderResult else { throw MockError.custom("No folder configured") }
        return folder
    }

    func deleteFolder(folderId: String) async throws {
        deleteFolderCalled += 1
        lastDeleteFolderId = folderId
        if let error = deleteFolderError { throw error }
    }

    func invalidateContentsCache(folderId: String?) {
        invalidateContentsCacheCalled += 1
        lastInvalidateFolderId = folderId
    }

    func invalidateAll() {
        invalidateAllCalled += 1
    }

    func initiateUpload(fileName: String, fileSizeBytes: Int64, folderId: String?, mimeType: String) async throws -> UploadSessionDTO {
        initiateUploadCalled += 1
        if let error = uploadSessionError { throw error }
        guard let session = uploadSessionResult else { throw MockError.custom("No upload session configured") }
        return session
    }

    func completeUpload(uploadId: String, parts: [[String: Any]]) async throws -> DriveFile {
        completeUploadCalled += 1
        if let error = completeUploadError { throw error }
        guard let file = completeUploadResult else { throw MockError.custom("No file configured") }
        return file
    }

    func abortUpload(uploadId: String) async throws {
        abortUploadCalled += 1
        if let error = abortUploadError { throw error }
    }

    func getTrash() async throws -> [TrashItemDTO] {
        getTrashCalled += 1
        if let error = trashError { throw error }
        return trashResult
    }

    func restoreTrashItem(type: String, itemId: String) async throws {
        restoreTrashItemCalled += 1
        if let error = restoreTrashError { throw error }
    }

    func permanentDeleteTrashItem(type: String, itemId: String) async throws {
        permanentDeleteTrashItemCalled += 1
        if let error = permanentDeleteTrashError { throw error }
    }

    func emptyTrash() async throws {
        emptyTrashCalled += 1
        if let error = emptyTrashError { throw error }
    }

    func toggleFavorite(itemId: String, itemType: String) async throws {
        toggleFavoriteCalled += 1
        lastToggleFavoriteItemId = itemId
        lastToggleFavoriteItemType = itemType
        if let error = toggleFavoriteError { throw error }
    }

    func getFavoriteIds() async throws -> FavoriteIdsDTO {
        getFavoriteIdsCalled += 1
        if let error = favoriteIdsError { throw error }
        return favoriteIdsResult
    }

    func getTags() async throws -> [DriveTag] {
        getTagsCalled += 1
        if let error = tagsError { throw error }
        return tagsResult
    }

    func assignTag(tagId: String, itemId: String, itemType: String) async throws {
        assignTagCalled += 1
        if let error = assignTagError { throw error }
    }

    func removeTag(tagId: String, itemId: String, itemType: String) async throws {
        removeTagCalled += 1
        lastRemoveTagId = tagId
        lastRemoveTagItemId = itemId
        if let error = removeTagError { throw error }
    }

    func getItemTags(itemId: String, itemType: String) async throws -> [DriveTag] {
        getItemTagsCalled += 1
        if let error = tagsError { throw error }
        return tagsResult
    }

    func getComments(fileId: String) async throws -> [DriveComment] {
        getCommentsCalled += 1
        if let error = commentsError { throw error }
        return commentsResult
    }

    func addComment(fileId: String, text: String) async throws -> DriveComment {
        addCommentCalled += 1
        lastAddCommentFileId = fileId
        lastAddCommentText = text
        if let error = addCommentError { throw error }
        guard let comment = addCommentResult else {
            return DriveComment(id: "new-comment", fileId: fileId, userId: "user1", text: text, createdOn: Int64(Date().timeIntervalSince1970 * 1000))
        }
        return comment
    }

    func deleteComment(commentId: String) async throws {
        deleteCommentCalled += 1
        lastDeleteCommentId = commentId
        if let error = deleteCommentError { throw error }
    }

    func getFileVersions(fileId: String) async throws -> [DriveVersion] {
        getFileVersionsCalled += 1
        if let error = versionsError { throw error }
        return versionsResult
    }

    func restoreVersion(fileId: String, versionId: String) async throws {
        restoreVersionCalled += 1
        lastRestoreVersionFileId = fileId
        lastRestoreVersionId = versionId
        if let error = restoreVersionError { throw error }
    }

    func bulkDelete(items: [[String: String]]) async throws {
        bulkDeleteCalled += 1
        if let error = bulkDeleteError { throw error }
    }

    func bulkMove(items: [[String: String]], targetFolderId: String?) async throws {
        bulkMoveCalled += 1
        lastBulkMoveItems = items
        lastBulkMoveTargetFolderId = targetFolderId
        if let error = bulkMoveError { throw error }
    }

    func getActivity(options: [String: String]) async throws -> [DriveActivity] {
        getActivityCalled += 1
        if let error = activityError { throw error }
        return activityResult
    }

    func getStorageUsage() async throws -> StorageUsage {
        getStorageUsageCalled += 1
        if let error = storageError { throw error }
        return storageResult
    }

    func getFolderAccess(folderId: String) async throws -> [FolderAccessDTO] {
        getFolderAccessCalled += 1
        if let error = folderAccessError { throw error }
        return folderAccessResult
    }

    func updateFolderAccess(folderId: String, entries: [[String: Any]]) async throws {
        updateFolderAccessCalled += 1
        lastUpdateFolderAccessFolderId = folderId
        lastUpdateFolderAccessEntries = entries
        if let error = updateFolderAccessError { throw error }
    }

    func getFileAccess(fileId: String) async throws -> [FileAccessDTO] {
        getFileAccessCalled += 1
        if let error = fileAccessError { throw error }
        return fileAccessResult
    }

    func updateFileAccess(fileId: String, entries: [[String: Any]]) async throws {
        updateFileAccessCalled += 1
        lastUpdateFileAccessFileId = fileId
        lastUpdateFileAccessEntries = entries
        if let error = updateFileAccessError { throw error }
    }
}
