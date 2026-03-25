import Foundation

struct DrivePermissions: Equatable {
    let canView: Bool
    let canEdit: Bool
    let canDownload: Bool
    let canDelete: Bool
}

enum DriveSection: String, CaseIterable {
    case myDrive
    case sharedWithMe

    var displayName: String {
        switch self {
        case .myDrive: return "My Drive"
        case .sharedWithMe: return "Shared with me"
        }
    }

    var quickFilter: String {
        switch self {
        case .myDrive: return "mine"
        case .sharedWithMe: return "shared"
        }
    }
}

struct DriveFile: Identifiable, Equatable {
    let id: String
    let fileName: String
    let fileExtension: String
    let fileSizeBytes: Int64
    let mimeType: String
    let folderId: String?
    let filePath: String?
    let description: String?
    let createdBy: String
    let createdOn: Int64
    let updatedOn: Int64
    let thumbnailUrl: String?
    var isFavorite: Bool = false
    var userPermissions: DrivePermissions? = nil
}

struct DriveFolder: Identifiable, Equatable {
    let id: String
    let folderName: String
    let parentFolderId: String?
    let description: String?
    let createdBy: String
    let createdOn: Int64
    let updatedOn: Int64
    let fileCount: Int
    let folderCount: Int
    var isFavorite: Bool = false
    var userPermissions: DrivePermissions? = nil
}

struct DriveContents {
    let folders: [DriveFolder]
    let files: [DriveFile]
    let totalFolders: Int
    let totalFiles: Int
}

enum DriveItem: Identifiable, Equatable {
    case file(DriveFile)
    case folder(DriveFolder)

    var id: String {
        switch self {
        case .file(let f): return f.id
        case .folder(let f): return f.id
        }
    }

    var name: String {
        switch self {
        case .file(let f): return f.fileName
        case .folder(let f): return f.folderName
        }
    }

    var isFavorite: Bool {
        switch self {
        case .file(let f): return f.isFavorite
        case .folder(let f): return f.isFavorite
        }
    }

    var createdOn: Int64 {
        switch self {
        case .file(let f): return f.createdOn
        case .folder(let f): return f.createdOn
        }
    }

    var createdBy: String {
        switch self {
        case .file(let f): return f.createdBy
        case .folder(let f): return f.createdBy
        }
    }

    var userPermissions: DrivePermissions? {
        switch self {
        case .file(let f): return f.userPermissions
        case .folder(let f): return f.userPermissions
        }
    }
}

struct DriveTag: Identifiable, Equatable {
    let id: String
    let name: String
    let color: String
}

struct DriveComment: Identifiable, Equatable {
    let id: String
    let fileId: String
    let userId: String
    let text: String
    let createdOn: Int64
}

struct DriveVersion: Identifiable, Equatable {
    let id: String
    let fileId: String
    let versionNumber: Int
    let fileSizeBytes: Int64
    let createdBy: String
    let createdOn: Int64
}

struct DriveActivity: Identifiable, Equatable {
    let id: String
    let action: String
    let itemId: String
    let itemType: String
    let userId: String
    let details: String?
    let createdOn: Int64
}

struct StorageUsage {
    let usedBytes: Int64
    let totalBytes: Int64
    let fileCount: Int
}

struct ShareLink {
    let url: String
    let expiresAt: String?
}

struct BreadcrumbItem: Identifiable, Equatable {
    let id: String?  // nil for root
    let name: String
}
