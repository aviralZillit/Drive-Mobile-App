import Foundation

// MARK: - Generic API Response

struct APIResponse<T: Decodable>: Decodable {
    let status: Int
    let message: String?
    let messageElements: [String]?
    let data: T?

    var isSuccess: Bool { status == 1 }
}

// MARK: - Permissions DTO

struct UserPermissionsDTO: Codable {
    let canView: Bool?
    let canEdit: Bool?
    let canDownload: Bool?
    let canDelete: Bool?

    enum CodingKeys: String, CodingKey {
        case canView = "can_view"
        case canEdit = "can_edit"
        case canDownload = "can_download"
        case canDelete = "can_delete"
    }
}

// MARK: - File DTOs

struct DriveFileDTO: Codable, Identifiable {
    let id: String
    let fileName: String
    let fileExtension: String?
    let fileSizeBytes: Int64?
    let mimeType: String?
    let folderId: String?
    let filePath: String?
    let description: String?
    let createdBy: String?
    let createdOn: Int64?
    let updatedOn: Int64?
    let deletedOn: Int64?
    let attachments: [AttachmentDTO]?
    var userPermissions: UserPermissionsDTO? = nil

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case fileName = "file_name"
        case fileExtension = "file_extension"
        case fileSizeBytes = "file_size_bytes"
        case mimeType = "mime_type"
        case folderId = "folder_id"
        case filePath = "file_path"
        case description
        case createdBy = "created_by"
        case createdOn = "created_on"
        case updatedOn = "updated_on"
        case deletedOn = "deleted_on"
        case attachments
        case userPermissions = "_userPermissions"
    }
}

struct AttachmentDTO: Codable {
    let media: String?
    let thumbnail: String?
}

// MARK: - Folder DTOs

struct DriveFolderDTO: Codable, Identifiable {
    let id: String
    let folderName: String
    let parentFolderId: String?
    let description: String?
    let createdBy: String?
    let createdOn: Int64?
    let updatedOn: Int64?
    let deletedOn: Int64?
    let fileCount: Int?
    let folderCount: Int?
    var userPermissions: UserPermissionsDTO? = nil

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case folderName = "folder_name"
        case parentFolderId = "parent_folder_id"
        case description
        case createdBy = "created_by"
        case createdOn = "created_on"
        case updatedOn = "updated_on"
        case deletedOn = "deleted_on"
        case fileCount = "file_count"
        case folderCount = "folder_count"
        case userPermissions = "_userPermissions"
    }
}

// MARK: - Contents DTO (from /v2/drive/folders/contents)

struct DriveContentsDTO: Codable {
    let items: [DriveContentItemDTO]?
    let pagination: PaginationDTO?
    let counts: CountsDTO?
}

struct DriveContentItemDTO: Codable, Identifiable {
    let id: String
    let type: String?           // "folder" or "file"
    let isFolder: Bool?
    let name: String?
    let dateModified: Int64?
    let size: Int64?

    // Folder fields
    let folderName: String?
    let folderPath: String?
    let parentFolderId: String?

    // File fields
    let fileName: String?
    let fileExtension: String?
    let fileSizeBytes: Int64?
    let mimeType: String?
    let folderId: String?
    let filePath: String?
    let attachments: [AttachmentDTO]?

    // Common fields
    let description: String?
    let createdBy: String?
    let createdOn: Int64?
    let updatedOn: Int64?
    let deletedOn: Int64?
    let fileCount: Int?
    let folderCount: Int?
    var userPermissions: UserPermissionsDTO? = nil

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case type
        case isFolder = "is_folder"
        case name
        case dateModified = "date_modified"
        case size
        case folderName = "folder_name"
        case folderPath = "folder_path"
        case parentFolderId = "parent_folder_id"
        case fileName = "file_name"
        case fileExtension = "file_extension"
        case fileSizeBytes = "file_size_bytes"
        case mimeType = "mime_type"
        case folderId = "folder_id"
        case filePath = "file_path"
        case attachments
        case description
        case createdBy = "created_by"
        case createdOn = "created_on"
        case updatedOn = "updated_on"
        case deletedOn = "deleted_on"
        case fileCount = "file_count"
        case folderCount = "folder_count"
        case userPermissions = "_userPermissions"
    }
}

struct PaginationDTO: Codable {
    let total: Int?
    let limit: Int?
    let offset: Int?
    let hasMore: Bool?

    enum CodingKeys: String, CodingKey {
        case total, limit, offset
        case hasMore = "has_more"
    }
}

struct CountsDTO: Codable {
    let folders: Int?
    let files: Int?
    let total: Int?
}

// MARK: - Upload DTOs

struct UploadSessionDTO: Codable {
    let uploadId: String
    let s3UploadId: String?
    let fileName: String?
    let fileSizeBytes: Int64?
    let chunkSize: Int64?
    let totalParts: Int?
    let presignedUrls: [PresignedUrlDTO]?

    enum CodingKeys: String, CodingKey {
        case uploadId = "upload_id"
        case s3UploadId = "s3_upload_id"
        case fileName = "file_name"
        case fileSizeBytes = "file_size_bytes"
        case chunkSize = "chunk_size"
        case totalParts = "total_parts"
        case presignedUrls = "presigned_urls"
    }
}

struct PresignedUrlDTO: Codable {
    let partNumber: Int
    let url: String

    enum CodingKeys: String, CodingKey {
        case partNumber = "part_number"
        case url
    }
}

// MARK: - Access DTOs

struct FolderAccessDTO: Codable {
    let userId: String
    let folderId: String?
    let role: String
    let inherited: Bool?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case folderId = "folder_id"
        case role, inherited
    }
}

struct FileAccessDTO: Codable {
    let userId: String
    let fileId: String?
    let canView: Bool?
    let canEdit: Bool?
    let canDownload: Bool?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case fileId = "file_id"
        case canView = "can_view"
        case canEdit = "can_edit"
        case canDownload = "can_download"
    }
}

// MARK: - Tag DTOs

struct DriveTagDTO: Codable, Identifiable {
    let id: String
    let name: String
    let color: String?
    let projectId: String?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case name, color
        case projectId = "project_id"
    }
}

// MARK: - Comment DTOs

struct DriveCommentDTO: Codable, Identifiable {
    let id: String
    let fileId: String?
    let userId: String?
    let text: String
    let createdOn: Int64?
    let updatedOn: Int64?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case fileId = "file_id"
        case userId = "user_id"
        case text
        case createdOn = "created_on"
        case updatedOn = "updated_on"
    }
}

// MARK: - Version DTOs

struct DriveVersionDTO: Codable, Identifiable {
    let id: String
    let fileId: String?
    let versionNumber: Int?
    let fileSizeBytes: Int64?
    let filePath: String?
    let createdBy: String?
    let createdOn: Int64?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case fileId = "file_id"
        case versionNumber = "version_number"
        case fileSizeBytes = "file_size_bytes"
        case filePath = "file_path"
        case createdBy = "created_by"
        case createdOn = "created_on"
    }
}

// MARK: - Activity DTOs

struct DriveActivityDTO: Codable, Identifiable {
    let id: String
    let action: String
    let itemId: String?
    let itemType: String?
    let userId: String?
    let details: String?
    let createdOn: Int64?

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case action
        case itemId = "item_id"
        case itemType = "item_type"
        case userId = "user_id"
        case details
        case createdOn = "created_on"
    }
}

// MARK: - Storage DTO

struct StorageUsageDTO: Codable {
    let usedBytes: Int64?
    let totalBytes: Int64?
    let fileCount: Int?

    enum CodingKeys: String, CodingKey {
        case usedBytes = "used_bytes"
        case totalBytes = "total_bytes"
        case fileCount = "file_count"
    }
}

// MARK: - Stream URL DTO

struct StreamUrlDTO: Codable {
    let url: String
}

// MARK: - Share Link DTO

struct ShareLinkDTO: Codable {
    let url: String
    let expiresAt: String?

    enum CodingKeys: String, CodingKey {
        case url
        case expiresAt = "expires_at"
    }
}

// MARK: - Favorite IDs

struct FavoriteIdsDTO: Codable {
    let fileIds: [String]?
    let folderIds: [String]?

    enum CodingKeys: String, CodingKey {
        case fileIds = "file_ids"
        case folderIds = "folder_ids"
    }
}
