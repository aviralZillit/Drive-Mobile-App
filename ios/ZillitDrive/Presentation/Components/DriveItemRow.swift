import SwiftUI
import Kingfisher

struct DriveItemRow: View {
    let item: DriveItem
    let isFavorite: Bool
    var isDropTarget: Bool = false
    var currentUserId: String? = nil
    var badgeCount: Int = 0
    var hasUnreadBadge: Bool = false
    var onTap: () -> Void = {}
    var onFavorite: () -> Void = {}
    var onDelete: () -> Void = {}
    var onShare: () -> Void = {}
    var onMove: () -> Void = {}
    var onRename: () -> Void = {}

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Icon
                itemIcon
                    .frame(width: 40, height: 40)
                    .background(iconBackground)
                    .cornerRadius(8)

                // Info
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .font(.body)
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    HStack(spacing: 8) {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundColor(.secondary)

                        if isFavorite {
                            Image(systemName: "star.fill")
                                .font(.caption2)
                                .foregroundColor(.orange)
                        }

                        // Shared indicator
                        if let uid = currentUserId, !item.createdBy.isEmpty, item.createdBy != uid {
                            Image(systemName: "person.2.fill")
                                .font(.caption2)
                                .foregroundColor(.blue)
                        }
                    }
                }

                Spacer()

                // Badge count (folders) or unread dot (files)
                if badgeCount > 0 {
                    Text("\(badgeCount)")
                        .font(.caption2).bold()
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color.orange))
                } else if hasUnreadBadge {
                    Circle()
                        .fill(Color.orange)
                        .frame(width: 8, height: 8)
                }

                // Context menu
                Menu {
                    Button { onFavorite() } label: {
                        Label(
                            isFavorite ? "Remove Favorite" : "Add Favorite",
                            systemImage: isFavorite ? "star.slash" : "star"
                        )
                    }

                    Button { onShare() } label: {
                        Label("Share", systemImage: "person.badge.plus")
                    }

                    Button { onMove() } label: {
                        Label("Move", systemImage: "folder.badge.arrow.forward")
                    }

                    if item.userPermissions?.canEdit ?? (currentUserId != nil && item.createdBy == currentUserId) {
                        Button { onRename() } label: {
                            Label("Rename", systemImage: "pencil")
                        }
                    }

                    Divider()

                    Button(role: .destructive) { onDelete() } label: {
                        Label("Delete", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundColor(.secondary)
                        .frame(width: 32, height: 32)
                }
            }
            .padding(.vertical, 4)
            .padding(.horizontal, isDropTarget ? 4 : 0)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isDropTarget ? Color.orange : Color.clear, lineWidth: 2)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(isDropTarget ? Color.orange.opacity(0.1) : Color.clear)
                    )
            )
            .animation(.easeInOut(duration: 0.2), value: isDropTarget)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Subviews

    @ViewBuilder
    private var itemIcon: some View {
        switch item {
        case .folder:
            Image(systemName: "folder.fill")
                .font(.title3)
                .foregroundColor(.orange)
        case .file(let file):
            if let previewUrl = file.previewUrl, !previewUrl.isEmpty,
               (FileUtils.isImage(file.fileExtension) || FileUtils.isVideo(file.fileExtension)) {
                KFImage(URL(string: previewUrl))
                    .placeholder {
                        Image(systemName: iconName(for: file.fileExtension))
                            .font(.title3)
                            .foregroundColor(FileUtils.extensionColor(for: file.fileExtension))
                    }
                    .resizable()
                    .scaledToFit()
            } else {
                Image(systemName: iconName(for: file.fileExtension))
                    .font(.title3)
                    .foregroundColor(FileUtils.extensionColor(for: file.fileExtension))
            }
        }
    }

    private var iconBackground: Color {
        switch item {
        case .folder: return Color.orange.opacity(0.1)
        case .file(let file): return FileUtils.extensionColor(for: file.fileExtension).opacity(0.1)
        }
    }

    private var subtitle: String {
        switch item {
        case .folder(let folder):
            let parts = [
                folder.fileCount > 0 ? "\(folder.fileCount) file\(folder.fileCount == 1 ? "" : "s")" : nil,
                folder.folderCount > 0 ? "\(folder.folderCount) folder\(folder.folderCount == 1 ? "" : "s")" : nil,
            ].compactMap { $0 }
            return parts.isEmpty ? "Folder" : parts.joined(separator: ", ")
        case .file(let file):
            return "\(FileUtils.formatFileSize(file.fileSizeBytes)) · \(file.fileExtension.uppercased())"
        }
    }

    private func iconName(for ext: String) -> String {
        if FileUtils.isImage(ext) { return "photo" }
        if FileUtils.isVideo(ext) { return "film" }
        if FileUtils.isAudio(ext) { return "music.note" }
        if FileUtils.isPDF(ext) { return "doc.richtext" }
        if FileUtils.isOffice(ext) { return "doc.text" }
        return "doc"
    }
}

// MARK: - Grid Cell

struct DriveItemGridCell: View {
    let item: DriveItem
    let isFavorite: Bool
    var isDropTarget: Bool = false
    var currentUserId: String? = nil
    var badgeCount: Int = 0
    var hasUnreadBadge: Bool = false
    var onTap: () -> Void = {}

    private var hasThumbnail: Bool {
        if case .file(let file) = item,
           let url = file.previewUrl, !url.isEmpty,
           (FileUtils.isImage(file.fileExtension) || FileUtils.isVideo(file.fileExtension)) {
            return true
        }
        return false
    }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                // Thumbnail / Icon area
                ZStack(alignment: .topTrailing) {
                    thumbnailView
                        .frame(maxWidth: .infinity)
                        .frame(height: 120)
                        .clipped()

                    // Badges overlay
                    HStack(spacing: 4) {
                        if isFavorite {
                            Image(systemName: "star.fill")
                                .font(.system(size: 10))
                                .foregroundColor(.white)
                                .padding(4)
                                .background(Circle().fill(Color.orange))
                        }
                        if badgeCount > 0 {
                            Text("\(badgeCount)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Color.orange))
                        } else if hasUnreadBadge {
                            Circle().fill(Color.orange).frame(width: 8, height: 8)
                        }
                    }
                    .padding(6)
                }

                // File info bar
                HStack(spacing: 6) {
                    // Small type icon
                    Image(systemName: smallIconName)
                        .font(.system(size: 12))
                        .foregroundColor(iconColor)

                    Text(item.name)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.primary)
                        .lineLimit(1)
                        .truncationMode(.middle)

                    Spacer(minLength: 0)

                    // Shared indicator
                    if let uid = currentUserId, !item.createdBy.isEmpty, item.createdBy != uid {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 9))
                            .foregroundColor(.blue)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
                .background(Color(.systemBackground))
            }
            .background(Color(.secondarySystemBackground))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isDropTarget ? Color.orange : Color(.separator).opacity(0.3), lineWidth: isDropTarget ? 2 : 0.5)
            )
            .shadow(color: Color.black.opacity(0.06), radius: 3, x: 0, y: 1)
            .animation(.easeInOut(duration: 0.2), value: isDropTarget)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var thumbnailView: some View {
        switch item {
        case .folder:
            ZStack {
                Color.orange.opacity(0.08)
                Image(systemName: "folder.fill")
                    .font(.system(size: 36))
                    .foregroundColor(.orange)
            }
        case .file(let file):
            if let previewUrl = file.previewUrl, !previewUrl.isEmpty,
               (FileUtils.isImage(file.fileExtension) || FileUtils.isVideo(file.fileExtension)) {
                ZStack {
                    Color(.secondarySystemBackground)
                    KFImage(URL(string: previewUrl))
                        .placeholder { ProgressView() }
                        .resizable()
                        .scaledToFit()
                        .padding(4)

                    if FileUtils.isVideo(file.fileExtension) {
                        Image(systemName: "play.circle.fill")
                            .font(.system(size: 28))
                            .foregroundStyle(.white, .black.opacity(0.4))
                    }
                }
            } else {
                ZStack {
                    FileUtils.extensionColor(for: file.fileExtension).opacity(0.08)
                    VStack(spacing: 4) {
                        Image(systemName: gridIconName(for: file.fileExtension))
                            .font(.system(size: 28))
                            .foregroundColor(FileUtils.extensionColor(for: file.fileExtension))
                        Text(file.fileExtension.uppercased())
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
    }

    private var smallIconName: String {
        switch item {
        case .folder: return "folder.fill"
        case .file(let file): return gridIconName(for: file.fileExtension)
        }
    }

    private var iconColor: Color {
        switch item {
        case .folder: return .orange
        case .file(let file): return FileUtils.extensionColor(for: file.fileExtension)
        }
    }

    private func gridIconName(for ext: String) -> String {
        if FileUtils.isImage(ext) { return "photo" }
        if FileUtils.isVideo(ext) { return "film" }
        if FileUtils.isAudio(ext) { return "music.note" }
        if FileUtils.isPDF(ext) { return "doc.richtext" }
        if FileUtils.isOffice(ext) { return "doc.text" }
        return "doc"
    }
}
