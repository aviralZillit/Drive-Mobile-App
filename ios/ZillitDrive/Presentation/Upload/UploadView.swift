import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct UploadView: View {
    @StateObject private var uploadManager = UploadManager.shared
    @State private var showFilePicker = false
    @State private var showPhotoPicker = false
    @State private var showCamera = false
    @State private var selectedPhotos: [PhotosPickerItem] = []

    var body: some View {
        Group {
            if uploadManager.uploads.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "arrow.up.doc")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("No active uploads")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    Text("Tap + to upload files")
                        .font(.subheadline)
                        .foregroundColor(.secondary)

                    VStack(spacing: 12) {
                        HStack(spacing: 16) {
                            Button {
                                showPhotoPicker = true
                            } label: {
                                Label("Photos & Videos", systemImage: "photo.on.rectangle")
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.orange)

                            Button {
                                showFilePicker = true
                            } label: {
                                Label("Files", systemImage: "folder")
                            }
                            .buttonStyle(.bordered)
                            .tint(.orange)
                        }

                        Button {
                            showCamera = true
                        } label: {
                            Label("Take Photo / Video", systemImage: "camera.fill")
                        }
                        .buttonStyle(.bordered)
                        .tint(.orange)
                    }
                }
            } else {
                List {
                    ForEach(uploadManager.uploads) { upload in
                        UploadRow(upload: upload)
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Uploads")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button {
                        showPhotoPicker = true
                    } label: {
                        Label("Photos & Videos", systemImage: "photo.on.rectangle")
                    }
                    Button {
                        showFilePicker = true
                    } label: {
                        Label("Files", systemImage: "folder")
                    }
                    Button {
                        showCamera = true
                    } label: {
                        Label("Camera", systemImage: "camera")
                    }
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { capturedURL in
                uploadManager.addFiles([capturedURL], folderId: nil)
            }
            .ignoresSafeArea()
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.item, .image, .movie, .audio, .pdf, .data],
            allowsMultipleSelection: true
        ) { result in
            if case .success(let urls) = result {
                uploadManager.addFiles(urls, folderId: nil)
            }
        }
        .photosPicker(
            isPresented: $showPhotoPicker,
            selection: $selectedPhotos,
            maxSelectionCount: 20,
            matching: .any(of: [.images, .videos])
        )
        .onChange(of: selectedPhotos) { newItems in
            Task {
                var urls: [URL] = []
                for item in newItems {
                    if let url = await exportPhotoItem(item) {
                        urls.append(url)
                    }
                }
                if !urls.isEmpty {
                    uploadManager.addFiles(urls, folderId: nil)
                }
                selectedPhotos = []
            }
        }
    }

    private func exportPhotoItem(_ item: PhotosPickerItem) async -> URL? {
        if let movie = try? await item.loadTransferable(type: VideoTransferable.self) {
            return movie.url
        }
        if let data = try? await item.loadTransferable(type: Data.self) {
            let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
            let fileName = "photo_\(UUID().uuidString.prefix(8)).\(ext)"
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try? data.write(to: tempURL)
            return tempURL
        }
        return nil
    }
}

// MARK: - Upload Row

struct UploadRow: View {
    let upload: UploadFileEntry
    @ObservedObject private var manager = UploadManager.shared

    var body: some View {
        HStack(spacing: 12) {
            // Status icon
            statusIcon
                .frame(width: 32, height: 32)

            // Info
            VStack(alignment: .leading, spacing: 4) {
                Text(upload.fileName)
                    .font(.body)
                    .lineLimit(1)

                ProgressView(value: min(max(upload.progress, 0), 1.0))
                    .tint(progressColor)

                Text(upload.statusText)
                    .font(.caption)
                    .foregroundColor(statusColor)
            }

            Spacer()

            // Action buttons
            HStack(spacing: 8) {
                switch upload.status {
                case .uploading:
                    Button { manager.pauseUpload(upload.id) } label: {
                        Image(systemName: "pause.circle.fill")
                            .font(.title3)
                            .foregroundColor(.orange)
                    }
                    Button { manager.cancelUpload(upload.id) } label: {
                        Image(systemName: "xmark.circle")
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                case .paused:
                    Button { manager.resumeUpload(upload.id) } label: {
                        Image(systemName: "play.circle.fill")
                            .font(.title3)
                            .foregroundColor(.green)
                    }
                    Button { manager.cancelUpload(upload.id) } label: {
                        Image(systemName: "xmark.circle")
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                case .failed:
                    Button { manager.retryUpload(upload.id) } label: {
                        Image(systemName: "arrow.clockwise.circle.fill")
                            .font(.title3)
                            .foregroundColor(.orange)
                    }
                    Button { manager.dismissUpload(upload.id) } label: {
                        Image(systemName: "xmark.circle")
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                case .completed:
                    Button { manager.dismissUpload(upload.id) } label: {
                        Image(systemName: "xmark.circle")
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                case .cancelled:
                    Button { manager.dismissUpload(upload.id) } label: {
                        Image(systemName: "xmark.circle")
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                case .queued:
                    Button { manager.cancelUpload(upload.id) } label: {
                        Image(systemName: "xmark.circle")
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch upload.status {
        case .queued:
            Image(systemName: "clock")
                .foregroundColor(.secondary)
        case .uploading:
            Image(systemName: "arrow.up.circle.fill")
                .foregroundColor(.blue)
        case .paused:
            Image(systemName: "pause.circle.fill")
                .foregroundColor(.orange)
        case .completed:
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.green)
        case .failed:
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(.red)
        case .cancelled:
            Image(systemName: "xmark.circle.fill")
                .foregroundColor(.secondary)
        }
    }

    private var progressColor: Color {
        switch upload.status {
        case .failed, .cancelled: return .red
        case .completed: return .green
        case .paused: return .orange
        default: return .blue
        }
    }

    private var statusColor: Color {
        switch upload.status {
        case .failed: return .red
        case .completed: return .green
        default: return .secondary
        }
    }
}

// MARK: - Video Transferable

struct VideoTransferable: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { video in
            SentTransferredFile(video.url)
        } importing: { received in
            let tempURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("video_\(UUID().uuidString.prefix(8)).\(received.file.pathExtension)")
            try FileManager.default.copyItem(at: received.file, to: tempURL)
            return Self(url: tempURL)
        }
    }
}
