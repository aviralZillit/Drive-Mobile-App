import Foundation

/// Disk-based offline cache for Drive contents.
/// Stores JSON to Library/Caches/ for fast app launch without network.
actor OfflineCache {
    static let shared = OfflineCache()

    private let cacheDir: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {
        let cachesDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        cacheDir = cachesDir.appendingPathComponent("drive_offline", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
    }

    // MARK: - Folder Contents

    func saveFolderContents(_ contents: CachedContents, forKey key: String) {
        let file = cacheDir.appendingPathComponent("contents_\(key.hashValue).json")
        if let data = try? encoder.encode(contents) {
            try? data.write(to: file, options: .atomic)
        }
    }

    func loadFolderContents(forKey key: String) -> CachedContents? {
        let file = cacheDir.appendingPathComponent("contents_\(key.hashValue).json")
        guard let data = try? Data(contentsOf: file) else { return nil }
        return try? decoder.decode(CachedContents.self, from: data)
    }

    func invalidate(forKey key: String) {
        let file = cacheDir.appendingPathComponent("contents_\(key.hashValue).json")
        try? FileManager.default.removeItem(at: file)
    }

    func invalidateAll() {
        let files = (try? FileManager.default.contentsOfDirectory(at: cacheDir, includingPropertiesForKeys: nil)) ?? []
        for file in files {
            try? FileManager.default.removeItem(at: file)
        }
    }

    // MARK: - Network Status

    var isOffline: Bool {
        // Simple check — in production use NWPathMonitor
        let url = URL(string: "https://driveapi-dev.zillit.com")!
        var request = URLRequest(url: url, timeoutInterval: 3)
        request.httpMethod = "HEAD"
        let semaphore = DispatchSemaphore(value: 0)
        var isReachable = true
        URLSession.shared.dataTask(with: request) { _, response, error in
            if error != nil { isReachable = false }
            semaphore.signal()
        }.resume()
        semaphore.wait()
        return !isReachable
    }
}

// MARK: - Cached Data Model

struct CachedContents: Codable {
    let folders: [CachedFolder]
    let files: [CachedFile]
    let timestamp: Date

    var age: TimeInterval { Date().timeIntervalSince(timestamp) }
    var isStale: Bool { age > 300 } // 5 minutes
}

struct CachedFolder: Codable {
    let id: String
    let folderName: String
    let parentFolderId: String?
    let description: String?
    let createdBy: String
    let createdOn: Int64
    let updatedOn: Int64
    let fileCount: Int
    let folderCount: Int
}

struct CachedFile: Codable {
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
}
