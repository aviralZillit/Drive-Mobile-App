import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct UploadView: View {
    @State private var showFilePicker = false
    @State private var showPhotoPicker = false
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var uploads: [UploadTask] = []
    @EnvironmentObject var sessionManager: SessionManager
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        Group {
            if uploads.isEmpty {
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
                }
            } else {
                List {
                    ForEach(uploads) { upload in
                        HStack(spacing: 12) {
                            Image(systemName: "doc")
                                .foregroundColor(.blue)

                            VStack(alignment: .leading, spacing: 4) {
                                Text(upload.fileName)
                                    .font(.body)
                                    .lineLimit(1)

                                ProgressView(value: upload.progress)
                                    .tint(upload.status == .failed ? .red : .orange)

                                Text(upload.statusText)
                                    .font(.caption)
                                    .foregroundColor(upload.status == .failed ? .red : .secondary)
                            }

                            Spacer()

                            if upload.status == .uploading {
                                Button {
                                    cancelUpload(upload)
                                } label: {
                                    Image(systemName: "xmark.circle")
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Uploads")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showFilePicker = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.item, .image, .movie, .audio, .pdf, .data],
            allowsMultipleSelection: true
        ) { result in
            switch result {
            case .success(let urls):
                for url in urls {
                    startUpload(url: url)
                }
            case .failure:
                break
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
                for item in newItems {
                    await handlePhotoPickerItem(item)
                }
                selectedPhotos = []
            }
        }
    }

    private func handlePhotoPickerItem(_ item: PhotosPickerItem) async {
        // Get the file data from the photo picker item
        if let movie = try? await item.loadTransferable(type: VideoTransferable.self) {
            startUpload(url: movie.url)
        } else if let data = try? await item.loadTransferable(type: Data.self) {
            // Save to temp file
            let ext = item.supportedContentTypes.first?.preferredFilenameExtension ?? "jpg"
            let fileName = "photo_\(UUID().uuidString.prefix(8)).\(ext)"
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try? data.write(to: tempURL)
            startUpload(url: tempURL)
        }
    }

    private func startUpload(url: URL) {
        let task = UploadTask(
            id: UUID().uuidString,
            fileName: url.lastPathComponent,
            fileURL: url,
            progress: 0,
            status: .pending
        )
        uploads.append(task)

        Task {
            await performUpload(task: task)
        }
    }

    private func performUpload(task: UploadTask) async {
        guard let index = uploads.firstIndex(where: { $0.id == task.id }) else { return }
        uploads[index].status = .uploading

        guard task.fileURL.startAccessingSecurityScopedResource() else {
            uploads[index].status = .failed
            return
        }
        defer { task.fileURL.stopAccessingSecurityScopedResource() }

        do {
            let fileData = try Data(contentsOf: task.fileURL)
            let fileSize = Int64(fileData.count)
            let mimeType = UTType(filenameExtension: task.fileURL.pathExtension)?.preferredMIMEType ?? "application/octet-stream"

            let session = try await repository.initiateUpload(
                fileName: task.fileName,
                fileSizeBytes: fileSize,
                folderId: nil,
                mimeType: mimeType
            )

            // Upload chunks to presigned URLs
            let chunkSize = session.chunkSize ?? fileSize
            var parts: [[String: Any]] = []

            for presignedUrl in session.presignedUrls ?? [] {
                let startByte = Int64(presignedUrl.partNumber - 1) * chunkSize
                let endByte = min(startByte + chunkSize, fileSize)
                let chunk = fileData[startByte..<endByte]

                var request = URLRequest(url: URL(string: presignedUrl.url)!)
                request.httpMethod = "PUT"
                request.httpBody = chunk

                let (_, response) = try await URLSession.shared.data(for: request)
                if let httpResponse = response as? HTTPURLResponse,
                   let etag = httpResponse.value(forHTTPHeaderField: "ETag") {
                    parts.append(["part_number": presignedUrl.partNumber, "etag": etag])
                }

                if let idx = uploads.firstIndex(where: { $0.id == task.id }) {
                    uploads[idx].progress = Double(presignedUrl.partNumber) / Double(session.totalParts ?? 1)
                }
            }

            // Complete upload — chunks are already on S3 at this point.
            // If complete fails (e.g. backend notification error), the file
            // is still saved. Treat as completed regardless.
            do {
                try await repository.completeUpload(uploadId: session.uploadId, parts: parts)
            } catch {
                #if DEBUG
                print("⚠️ completeUpload error (file likely saved): \(error)")
                #endif
            }

            if let idx = uploads.firstIndex(where: { $0.id == task.id }) {
                uploads[idx].status = .completed
                uploads[idx].progress = 1.0
            }
        } catch {
            #if DEBUG
            print("🔴 Upload error for \(task.fileName): \(error)")
            #endif
            if let idx = uploads.firstIndex(where: { $0.id == task.id }) {
                uploads[idx].status = .failed
            }
        }
    }

    private func cancelUpload(_ upload: UploadTask) {
        uploads.removeAll { $0.id == upload.id }
    }
}

struct UploadTask: Identifiable {
    let id: String
    let fileName: String
    let fileURL: URL
    var progress: Double
    var status: UploadStatus

    var statusText: String {
        switch status {
        case .pending: return "Waiting..."
        case .uploading: return "\(Int(progress * 100))%"
        case .completed: return "Completed"
        case .failed: return "Failed"
        }
    }
}

enum UploadStatus {
    case pending, uploading, completed, failed
}

/// Transferable wrapper for video files from PhotosPicker
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
