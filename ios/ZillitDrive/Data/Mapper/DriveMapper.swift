import Foundation

enum DriveMapper {

    // MARK: - Permissions

    private static func mapPermissions(_ dto: UserPermissionsDTO?, createdBy: String?) -> DrivePermissions {
        if let p = dto {
            return DrivePermissions(
                canView: p.canView ?? true,
                canEdit: p.canEdit ?? false,
                canDownload: p.canDownload ?? false,
                canDelete: p.canDelete ?? false
            )
        }
        // Fallback: creator gets full perms, others get view-only (matches web)
        let currentUserId = SessionManager.shared.currentSession?.userId
        let isCreator = currentUserId != nil && createdBy != nil && createdBy == currentUserId
        return DrivePermissions(
            canView: true,
            canEdit: isCreator,
            canDownload: isCreator,
            canDelete: isCreator
        )
    }

    // MARK: - File

    static func toDomain(_ dto: DriveFileDTO, isFavorite: Bool = false) -> DriveFile {
        DriveFile(
            id: dto.id,
            fileName: dto.fileName,
            fileExtension: dto.fileExtension ?? "",
            fileSizeBytes: dto.fileSizeBytes ?? 0,
            mimeType: dto.mimeType ?? "application/octet-stream",
            folderId: dto.folderId,
            filePath: dto.filePath,
            description: dto.description,
            createdBy: dto.createdBy ?? "",
            createdOn: dto.createdOn ?? 0,
            updatedOn: dto.updatedOn ?? 0,
            thumbnailUrl: dto.attachments?.first?.thumbnail,
            isFavorite: isFavorite,
            userPermissions: mapPermissions(dto.userPermissions, createdBy: dto.createdBy)
        )
    }

    // MARK: - Folder

    static func toDomain(_ dto: DriveFolderDTO, isFavorite: Bool = false) -> DriveFolder {
        DriveFolder(
            id: dto.id,
            folderName: dto.folderName,
            parentFolderId: dto.parentFolderId,
            description: dto.description,
            createdBy: dto.createdBy ?? "",
            createdOn: dto.createdOn ?? 0,
            updatedOn: dto.updatedOn ?? 0,
            fileCount: dto.fileCount ?? 0,
            folderCount: dto.folderCount ?? 0,
            isFavorite: isFavorite,
            userPermissions: mapPermissions(dto.userPermissions, createdBy: dto.createdBy)
        )
    }

    // MARK: - Contents (from unified items array)

    static func toDomain(_ dto: DriveContentsDTO, favoriteFileIds: Set<String> = [], favoriteFolderIds: Set<String> = []) -> DriveContents {
        var folders: [DriveFolder] = []
        var files: [DriveFile] = []

        for item in dto.items ?? [] {
            let perms = mapPermissions(item.userPermissions, createdBy: item.createdBy)
            if item.isFolder == true || item.type == "folder" {
                let folder = DriveFolder(
                    id: item.id,
                    folderName: item.folderName ?? item.name ?? "Untitled",
                    parentFolderId: item.parentFolderId,
                    description: item.description,
                    createdBy: item.createdBy ?? "",
                    createdOn: item.createdOn ?? 0,
                    updatedOn: item.updatedOn ?? 0,
                    fileCount: item.fileCount ?? 0,
                    folderCount: item.folderCount ?? 0,
                    isFavorite: favoriteFolderIds.contains(item.id),
                    userPermissions: perms
                )
                folders.append(folder)
            } else {
                let file = DriveFile(
                    id: item.id,
                    fileName: item.fileName ?? item.name ?? "Untitled",
                    fileExtension: item.fileExtension ?? "",
                    fileSizeBytes: item.fileSizeBytes ?? item.size ?? 0,
                    mimeType: item.mimeType ?? "application/octet-stream",
                    folderId: item.folderId,
                    filePath: item.filePath,
                    description: item.description,
                    createdBy: item.createdBy ?? "",
                    createdOn: item.createdOn ?? 0,
                    updatedOn: item.updatedOn ?? 0,
                    thumbnailUrl: item.attachments?.first?.thumbnail,
                    isFavorite: favoriteFileIds.contains(item.id),
                    userPermissions: perms
                )
                files.append(file)
            }
        }

        return DriveContents(
            folders: folders,
            files: files,
            totalFolders: dto.counts?.folders ?? folders.count,
            totalFiles: dto.counts?.files ?? files.count
        )
    }

    // MARK: - Tag

    static func toDomain(_ dto: DriveTagDTO) -> DriveTag {
        DriveTag(id: dto.id, name: dto.name, color: dto.color ?? "#999999")
    }

    // MARK: - Comment

    static func toDomain(_ dto: DriveCommentDTO) -> DriveComment {
        DriveComment(
            id: dto.id,
            fileId: dto.fileId ?? "",
            userId: dto.userId ?? "",
            text: dto.text,
            createdOn: dto.createdOn ?? 0
        )
    }

    // MARK: - Version

    static func toDomain(_ dto: DriveVersionDTO) -> DriveVersion {
        DriveVersion(
            id: dto.id,
            fileId: dto.fileId ?? "",
            versionNumber: dto.versionNumber ?? 0,
            fileSizeBytes: dto.fileSizeBytes ?? 0,
            createdBy: dto.createdBy ?? "",
            createdOn: dto.createdOn ?? 0
        )
    }

    // MARK: - Activity

    static func toDomain(_ dto: DriveActivityDTO) -> DriveActivity {
        DriveActivity(
            id: dto.id,
            action: dto.action,
            itemId: dto.itemId ?? "",
            itemType: dto.itemType ?? "",
            userId: dto.userId ?? "",
            details: dto.details,
            createdOn: dto.createdOn ?? 0
        )
    }

    // MARK: - Storage

    static func toDomain(_ dto: StorageUsageDTO) -> StorageUsage {
        StorageUsage(
            usedBytes: dto.usedBytes ?? 0,
            totalBytes: dto.totalBytes ?? 0,
            fileCount: dto.fileCount ?? 0
        )
    }

    // MARK: - Share Link

    static func toDomain(_ dto: ShareLinkDTO) -> ShareLink {
        ShareLink(url: dto.url, expiresAt: dto.expiresAt)
    }

    // MARK: - Project User

    static func toDomain(_ dto: ProjectUserDTO) -> ProjectUser {
        let firstName = dto.firstName ?? ""
        let lastName = dto.lastName ?? ""
        let fullName = dto.fullName ?? "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
        return ProjectUser(
            id: dto.id ?? "",
            fullName: fullName.isEmpty ? (dto.email ?? "Unknown") : fullName,
            firstName: firstName,
            lastName: lastName,
            email: dto.email ?? "",
            profileImage: dto.profileImage,
            designationName: dto.designationName
        )
    }

    // MARK: - Trash

    static func toDomain(_ dto: TrashItemDTO) -> DriveItem {
        if dto.itemType == "folder" {
            return .folder(DriveFolder(
                id: dto.id,
                folderName: dto.folderName ?? dto.name ?? "Untitled",
                parentFolderId: dto.parentFolderId,
                description: nil,
                createdBy: dto.createdBy ?? "",
                createdOn: dto.createdOn ?? 0,
                updatedOn: 0,
                fileCount: 0,
                folderCount: 0
            ))
        }
        return .file(DriveFile(
            id: dto.id,
            fileName: dto.fileName ?? dto.name ?? "Untitled",
            fileExtension: dto.fileExtension ?? "",
            fileSizeBytes: dto.fileSizeBytes ?? 0,
            mimeType: dto.mimeType ?? "",
            folderId: dto.folderId,
            filePath: nil,
            description: nil,
            createdBy: dto.createdBy ?? "",
            createdOn: dto.createdOn ?? 0,
            updatedOn: 0,
            thumbnailUrl: nil
        ))
    }
}
