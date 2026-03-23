import Foundation

/// All Drive API endpoints matching the web's driveApi.js (69 endpoints).
enum DriveEndpoints {
    private static let api = APIClient.shared

    // MARK: - Files

    static func getFiles(options: [String: String]) async throws -> [DriveFileDTO] {
        let response: APIResponse<[DriveFileDTO]> = try await api.request(
            endpoint: "files", queryParams: options
        )
        return response.data ?? []
    }

    static func getFile(fileId: String) async throws -> DriveFileDTO {
        let response: APIResponse<DriveFileDTO> = try await api.request(endpoint: "files/\(fileId)")
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    static func createFile(data: [String: Any]) async throws -> DriveFileDTO {
        let response: APIResponse<DriveFileDTO> = try await api.request(
            endpoint: "files", method: .post, body: data
        )
        guard let result = response.data else { throw APIError.invalidResponse }
        return result
    }

    static func updateFile(fileId: String, data: [String: Any]) async throws -> DriveFileDTO {
        let response: APIResponse<DriveFileDTO> = try await api.request(
            endpoint: "files/\(fileId)", method: .put, body: data
        )
        guard let result = response.data else { throw APIError.invalidResponse }
        return result
    }

    static func deleteFile(fileId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "files/\(fileId)", method: .delete
        )
    }

    static func moveFile(fileId: String, targetFolderId: String?) async throws {
        let body: [String: Any] = ["target_folder_id": targetFolderId ?? NSNull()]
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "files/\(fileId)/move", method: .put, body: body
        )
    }

    static func getFileStreamUrl(fileId: String) async throws -> String {
        let response: APIResponse<StreamUrlDTO> = try await api.request(
            endpoint: "files/\(fileId)/stream"
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data.url
    }

    /// Preview URL - only requires VIEW permission (not download)
    static func getFilePreviewUrl(fileId: String) async throws -> String {
        let response: APIResponse<StreamUrlDTO> = try await api.request(
            endpoint: "files/\(fileId)/preview"
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data.url
    }

    static func generateShareLink(fileId: String, expiry: String = "24h") async throws -> ShareLinkDTO {
        let response: APIResponse<ShareLinkDTO> = try await api.request(
            endpoint: "files/\(fileId)/share-link", method: .post, body: ["expiry": expiry]
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    // MARK: - Folders

    static func getFolders(options: [String: String]) async throws -> [DriveFolderDTO] {
        let response: APIResponse<[DriveFolderDTO]> = try await api.request(
            endpoint: "folders", queryParams: options
        )
        return response.data ?? []
    }

    static func getFolderContents(options: [String: String]) async throws -> DriveContentsDTO {
        let response: APIResponse<DriveContentsDTO> = try await api.request(
            endpoint: "folders/contents", queryParams: options
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    static func createFolder(data: [String: Any]) async throws -> DriveFolderDTO {
        let response: APIResponse<DriveFolderDTO> = try await api.request(
            endpoint: "folders", method: .post, body: data
        )
        guard let result = response.data else { throw APIError.invalidResponse }
        return result
    }

    static func updateFolder(folderId: String, data: [String: Any]) async throws -> DriveFolderDTO {
        let response: APIResponse<DriveFolderDTO> = try await api.request(
            endpoint: "folders/\(folderId)", method: .put, body: data
        )
        guard let result = response.data else { throw APIError.invalidResponse }
        return result
    }

    static func deleteFolder(folderId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "folders/\(folderId)?force=false", method: .delete
        )
    }

    // MARK: - Uploads

    static func initiateUpload(
        fileName: String, fileSizeBytes: Int64, folderId: String?, mimeType: String
    ) async throws -> UploadSessionDTO {
        var body: [String: Any] = [
            "file_name": fileName,
            "file_size_bytes": fileSizeBytes,
            "mime_type": mimeType
        ]
        if let folderId = folderId { body["folder_id"] = folderId }

        let response: APIResponse<UploadSessionDTO> = try await api.request(
            endpoint: "uploads", method: .post, body: body
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    static func completeUpload(uploadId: String, parts: [[String: Any]]) async throws -> DriveFileDTO {
        let response: APIResponse<DriveFileDTO> = try await api.request(
            endpoint: "uploads/\(uploadId)/complete", method: .post, body: ["parts": parts]
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    static func abortUpload(uploadId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "uploads/\(uploadId)", method: .delete
        )
    }

    // MARK: - Trash

    static func getTrash() async throws -> APIResponse<[TrashItemDTO]> {
        try await api.request(endpoint: "trash")
    }

    static func restoreTrashItem(type: String, itemId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "trash/\(type)/\(itemId)/restore", method: .post
        )
    }

    static func permanentDeleteTrashItem(type: String, itemId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "trash/\(type)/\(itemId)", method: .delete
        )
    }

    static func emptyTrash() async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "trash", method: .delete
        )
    }

    // MARK: - Favorites

    static func toggleFavorite(itemId: String, itemType: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "favorites/toggle", method: .post,
            body: ["item_id": itemId, "item_type": itemType]
        )
    }

    static func getFavoriteIds() async throws -> FavoriteIdsDTO {
        let response: APIResponse<FavoriteIdsDTO> = try await api.request(endpoint: "favorites/ids")
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    // MARK: - Tags

    static func getTags() async throws -> [DriveTagDTO] {
        let response: APIResponse<[DriveTagDTO]> = try await api.request(endpoint: "tags")
        return response.data ?? []
    }

    static func assignTag(tagId: String, itemId: String, itemType: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "tags/assign", method: .post,
            body: ["tag_id": tagId, "item_id": itemId, "item_type": itemType]
        )
    }

    static func removeTag(tagId: String, itemId: String, itemType: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "tags/remove", method: .post,
            body: ["tag_id": tagId, "item_id": itemId, "item_type": itemType]
        )
    }

    static func getItemTags(itemId: String, itemType: String) async throws -> [DriveTagDTO] {
        let response: APIResponse<[DriveTagDTO]> = try await api.request(
            endpoint: "tags/item-tags", queryParams: ["item_id": itemId, "item_type": itemType]
        )
        return response.data ?? []
    }

    // MARK: - Comments

    static func getComments(fileId: String) async throws -> [DriveCommentDTO] {
        let response: APIResponse<[DriveCommentDTO]> = try await api.request(
            endpoint: "comments", queryParams: ["file_id": fileId]
        )
        return response.data ?? []
    }

    static func addComment(fileId: String, text: String) async throws -> DriveCommentDTO {
        let response: APIResponse<DriveCommentDTO> = try await api.request(
            endpoint: "comments", method: .post, body: ["file_id": fileId, "text": text]
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    static func deleteComment(commentId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "comments/\(commentId)", method: .delete
        )
    }

    // MARK: - Versions

    static func getFileVersions(fileId: String) async throws -> [DriveVersionDTO] {
        let response: APIResponse<[DriveVersionDTO]> = try await api.request(
            endpoint: "versions/\(fileId)"
        )
        return response.data ?? []
    }

    static func restoreVersion(fileId: String, versionId: String) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "versions/\(fileId)/\(versionId)/restore", method: .post
        )
    }

    // MARK: - Bulk

    static func bulkDelete(items: [[String: String]]) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "bulk/delete", method: .post, body: ["items": items]
        )
    }

    static func bulkMove(items: [[String: String]], targetFolderId: String?) async throws {
        let body: [String: Any] = [
            "items": items,
            "target_folder_id": targetFolderId ?? NSNull()
        ]
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "bulk/move", method: .post, body: body
        )
    }

    // MARK: - Activity

    static func getActivity(options: [String: String] = [:]) async throws -> [DriveActivityDTO] {
        let response: APIResponse<[DriveActivityDTO]> = try await api.request(
            endpoint: "activity", queryParams: options
        )
        return response.data ?? []
    }

    // MARK: - Storage

    static func getStorageUsage() async throws -> StorageUsageDTO {
        let response: APIResponse<StorageUsageDTO> = try await api.request(endpoint: "storage")
        guard let data = response.data else { throw APIError.invalidResponse }
        return data
    }

    // MARK: - Folder Access

    static func getFolderAccess(folderId: String) async throws -> [FolderAccessDTO] {
        let response: APIResponse<[FolderAccessDTO]> = try await api.request(
            endpoint: "folders/\(folderId)/access"
        )
        return response.data ?? []
    }

    static func updateFolderAccess(folderId: String, entries: [[String: Any]]) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "folders/\(folderId)/access", method: .put,
            body: ["entries": entries, "replace_existing": true]
        )
    }

    // MARK: - File Access

    static func getFileAccess(fileId: String) async throws -> [FileAccessDTO] {
        let response: APIResponse<[FileAccessDTO]> = try await api.request(
            endpoint: "file-access/\(fileId)/access"
        )
        return response.data ?? []
    }

    static func updateFileAccess(fileId: String, entries: [[String: Any]]) async throws {
        let _: APIResponse<EmptyResponse> = try await api.request(
            endpoint: "file-access/\(fileId)/access", method: .put,
            body: ["entries": entries]
        )
    }

    // MARK: - Editor

    /// Get a page token for opening the OnlyOffice editor in a WebView
    static func getEditorPageToken(fileId: String) async throws -> String {
        let response: APIResponse<EditorPageTokenDTO> = try await api.request(
            endpoint: "editor/\(fileId)/page-token"
        )
        guard let data = response.data else { throw APIError.invalidResponse }
        return data.token
    }
}

// MARK: - Helper Types

struct EmptyResponse: Codable {}

struct EditorPageTokenDTO: Codable {
    let token: String
}

struct TrashItemDTO: Codable, Identifiable {
    let id: String
    let type: String
    let name: String?
    let deletedOn: Int64?
    let file: DriveFileDTO?
    let folder: DriveFolderDTO?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case type, name
        case deletedOn = "deleted_on"
        case file, folder
    }
}
