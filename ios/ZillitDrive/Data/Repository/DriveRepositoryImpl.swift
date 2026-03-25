import Foundation

// MARK: - Cache Actor

/// Thread-safe cache storage using Swift actor isolation.
private actor DriveCache {

    // MARK: - Folder Contents Cache

    private var contentsCache: [String: DriveContents] = [:]

    func getCachedContents(forKey key: String) -> DriveContents? {
        contentsCache[key]
    }

    func setCachedContents(_ contents: DriveContents, forKey key: String) {
        contentsCache[key] = contents
    }

    func invalidateContents(folderId: String?) {
        if let folderId {
            // Remove entries whose cache key contains the folderId
            contentsCache = contentsCache.filter { !$0.key.contains("folderId=\(folderId)") }
        } else {
            contentsCache.removeAll()
        }
    }

    // MARK: - File Detail Cache

    private var fileCache: [String: DriveFile] = [:]

    func getCachedFile(fileId: String) -> DriveFile? {
        fileCache[fileId]
    }

    func setCachedFile(_ file: DriveFile, fileId: String) {
        fileCache[fileId] = file
    }

    func invalidateFiles() {
        fileCache.removeAll()
    }

    // MARK: - Favorite IDs Cache

    private var favoriteIdsCache: FavoriteIdsDTO?
    private var favoriteIdsCacheTimestamp: Date?
    private let favoriteIdsTTL: TimeInterval = 60 // 60 seconds

    func getCachedFavoriteIds() -> FavoriteIdsDTO? {
        guard let cache = favoriteIdsCache,
              let timestamp = favoriteIdsCacheTimestamp,
              Date().timeIntervalSince(timestamp) < favoriteIdsTTL else {
            return nil
        }
        return cache
    }

    func setCachedFavoriteIds(_ ids: FavoriteIdsDTO) {
        favoriteIdsCache = ids
        favoriteIdsCacheTimestamp = Date()
    }

    func invalidateFavoriteIds() {
        favoriteIdsCache = nil
        favoriteIdsCacheTimestamp = nil
    }

    // MARK: - Full Invalidation

    func invalidateAll() {
        contentsCache.removeAll()
        fileCache.removeAll()
        favoriteIdsCache = nil
        favoriteIdsCacheTimestamp = nil
    }
}

// MARK: - Repository Implementation

final class DriveRepositoryImpl: DriveRepository {

    private let cache = DriveCache()

    // MARK: - Cache Key Helpers

    private func contentsCacheKey(from options: [String: String]) -> String {
        options.sorted(by: { $0.key < $1.key })
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: "&")
    }

    // MARK: - Files

    func getFiles(options: [String: String]) async throws -> [DriveFile] {
        let dtos = try await DriveEndpoints.getFiles(options: options)
        return dtos.map { DriveMapper.toDomain($0) }
    }

    func getFile(fileId: String) async throws -> DriveFile {
        if let cached = await cache.getCachedFile(fileId: fileId) {
            return cached
        }
        let dto = try await DriveEndpoints.getFile(fileId: fileId)
        let file = DriveMapper.toDomain(dto)
        await cache.setCachedFile(file, fileId: fileId)
        return file
    }

    func createFile(data: [String: Any]) async throws -> DriveFile {
        let dto = try await DriveEndpoints.createFile(data: data)
        let file = DriveMapper.toDomain(dto)
        let folderId = data["folderId"] as? String
        await cache.invalidateContents(folderId: folderId)
        return file
    }

    func updateFile(fileId: String, data: [String: Any]) async throws -> DriveFile {
        let dto = try await DriveEndpoints.updateFile(fileId: fileId, data: data)
        let file = DriveMapper.toDomain(dto)
        await cache.setCachedFile(file, fileId: fileId)
        return file
    }

    func deleteFile(fileId: String) async throws {
        try await DriveEndpoints.deleteFile(fileId: fileId)
        await cache.invalidateContents(folderId: nil)
    }

    func moveFile(fileId: String, targetFolderId: String?) async throws {
        try await DriveEndpoints.moveFile(fileId: fileId, targetFolderId: targetFolderId)
        await cache.invalidateContents(folderId: nil)
    }

    func getFileStreamUrl(fileId: String) async throws -> String {
        try await DriveEndpoints.getFileStreamUrl(fileId: fileId)
    }

    func getFilePreviewUrl(fileId: String) async throws -> String {
        try await DriveEndpoints.getFilePreviewUrl(fileId: fileId)
    }

    func generateShareLink(fileId: String, expiry: String) async throws -> ShareLink {
        let dto = try await DriveEndpoints.generateShareLink(fileId: fileId, expiry: expiry)
        return DriveMapper.toDomain(dto)
    }

    // MARK: - Folders

    func getFolders(options: [String: String]) async throws -> [DriveFolder] {
        let dtos = try await DriveEndpoints.getFolders(options: options)
        return dtos.map { DriveMapper.toDomain($0) }
    }

    func getFolderContents(fileOptions: [String: String], folderOptions: [String: String]) async throws -> DriveContents {
        let key = contentsCacheKey(from: fileOptions)
        if let cached = await cache.getCachedContents(forKey: key) {
            return cached
        }
        return try await fetchContentsFromSeparateEndpoints(fileOptions: fileOptions, folderOptions: folderOptions, cacheKey: key)
    }

    func forceGetFolderContents(fileOptions: [String: String], folderOptions: [String: String]) async throws -> DriveContents {
        let key = contentsCacheKey(from: fileOptions)
        return try await fetchContentsFromSeparateEndpoints(fileOptions: fileOptions, folderOptions: folderOptions, cacheKey: key)
    }

    /// Fetches files + folders from separate endpoints
    /// Web: /files?folder_id=xxx + /folders?parent_folder_id=xxx (different param names!)
    private func fetchContentsFromSeparateEndpoints(fileOptions: [String: String], folderOptions: [String: String], cacheKey: String) async throws -> DriveContents {
        async let filesTask = DriveEndpoints.getFiles(options: fileOptions)
        async let foldersTask = DriveEndpoints.getFolders(options: folderOptions)

        let (fileDTOs, folderDTOs) = try await (filesTask, foldersTask)

        let files = fileDTOs.map { DriveMapper.toDomain($0) }
        let folders = folderDTOs.map { DriveMapper.toDomain($0) }

        let contents = DriveContents(
            folders: folders,
            files: files,
            totalFolders: folders.count,
            totalFiles: files.count
        )
        await cache.setCachedContents(contents, forKey: cacheKey)
        return contents
    }

    func createFolder(data: [String: Any]) async throws -> DriveFolder {
        let dto = try await DriveEndpoints.createFolder(data: data)
        let folder = DriveMapper.toDomain(dto)
        let parentId = data["parentFolderId"] as? String
        await cache.invalidateContents(folderId: parentId)
        return folder
    }

    func updateFolder(folderId: String, data: [String: Any]) async throws -> DriveFolder {
        let dto = try await DriveEndpoints.updateFolder(folderId: folderId, data: data)
        return DriveMapper.toDomain(dto)
    }

    func deleteFolder(folderId: String) async throws {
        try await DriveEndpoints.deleteFolder(folderId: folderId)
        await cache.invalidateContents(folderId: nil)
    }

    // MARK: - Cache Invalidation

    func invalidateContentsCache(folderId: String?) {
        Task { await cache.invalidateContents(folderId: folderId) }
    }

    func invalidateAll() {
        Task { await cache.invalidateAll() }
    }

    // MARK: - Uploads

    func initiateUpload(fileName: String, fileSizeBytes: Int64, folderId: String?, mimeType: String) async throws -> UploadSessionDTO {
        try await DriveEndpoints.initiateUpload(
            fileName: fileName, fileSizeBytes: fileSizeBytes,
            folderId: folderId, mimeType: mimeType
        )
    }

    func completeUpload(uploadId: String, parts: [[String: Any]]) async throws -> DriveFile {
        let dto = try await DriveEndpoints.completeUpload(uploadId: uploadId, parts: parts)
        let file = DriveMapper.toDomain(dto)
        await cache.invalidateContents(folderId: nil)
        return file
    }

    func abortUpload(uploadId: String) async throws {
        try await DriveEndpoints.abortUpload(uploadId: uploadId)
    }

    // MARK: - Trash

    func getTrash() async throws -> [TrashItemDTO] {
        try await DriveEndpoints.getTrash()
    }

    func restoreTrashItem(type: String, itemId: String) async throws {
        try await DriveEndpoints.restoreTrashItem(type: type, itemId: itemId)
        await cache.invalidateContents(folderId: nil)
    }

    func permanentDeleteTrashItem(type: String, itemId: String) async throws {
        try await DriveEndpoints.permanentDeleteTrashItem(type: type, itemId: itemId)
    }

    func emptyTrash() async throws {
        try await DriveEndpoints.emptyTrash()
    }

    // MARK: - Favorites

    func toggleFavorite(itemId: String, itemType: String) async throws {
        try await DriveEndpoints.toggleFavorite(itemId: itemId, itemType: itemType)
        await cache.invalidateFavoriteIds()
    }

    func getFavoriteIds() async throws -> FavoriteIdsDTO {
        if let cached = await cache.getCachedFavoriteIds() {
            return cached
        }
        let result = try await DriveEndpoints.getFavoriteIds()
        await cache.setCachedFavoriteIds(result)
        return result
    }

    // MARK: - Tags

    func getTags() async throws -> [DriveTag] {
        let dtos = try await DriveEndpoints.getTags()
        return dtos.map { DriveMapper.toDomain($0) }
    }

    func assignTag(tagId: String, itemId: String, itemType: String) async throws {
        try await DriveEndpoints.assignTag(tagId: tagId, itemId: itemId, itemType: itemType)
    }

    func removeTag(tagId: String, itemId: String, itemType: String) async throws {
        try await DriveEndpoints.removeTag(tagId: tagId, itemId: itemId, itemType: itemType)
    }

    func getItemTags(itemId: String, itemType: String) async throws -> [DriveTag] {
        let dtos = try await DriveEndpoints.getItemTags(itemId: itemId, itemType: itemType)
        return dtos.map { DriveMapper.toDomain($0) }
    }

    // MARK: - Comments

    func getComments(fileId: String) async throws -> [DriveComment] {
        let dtos = try await DriveEndpoints.getComments(fileId: fileId)
        return dtos.map { DriveMapper.toDomain($0) }
    }

    func addComment(fileId: String, text: String) async throws -> DriveComment {
        let dto = try await DriveEndpoints.addComment(fileId: fileId, text: text)
        return DriveMapper.toDomain(dto)
    }

    func deleteComment(commentId: String) async throws {
        try await DriveEndpoints.deleteComment(commentId: commentId)
    }

    // MARK: - Versions

    func getFileVersions(fileId: String) async throws -> [DriveVersion] {
        let dtos = try await DriveEndpoints.getFileVersions(fileId: fileId)
        return dtos.map { DriveMapper.toDomain($0) }
    }

    func restoreVersion(fileId: String, versionId: String) async throws {
        try await DriveEndpoints.restoreVersion(fileId: fileId, versionId: versionId)
    }

    // MARK: - Bulk

    func bulkDelete(items: [[String: String]]) async throws {
        try await DriveEndpoints.bulkDelete(items: items)
        await cache.invalidateContents(folderId: nil)
    }

    func bulkMove(items: [[String: String]], targetFolderId: String?) async throws {
        try await DriveEndpoints.bulkMove(items: items, targetFolderId: targetFolderId)
        await cache.invalidateContents(folderId: nil)
    }

    // MARK: - Activity

    func getActivity(options: [String: String]) async throws -> [DriveActivity] {
        let dtos = try await DriveEndpoints.getActivity(options: options)
        return dtos.map { DriveMapper.toDomain($0) }
    }

    // MARK: - Storage

    func getStorageUsage() async throws -> StorageUsage {
        let dto = try await DriveEndpoints.getStorageUsage()
        return DriveMapper.toDomain(dto)
    }

    // MARK: - Access

    func getFolderAccess(folderId: String) async throws -> [FolderAccessDTO] {
        try await DriveEndpoints.getFolderAccess(folderId: folderId)
    }

    func updateFolderAccess(folderId: String, entries: [[String: Any]]) async throws {
        try await DriveEndpoints.updateFolderAccess(folderId: folderId, entries: entries)
    }

    func getFileAccess(fileId: String) async throws -> [FileAccessDTO] {
        try await DriveEndpoints.getFileAccess(fileId: fileId)
    }

    func updateFileAccess(fileId: String, entries: [[String: Any]]) async throws {
        try await DriveEndpoints.updateFileAccess(fileId: fileId, entries: entries)
    }

    // MARK: - Editor

    func getEditorPageToken(fileId: String) async throws -> String {
        try await DriveEndpoints.getEditorPageToken(fileId: fileId)
    }
}
