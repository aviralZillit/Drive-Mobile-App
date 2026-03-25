import SwiftUI
import QuickLook
import Kingfisher
import AVKit

struct FileDetailView: View {
    let file: DriveFile
    @State private var comments: [DriveComment] = []
    @State private var versions: [DriveVersion] = []
    @State private var tags: [DriveTag] = []
    @State private var newComment = ""
    @State private var isLoading = false
    @State private var isDownloading = false
    @State private var downloadError: String?
    @State private var showShareSheet = false
    @State private var shareURL: URL?
    @State private var previewURL: URL?
    @State private var selectedTab = 0
    @State private var showEditor = false
    @State private var editorMode = "edit"
    @State private var mediaPreviewUrl: String?
    @State private var videoPlayer: AVPlayer?
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        VStack(spacing: 0) {
            // File info header with inline media preview
            VStack(spacing: 8) {
                // Image preview
                if FileUtils.isImage(file.fileExtension), let urlStr = mediaPreviewUrl ?? file.previewUrl,
                   let url = URL(string: urlStr) {
                    KFImage(url)
                        .placeholder {
                            ZStack {
                                Color(.secondarySystemBackground)
                                ProgressView()
                            }
                        }
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 250)
                        .cornerRadius(12)
                        .padding(.horizontal)
                // Video player
                } else if FileUtils.isVideo(file.fileExtension), let player = videoPlayer {
                    VideoPlayer(player: player)
                        .frame(height: 220)
                        .cornerRadius(12)
                        .padding(.horizontal)
                // Fallback icon
                } else {
                    Image(systemName: iconName)
                        .font(.system(size: 48))
                        .foregroundColor(FileUtils.extensionColor(for: file.fileExtension))
                }

                Text(file.fileName)
                    .font(.headline)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                HStack(spacing: 16) {
                    Label(FileUtils.formatFileSize(file.fileSizeBytes), systemImage: "doc")
                    Label(file.fileExtension.uppercased(), systemImage: "tag")
                    Label(formatDate(file.createdOn), systemImage: "calendar")
                }
                .font(.caption)
                .foregroundColor(.secondary)
            }
            .padding()
            .task { await loadMediaPreview() }

            // Action buttons
            VStack(spacing: 8) {
                HStack(spacing: 24) {
                    actionButton(icon: "arrow.down.circle", title: isDownloading ? "Loading..." : "Download") {
                        Task { await downloadFile() }
                    }
                    .disabled(isDownloading)

                    actionButton(icon: "square.and.arrow.up", title: "Share") {
                        Task { await shareFile() }
                    }
                    actionButton(icon: "eye", title: "Preview") {
                        Task { await previewFile() }
                    }
                    .disabled(isDownloading)

                    if FileUtils.isEditable(file.fileExtension) {
                        actionButton(icon: "pencil.circle", title: "Edit") {
                            editorMode = "edit"
                            showEditor = true
                        }
                    }
                    if FileUtils.isOffice(file.fileExtension) || FileUtils.isPDF(file.fileExtension) {
                        actionButton(icon: "doc.viewfinder", title: "Preview") {
                            editorMode = "view"
                            showEditor = true
                        }
                    }
                }

                if isDownloading {
                    ProgressView()
                        .progressViewStyle(.linear)
                        .padding(.horizontal)
                }

                if let error = downloadError {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 8)

            // Tabs
            Picker("", selection: $selectedTab) {
                Text("Comments").tag(0)
                Text("Versions").tag(1)
                Text("Tags").tag(2)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            // Tab content
            switch selectedTab {
            case 0:
                commentsSection
            case 1:
                versionsSection
            case 2:
                tagsSection
            default:
                Spacer()
            }
        }
        .navigationTitle("File Details")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadDetails() }
        .sheet(isPresented: $showShareSheet) {
            if let url = shareURL {
                ShareSheet(items: [url])
            }
        }
        .quickLookPreview($previewURL)
        .fullScreenCover(isPresented: $showEditor) {
            OnlyOfficeEditorView(fileId: file.id, fileName: file.fileName, mode: editorMode)
        }
    }

    // MARK: - Comments

    private var commentsSection: some View {
        VStack {
            if comments.isEmpty {
                Spacer()
                Text("No comments")
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                List(comments) { comment in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(comment.text)
                            .font(.body)
                        Text(formatDate(comment.createdOn))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .swipeActions {
                        Button(role: .destructive) {
                            Task { await deleteComment(comment.id) }
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
                .listStyle(.plain)
            }

            // Add comment
            HStack {
                TextField("Add a comment...", text: $newComment)
                    .textFieldStyle(.roundedBorder)

                Button {
                    Task { await addComment() }
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(.orange)
                }
                .disabled(newComment.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding()
        }
    }

    // MARK: - Versions

    private var versionsSection: some View {
        Group {
            if versions.isEmpty {
                VStack {
                    Spacer()
                    Text("No versions")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            } else {
                List(versions) { version in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Version \(version.versionNumber)")
                                .font(.body.bold())
                            Text(FileUtils.formatFileSize(version.fileSizeBytes))
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(formatDate(version.createdOn))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Button("Restore") {
                            Task { await restoreVersion(version) }
                        }
                        .buttonStyle(.bordered)
                        .tint(.orange)
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    // MARK: - Tags

    private var tagsSection: some View {
        Group {
            if tags.isEmpty {
                VStack {
                    Spacer()
                    Text("No tags")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            } else {
                ScrollView {
                    FlowLayout(spacing: 8) {
                        ForEach(tags) { tag in
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(Color(hex: tag.color))
                                    .frame(width: 8, height: 8)
                                Text(tag.name)
                                    .font(.caption)

                                Button {
                                    Task { await removeTag(tag) }
                                } label: {
                                    Image(systemName: "xmark")
                                        .font(.caption2)
                                }
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color(.systemGray6))
                            .cornerRadius(16)
                        }
                    }
                    .padding()
                }
            }
        }
    }

    // MARK: - Actions

    private func loadDetails() async {
        isLoading = true
        async let c = repository.getComments(fileId: file.id)
        async let v = repository.getFileVersions(fileId: file.id)
        async let t = repository.getItemTags(itemId: file.id, itemType: "file")

        comments = (try? await c) ?? []
        versions = (try? await v) ?? []
        tags = (try? await t) ?? []
        isLoading = false
    }

    private func addComment() async {
        let text = newComment.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        do {
            let comment = try await repository.addComment(fileId: file.id, text: text)
            comments.append(comment)
            newComment = ""
        } catch {
            downloadError = "Failed to add comment: \(error.localizedDescription)"
        }
    }

    private func deleteComment(_ commentId: String) async {
        do {
            try await repository.deleteComment(commentId: commentId)
            comments.removeAll { $0.id == commentId }
        } catch {}
    }

    private func restoreVersion(_ version: DriveVersion) async {
        do {
            try await repository.restoreVersion(fileId: file.id, versionId: version.id)
            await loadDetails()
        } catch {}
    }

    private func removeTag(_ tag: DriveTag) async {
        do {
            try await repository.removeTag(tagId: tag.id, itemId: file.id, itemType: "file")
            tags.removeAll { $0.id == tag.id }
        } catch {}
    }

    private func shareFile() async {
        do {
            let link = try await repository.generateShareLink(fileId: file.id, expiry: "24h")
            shareURL = URL(string: link.url)
            showShareSheet = true
        } catch {
            downloadError = "Failed to generate share link: \(error.localizedDescription)"
            #if DEBUG
            print("🔴 Share error: \(error)")
            #endif
        }
    }

    private func downloadFile() async {
        isDownloading = true
        downloadError = nil
        do {
            let streamUrl = try await repository.getFileStreamUrl(fileId: file.id)
            guard let url = URL(string: streamUrl) else {
                downloadError = "Invalid download URL"
                isDownloading = false
                return
            }
            let (data, response) = try await URLSession.shared.data(from: url)

            // Check HTTP status
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
                downloadError = "Download failed (HTTP \(httpResponse.statusCode))"
                isDownloading = false
                return
            }

            guard !data.isEmpty else {
                downloadError = "Downloaded file is empty"
                isDownloading = false
                return
            }

            let tempDir = FileManager.default.temporaryDirectory
            let fileURL = tempDir.appendingPathComponent(file.fileName)

            // Remove existing file if present
            if FileManager.default.fileExists(atPath: fileURL.path) {
                try? FileManager.default.removeItem(at: fileURL)
            }

            try data.write(to: fileURL)
            previewURL = fileURL
            isDownloading = false
        } catch {
            let errorMsg = error.localizedDescription
            if errorMsg.contains("insufficient_permissions") || errorMsg.contains("403") || errorMsg.contains("Forbidden") {
                downloadError = "You don't have download permission for this file"
            } else {
                downloadError = "Download failed: \(errorMsg)"
            }
            isDownloading = false
            #if DEBUG
            print("🔴 Download error: \(error)")
            #endif
        }
    }

    /// Preview uses the /preview endpoint which only requires VIEW permission (not download)
    private func previewFile() async {
        isDownloading = true
        downloadError = nil
        do {
            // Use preview endpoint - only requires view access
            let previewUrlStr = try await repository.getFilePreviewUrl(fileId: file.id)
            guard let url = URL(string: previewUrlStr) else {
                downloadError = "Invalid preview URL"
                isDownloading = false
                return
            }
            let (data, response) = try await URLSession.shared.data(from: url)

            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
                downloadError = "Preview failed (HTTP \(httpResponse.statusCode))"
                isDownloading = false
                return
            }

            guard !data.isEmpty else {
                downloadError = "Preview file is empty"
                isDownloading = false
                return
            }

            let tempDir = FileManager.default.temporaryDirectory
            let fileURL = tempDir.appendingPathComponent("preview_\(file.fileName)")

            if FileManager.default.fileExists(atPath: fileURL.path) {
                try? FileManager.default.removeItem(at: fileURL)
            }

            try data.write(to: fileURL)
            previewURL = fileURL
            isDownloading = false
        } catch {
            let errorMsg = error.localizedDescription
            if errorMsg.contains("insufficient_permissions") || errorMsg.contains("403") || errorMsg.contains("Forbidden") {
                downloadError = "You don't have permission to view this file"
            } else {
                downloadError = "Preview failed: \(errorMsg)"
            }
            isDownloading = false
            #if DEBUG
            print("🔴 Preview error: \(error)")
            #endif
        }
    }

    // MARK: - Media Preview

    private func loadMediaPreview() async {
        guard FileUtils.isImage(file.fileExtension) || FileUtils.isVideo(file.fileExtension) else { return }
        do {
            let urlStr = try await repository.getFilePreviewUrl(fileId: file.id)
            if FileUtils.isImage(file.fileExtension) {
                mediaPreviewUrl = urlStr
            } else if FileUtils.isVideo(file.fileExtension), let url = URL(string: urlStr) {
                videoPlayer = AVPlayer(url: url)
            }
        } catch {
            #if DEBUG
            print("🔴 Media preview load error: \(error)")
            #endif
        }
    }

    // MARK: - Helpers

    private func actionButton(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.title3)
                Text(title)
                    .font(.caption)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(.orange)
    }

    private var iconName: String {
        if FileUtils.isImage(file.fileExtension) { return "photo" }
        if FileUtils.isVideo(file.fileExtension) { return "film" }
        if FileUtils.isAudio(file.fileExtension) { return "music.note" }
        if FileUtils.isPDF(file.fileExtension) { return "doc.richtext" }
        if FileUtils.isOffice(file.fileExtension) { return "doc.text" }
        return "doc"
    }

    private func formatDate(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Flow Layout

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
        }

        return (CGSize(width: maxWidth, height: y + rowHeight), positions)
    }
}

// MARK: - Color from hex

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xFF) / 255.0
        let g = Double((int >> 8) & 0xFF) / 255.0
        let b = Double(int & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
