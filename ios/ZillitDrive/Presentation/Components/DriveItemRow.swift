import SwiftUI

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

                    if item.userPermissions?.canEdit ?? (currentUserId != nil && item.createdBy == currentUserId) {
                        Button { onRename() } label: {
                            Label("Rename", systemImage: "pencil")
                        }
                    }

                    Divider()

                    if item.userPermissions?.canDelete ?? (currentUserId != nil && item.createdBy == currentUserId) {
                        Button(role: .destructive) { onDelete() } label: {
                            Label("Delete", systemImage: "trash")
                        }
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
            Image(systemName: iconName(for: file.fileExtension))
                .font(.title3)
                .foregroundColor(FileUtils.extensionColor(for: file.fileExtension))
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
            return "\(folder.fileCount) files, \(folder.folderCount) folders"
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

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(backgroundColor)
                        .frame(height: 100)
                        .overlay {
                            icon
                        }

                    // Top-right badges
                    VStack(spacing: 4) {
                        if isFavorite {
                            Image(systemName: "star.fill")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                        if badgeCount > 0 {
                            Text("\(badgeCount)")
                                .font(.caption2).bold()
                                .foregroundColor(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(Capsule().fill(Color.orange))
                        } else if hasUnreadBadge {
                            Circle()
                                .fill(Color.orange)
                                .frame(width: 8, height: 8)
                        }
                    }
                    .padding(6)
                }

                HStack(spacing: 4) {
                    Text(item.name)
                        .font(.caption)
                        .foregroundColor(.primary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)

                    // Shared indicator
                    if let uid = currentUserId, !item.createdBy.isEmpty, item.createdBy != uid {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 8))
                            .foregroundColor(.blue)
                    }
                }
            }
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isDropTarget ? Color.orange : Color.clear, lineWidth: 2)
            )
            .animation(.easeInOut(duration: 0.2), value: isDropTarget)
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var icon: some View {
        switch item {
        case .folder:
            Image(systemName: "folder.fill")
                .font(.largeTitle)
                .foregroundColor(.orange)
        case .file(let file):
            VStack(spacing: 4) {
                Image(systemName: gridIconName(for: file.fileExtension))
                    .font(.title)
                    .foregroundColor(FileUtils.extensionColor(for: file.fileExtension))
                Text(file.fileExtension.uppercased())
                    .font(.caption2.bold())
                    .foregroundColor(.secondary)
            }
        }
    }

    private var backgroundColor: Color {
        switch item {
        case .folder: return Color.orange.opacity(0.08)
        case .file(let file): return FileUtils.extensionColor(for: file.fileExtension).opacity(0.08)
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
