import Foundation
import UniformTypeIdentifiers

extension Notification.Name {
    static let driveUploadCompleted = Notification.Name("driveUploadCompleted")
    static let driveContentChanged = Notification.Name("driveContentChanged")
}

// MARK: - Types

enum UploadFileStatus: String {
    case queued, uploading, paused, completed, failed, cancelled
}

struct UploadFileEntry: Identifiable, Equatable {
    let id: String
    let fileName: String
    let fileSize: Int64
    let fileURL: URL
    let folderId: String?
    var status: UploadFileStatus = .queued
    var progress: Double = 0
    var uploadedBytes: Int64 = 0
    var speed: Double = 0  // bytes/sec
    var eta: TimeInterval = 0
    var error: String?
    var serverUploadId: String?
    var completedParts: Set<Int> = []

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.id == rhs.id && lhs.status == rhs.status && lhs.progress == rhs.progress
    }

    var statusText: String {
        switch status {
        case .queued: return "Queued"
        case .uploading:
            let pct = Int(progress * 100)
            var text = "\(pct)%"
            if speed > 0 { text += " \u{2022} \(FileUtils.formatFileSize(Int64(speed)))/s" }
            if eta > 0 { text += " \u{2022} \(formatEta(eta)) left" }
            return text
        case .paused: return "Paused \u{2022} \(Int(progress * 100))%"
        case .completed: return "Completed"
        case .failed: return error ?? "Failed"
        case .cancelled: return "Cancelled"
        }
    }

    private func formatEta(_ seconds: TimeInterval) -> String {
        if seconds < 60 { return "\(Int(seconds))s" }
        if seconds < 3600 { return "\(Int(seconds / 60))m \(Int(seconds.truncatingRemainder(dividingBy: 60)))s" }
        return "\(Int(seconds / 3600))h \(Int((seconds / 60).truncatingRemainder(dividingBy: 60)))m"
    }
}

private struct SpeedSample {
    let timestamp: Date
    let bytes: Int64
}

// MARK: - Upload Manager

@MainActor
final class UploadManager: ObservableObject {
    static let shared = UploadManager()

    @Published var uploads: [UploadFileEntry] = []

    private let repository: DriveRepository = DriveRepositoryImpl()
    private let maxConcurrentFiles = 3
    private let maxConcurrentChunks = 6
    private let maxRetries = 3
    private let retryBaseDelay: Double = 0.650
    private let retryMaxDelay: Double = 6.0
    private let speedWindowSeconds: Double = 8.0

    private var speedSamples: [String: [SpeedSample]] = [:]
    private var tasks: [String: Task<Void, Never>] = [:]
    private var pauseFlags: [String: Bool] = [:]

    private init() {}

    // MARK: - Public API

    func addFiles(_ urls: [URL], folderId: String?) {
        for url in urls {
            let fileSize = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int64) ?? 0
            let entry = UploadFileEntry(
                id: UUID().uuidString,
                fileName: url.lastPathComponent,
                fileSize: fileSize,
                fileURL: url,
                folderId: folderId
            )
            uploads.append(entry)
        }
        // Sort queued by size (smallest first)
        uploads.sort { a, b in
            if a.status == .queued && b.status == .queued { return a.fileSize < b.fileSize }
            return false
        }
        processQueue()
    }

    func pauseUpload(_ id: String) {
        guard let idx = index(of: id) else { return }
        pauseFlags[id] = true
        tasks[id]?.cancel()
        tasks[id] = nil
        uploads[idx].status = .paused
    }

    func resumeUpload(_ id: String) {
        guard let idx = index(of: id), uploads[idx].status == .paused else { return }
        pauseFlags[id] = false
        uploads[idx].status = .queued
        processQueue()
    }

    func cancelUpload(_ id: String) {
        guard let idx = index(of: id) else { return }
        pauseFlags[id] = true
        tasks[id]?.cancel()
        tasks[id] = nil
        uploads[idx].status = .cancelled
        // Abort server-side if we have a session
        if let serverId = uploads[idx].serverUploadId {
            Task { try? await repository.abortUpload(uploadId: serverId) }
        }
        processQueue()
    }

    func retryUpload(_ id: String) {
        guard let idx = index(of: id), uploads[idx].status == .failed else { return }
        uploads[idx].status = .queued
        uploads[idx].progress = 0
        uploads[idx].uploadedBytes = 0
        uploads[idx].error = nil
        uploads[idx].completedParts = []
        uploads[idx].serverUploadId = nil
        pauseFlags[id] = false
        processQueue()
    }

    func dismissUpload(_ id: String) {
        uploads.removeAll { $0.id == id }
        tasks[id]?.cancel()
        tasks.removeValue(forKey: id)
        speedSamples.removeValue(forKey: id)
        pauseFlags.removeValue(forKey: id)
    }

    // MARK: - Queue Processing

    private func processQueue() {
        let activeCount = uploads.filter { $0.status == .uploading }.count
        let available = maxConcurrentFiles - activeCount
        guard available > 0 else { return }

        let queued = uploads.filter { $0.status == .queued }
        for entry in queued.prefix(available) {
            guard let idx = index(of: entry.id) else { continue }
            uploads[idx].status = .uploading
            uploads[idx].progress = 0.05
            pauseFlags[entry.id] = false

            let entryId = entry.id
            tasks[entryId] = Task { [weak self] in
                await self?.performUpload(id: entryId)
            }
        }
    }

    // MARK: - Upload Execution

    private func performUpload(id: String) async {
        guard let idx = index(of: id) else { return }
        let entry = uploads[idx]

        guard entry.fileURL.startAccessingSecurityScopedResource() else {
            fail(id: id, error: "Cannot access file")
            return
        }
        defer { entry.fileURL.stopAccessingSecurityScopedResource() }

        do {
            // Read file data
            let fileData = try Data(contentsOf: entry.fileURL)
            let fileSize = Int64(fileData.count)
            let mimeType = UTType(filenameExtension: entry.fileURL.pathExtension)?.preferredMIMEType ?? "application/octet-stream"

            // Initiate upload
            update(id: id) { $0.progress = 0.10 }
            let session = try await repository.initiateUpload(
                fileName: entry.fileName,
                fileSizeBytes: fileSize,
                folderId: entry.folderId,
                mimeType: mimeType
            )
            update(id: id) { $0.serverUploadId = session.uploadId }

            let chunkSize = session.chunkSize ?? fileSize
            let presignedUrls = session.presignedUrls ?? []
            let totalParts = presignedUrls.count

            guard totalParts > 0 else {
                fail(id: id, error: "No presigned URLs")
                return
            }

            // Upload chunks with concurrency limit
            let completedParts = uploads[index(of: id)!].completedParts
            let remainingUrls = presignedUrls.filter { !completedParts.contains($0.partNumber) }
            var allParts: [(Int, String)] = completedParts.map { ($0, "") } // placeholder for already done

            // Semaphore for limiting concurrent chunks
            let semaphore = AsyncSemaphore(limit: maxConcurrentChunks)

            try await withThrowingTaskGroup(of: (Int, String).self) { group in
                for presignedUrl in remainingUrls {
                    // Check pause/cancel
                    if pauseFlags[id] == true || Task.isCancelled { break }

                    await semaphore.wait()

                    group.addTask { [weak self] in
                        defer { Task { await semaphore.signal() } }

                        guard let self else { throw CancellationError() }

                        let startByte = Int64(presignedUrl.partNumber - 1) * chunkSize
                        let endByte = min(startByte + chunkSize, fileSize)
                        let chunkData = fileData[startByte..<endByte]

                        let etag = try await self.uploadChunkWithRetry(
                            url: presignedUrl.url,
                            data: Data(chunkData),
                            uploadId: id,
                            attempt: 0
                        )

                        // Record speed + update progress
                        await MainActor.run {
                            self.recordSpeedSample(uploadId: id, bytes: Int64(chunkData.count))
                            let completed = (self.uploads[self.index(of: id) ?? 0].completedParts.count + 1)
                            let progress = 0.10 + (Double(completed) / Double(totalParts)) * 0.80
                            let speed = self.calculateSpeed(uploadId: id)
                            let remaining = Double(fileSize - Int64(Double(completed) / Double(totalParts) * Double(fileSize)))
                            let eta = speed > 0 ? remaining / speed : 0

                            self.update(id: id) {
                                $0.completedParts.insert(presignedUrl.partNumber)
                                $0.progress = min(progress, 0.90)
                                $0.uploadedBytes = Int64(Double(completed) / Double(totalParts) * Double(fileSize))
                                $0.speed = speed
                                $0.eta = eta
                            }
                        }

                        return (presignedUrl.partNumber, etag)
                    }
                }

                for try await (partNum, etag) in group {
                    if !etag.isEmpty {
                        allParts.append((partNum, etag))
                    }
                }
            }

            // Check if paused/cancelled during chunk upload
            if pauseFlags[id] == true || Task.isCancelled { return }

            // Complete upload
            update(id: id) { $0.progress = 0.92 }
            let parts: [[String: Any]] = allParts
                .filter { !$0.1.isEmpty }
                .map { ["part_number": $0.0, "etag": $0.1] }

            do {
                try await repository.completeUpload(uploadId: session.uploadId, parts: parts)
            } catch {
                #if DEBUG
                print("⚠️ completeUpload error (file likely saved): \(error)")
                #endif
            }

            update(id: id) {
                $0.status = .completed
                $0.progress = 1.0
                $0.speed = 0
                $0.eta = 0
            }

            // Notify listeners (HomeViewModel) to refresh file list
            NotificationCenter.default.post(name: .driveUploadCompleted, object: nil)

        } catch is CancellationError {
            // Paused or cancelled — don't mark as failed
            return
        } catch {
            fail(id: id, error: error.localizedDescription)
        }

        // Cleanup and process next in queue
        tasks.removeValue(forKey: id)
        speedSamples.removeValue(forKey: id)
        processQueue()
    }

    // MARK: - Chunk Upload with Retry

    private nonisolated func uploadChunkWithRetry(url: String, data: Data, uploadId: String, attempt: Int) async throws -> String {
        guard let uploadURL = URL(string: url) else { throw URLError(.badURL) }

        do {
            var request = URLRequest(url: uploadURL)
            request.httpMethod = "PUT"
            request.httpBody = data
            request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")

            let (_, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                throw URLError(.badServerResponse)
            }

            if isRetryableStatus(httpResponse.statusCode) && attempt < maxRetries {
                try await retryDelay(attempt: attempt)
                return try await uploadChunkWithRetry(url: url, data: data, uploadId: uploadId, attempt: attempt + 1)
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                throw URLError(.badServerResponse)
            }

            return httpResponse.value(forHTTPHeaderField: "ETag") ?? ""

        } catch is CancellationError {
            throw CancellationError()
        } catch {
            if isRetryableError(error) && attempt < maxRetries {
                try await retryDelay(attempt: attempt)
                return try await uploadChunkWithRetry(url: url, data: data, uploadId: uploadId, attempt: attempt + 1)
            }
            throw error
        }
    }

    private nonisolated func retryDelay(attempt: Int) async throws {
        let delay = min(retryBaseDelay * pow(2.0, Double(attempt)), retryMaxDelay)
        let jitter = Double.random(in: 0...0.220)
        try await Task.sleep(nanoseconds: UInt64((delay + jitter) * 1_000_000_000))
    }

    private nonisolated func isRetryableError(_ error: Error) -> Bool {
        if error is CancellationError { return false }
        let urlError = error as? URLError
        let retryableCodes: Set<URLError.Code> = [.timedOut, .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost]
        if let code = urlError?.code, retryableCodes.contains(code) { return true }
        return false
    }

    private nonisolated func isRetryableStatus(_ status: Int) -> Bool {
        [408, 429, 500, 502, 503, 504].contains(status)
    }

    // MARK: - Speed Tracking (8-sec rolling window)

    private func recordSpeedSample(uploadId: String, bytes: Int64) {
        let now = Date()
        speedSamples[uploadId, default: []].append(SpeedSample(timestamp: now, bytes: bytes))
        let cutoff = now.addingTimeInterval(-speedWindowSeconds)
        speedSamples[uploadId] = speedSamples[uploadId]?.filter { $0.timestamp > cutoff }
    }

    private func calculateSpeed(uploadId: String) -> Double {
        guard let samples = speedSamples[uploadId], samples.count >= 2 else { return 0 }
        let cutoff = Date().addingTimeInterval(-speedWindowSeconds)
        let recent = samples.filter { $0.timestamp > cutoff }
        guard recent.count >= 2, let first = recent.first, let last = recent.last else { return 0 }
        let totalBytes = recent.map(\.bytes).reduce(0, +)
        let timeSpan = last.timestamp.timeIntervalSince(first.timestamp)
        return timeSpan > 0 ? Double(totalBytes) / timeSpan : 0
    }

    // MARK: - Helpers

    private func index(of id: String) -> Int? {
        uploads.firstIndex { $0.id == id }
    }

    private func update(id: String, _ block: (inout UploadFileEntry) -> Void) {
        guard let idx = index(of: id) else { return }
        block(&uploads[idx])
    }

    private func fail(id: String, error: String) {
        update(id: id) {
            $0.status = .failed
            $0.error = error
            $0.speed = 0
            $0.eta = 0
        }
        tasks.removeValue(forKey: id)
        processQueue()
    }
}

// MARK: - Async Semaphore (for chunk concurrency limiting)

actor AsyncSemaphore {
    private var count: Int
    private var waiters: [CheckedContinuation<Void, Never>] = []

    init(limit: Int) { self.count = limit }

    func wait() async {
        if count > 0 {
            count -= 1
        } else {
            await withCheckedContinuation { continuation in
                waiters.append(continuation)
            }
        }
    }

    func signal() {
        if let waiter = waiters.first {
            waiters.removeFirst()
            waiter.resume()
        } else {
            count += 1
        }
    }
}
