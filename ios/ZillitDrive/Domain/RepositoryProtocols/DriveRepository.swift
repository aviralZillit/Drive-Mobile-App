import Foundation

protocol DriveRepository {
    // Files
    func getFiles(options: [String: String]) async throws -> [DriveFile]
    func getFile(fileId: String) async throws -> DriveFile
    func createFile(data: [String: Any]) async throws -> DriveFile
    func updateFile(fileId: String, data: [String: Any]) async throws -> DriveFile
    func deleteFile(fileId: String) async throws
    func moveFile(fileId: String, targetFolderId: String?) async throws
    func getFileStreamUrl(fileId: String) async throws -> String
    func getFilePreviewUrl(fileId: String) async throws -> String
    func generateShareLink(fileId: String, expiry: String) async throws -> ShareLink

    // Folders
    func getFolders(options: [String: String]) async throws -> [DriveFolder]
    func getFolderContents(fileOptions: [String: String], folderOptions: [String: String]) async throws -> DriveContents
    func forceGetFolderContents(fileOptions: [String: String], folderOptions: [String: String]) async throws -> DriveContents
    func createFolder(data: [String: Any]) async throws -> DriveFolder
    func updateFolder(folderId: String, data: [String: Any]) async throws -> DriveFolder
    func deleteFolder(folderId: String) async throws

    // Cache Invalidation
    func invalidateContentsCache(folderId: String?)
    func invalidateAll()

    // Uploads
    func initiateUpload(fileName: String, fileSizeBytes: Int64, folderId: String?, mimeType: String) async throws -> UploadSessionDTO
    func completeUpload(uploadId: String, parts: [[String: Any]]) async throws -> DriveFile
    func abortUpload(uploadId: String) async throws

    // Trash
    func getTrash() async throws -> [TrashItemDTO]
    func restoreTrashItem(type: String, itemId: String) async throws
    func permanentDeleteTrashItem(type: String, itemId: String) async throws
    func emptyTrash() async throws

    // Favorites
    func toggleFavorite(itemId: String, itemType: String) async throws
    func getFavoriteIds() async throws -> FavoriteIdsDTO

    // Tags
    func getTags() async throws -> [DriveTag]
    func assignTag(tagId: String, itemId: String, itemType: String) async throws
    func removeTag(tagId: String, itemId: String, itemType: String) async throws
    func getItemTags(itemId: String, itemType: String) async throws -> [DriveTag]

    // Comments
    func getComments(fileId: String) async throws -> [DriveComment]
    func addComment(fileId: String, text: String) async throws -> DriveComment
    func deleteComment(commentId: String) async throws

    // Versions
    func getFileVersions(fileId: String) async throws -> [DriveVersion]
    func restoreVersion(fileId: String, versionId: String) async throws

    // Bulk
    func bulkDelete(items: [[String: String]]) async throws
    func bulkMove(items: [[String: String]], targetFolderId: String?) async throws

    // Activity
    func getActivity(options: [String: String]) async throws -> [DriveActivity]

    // Storage
    func getStorageUsage() async throws -> StorageUsage

    // Access
    func getFolderAccess(folderId: String) async throws -> [FolderAccessDTO]
    func updateFolderAccess(folderId: String, entries: [[String: Any]]) async throws
    func getFileAccess(fileId: String) async throws -> [FileAccessDTO]
    func updateFileAccess(fileId: String, entries: [[String: Any]]) async throws

    // Editor
    func getEditorPageToken(fileId: String) async throws -> String
}
