import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var items: [DriveItem] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var breadcrumbs: [BreadcrumbItem] = [BreadcrumbItem(id: nil, name: "My Drive")]
    @Published var favoriteFileIds: Set<String> = []
    @Published var favoriteFolderIds: Set<String> = []
    @Published var sortBy: SortOption = .name
    @Published var isGridView = false
    @Published var showCreateFolderSheet = false
    @Published var newFolderName = ""
    @Published var driveSection: DriveSection = .myDrive
    @Published var folderBadges: [String: Int] = [:]
    @Published var fileBadges: Set<String> = []

    private let repository: DriveRepository
    private let socketManager = DriveSocketManager()
    private var loadTask: Task<Void, Never>?
    private var loadId: UInt64 = 0

    init(repository: DriveRepository = DriveRepositoryImpl()) {
        self.repository = repository
        setupSocket()
    }

    var currentFolderId: String? {
        breadcrumbs.last?.id
    }

    // MARK: - Load Contents

    func loadContents() async {
        // Increment load ID — any in-flight request with an older ID is stale
        loadId &+= 1
        let myLoadId = loadId

        isLoading = true
        errorMessage = nil

        let fileOpts = buildFileOptions()
        let folderOpts = buildFolderOptions()
        #if DEBUG
        print("[loadContents] id=\(myLoadId) section=\(driveSection) folderId=\(currentFolderId ?? "root") fileOpts=\(fileOpts) folderOpts=\(folderOpts)")
        #endif

        do {
            async let favIdsTask = try? repository.getFavoriteIds()
            let contentsResult = try await repository.getFolderContents(
                fileOptions: fileOpts, folderOptions: folderOpts
            )

            // Stale check — if another load started while we were waiting, discard
            guard myLoadId == loadId else {
                #if DEBUG
                print("[loadContents] id=\(myLoadId) DISCARDED (current=\(loadId))")
                #endif
                return
            }

            if let favResult = await favIdsTask {
                favoriteFileIds = Set(favResult.fileIds ?? [])
                favoriteFolderIds = Set(favResult.folderIds ?? [])
            }

            // Client-side filtering (matches web's combinedData useMemo)
            let filteredFolders: [DriveFolder]
            let filteredFiles: [DriveFile]
            if let folderId = currentFolderId {
                filteredFolders = contentsResult.folders.filter { $0.parentFolderId == folderId }
                filteredFiles = contentsResult.files.filter { $0.folderId == folderId }
            } else {
                filteredFolders = contentsResult.folders.filter { $0.parentFolderId == nil || $0.parentFolderId?.isEmpty == true }
                filteredFiles = contentsResult.files.filter { $0.folderId == nil || $0.folderId?.isEmpty == true }
            }

            let folders: [DriveItem] = filteredFolders.map { folder in
                var f = folder
                f.isFavorite = favoriteFolderIds.contains(f.id)
                return .folder(f)
            }
            let files: [DriveItem] = filteredFiles.map { file in
                var f = file
                f.isFavorite = favoriteFileIds.contains(f.id)
                return .file(f)
            }

            var allItems = folders + files
            allItems = sortItems(allItems)
            isLoading = false
            items = allItems
            #if DEBUG
            print("[loadContents] id=\(myLoadId) DONE — \(allItems.count) items")
            #endif

            // Load thumbnail preview URLs for image/video files in background
            Task { [weak self] in
                guard let self else { return }
                for (index, item) in allItems.enumerated() {
                    guard myLoadId == self.loadId else { return }
                    if case .file(let file) = item,
                       (FileUtils.isImage(file.fileExtension) || FileUtils.isVideo(file.fileExtension)) {
                        if let url = try? await self.repository.getFilePreviewUrl(fileId: file.id) {
                            // Update the item in place
                            if myLoadId == self.loadId, index < self.items.count,
                               case .file(var f) = self.items[index], f.id == file.id {
                                f.previewUrl = url
                                self.items[index] = .file(f)
                            }
                        }
                    }
                }
            }
        } catch {
            guard myLoadId == loadId else { return }
            isLoading = false
            errorMessage = error.localizedDescription
            #if DEBUG
            print("🔴 loadContents id=\(myLoadId) error: \(error)")
            #endif
        }
    }

    /// Force-refresh from network (for pull-to-refresh)
    func forceLoadContents() async {
        isLoading = true
        errorMessage = nil

        do {
            async let favIdsTask = try? repository.getFavoriteIds()
            let contentsResult = try await repository.forceGetFolderContents(
                fileOptions: buildFileOptions(), folderOptions: buildFolderOptions()
            )

            if let favResult = await favIdsTask {
                favoriteFileIds = Set(favResult.fileIds ?? [])
                favoriteFolderIds = Set(favResult.folderIds ?? [])
            }

            // Client-side filtering (same as loadContents)
            let filteredFolders: [DriveFolder]
            let filteredFiles: [DriveFile]
            if let folderId = currentFolderId {
                filteredFolders = contentsResult.folders.filter { $0.parentFolderId == folderId }
                filteredFiles = contentsResult.files.filter { $0.folderId == folderId }
            } else {
                filteredFolders = contentsResult.folders.filter { $0.parentFolderId == nil || $0.parentFolderId?.isEmpty == true }
                filteredFiles = contentsResult.files.filter { $0.folderId == nil || $0.folderId?.isEmpty == true }
            }

            let folders: [DriveItem] = filteredFolders.map { folder in
                var f = folder
                f.isFavorite = favoriteFolderIds.contains(f.id)
                return .folder(f)
            }
            let files: [DriveItem] = filteredFiles.map { file in
                var f = file
                f.isFavorite = favoriteFileIds.contains(f.id)
                return .file(f)
            }

            var allItems = folders + files
            allItems = sortItems(allItems)
            isLoading = false
            items = allItems
        } catch {
            isLoading = false
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Navigation

    func navigateToFolder(_ folder: DriveFolder) {
        loadTask?.cancel()
        items = []
        breadcrumbs.append(BreadcrumbItem(id: folder.id, name: folder.folderName))
        markFolderRead(folder.id)
        loadTask = Task { await loadContents() }
    }

    func navigateToBreadcrumb(_ crumb: BreadcrumbItem) {
        guard let index = breadcrumbs.firstIndex(where: { $0.id == crumb.id && $0.name == crumb.name }) else { return }
        loadTask?.cancel()
        items = []
        breadcrumbs = Array(breadcrumbs.prefix(through: index))
        loadTask = Task { await loadContents() }
    }

    func navigateBack() -> Bool {
        guard breadcrumbs.count > 1 else { return false }
        loadTask?.cancel()
        items = []
        breadcrumbs.removeLast()
        loadTask = Task { await loadContents() }
        return true
    }

    // MARK: - Actions

    func toggleFavorite(item: DriveItem) async {
        let itemType = item.isFileItem ? "file" : "folder"

        // Optimistic local update first (no network wait for UI)
        let isFav: Bool
        switch item {
        case .file:
            isFav = favoriteFileIds.contains(item.id)
            if isFav { favoriteFileIds.remove(item.id) } else { favoriteFileIds.insert(item.id) }
        case .folder:
            isFav = favoriteFolderIds.contains(item.id)
            if isFav { favoriteFolderIds.remove(item.id) } else { favoriteFolderIds.insert(item.id) }
        }

        // Update in-place without full reload
        items = items.map { i in
            guard i.id == item.id else { return i }
            switch i {
            case .file(var f):
                f.isFavorite = !isFav
                return .file(f)
            case .folder(var f):
                f.isFavorite = !isFav
                return .folder(f)
            }
        }

        // Fire-and-forget API call
        do {
            try await repository.toggleFavorite(itemId: item.id, itemType: itemType)
        } catch {
            // Revert on failure
            switch item {
            case .file:
                if isFav { favoriteFileIds.insert(item.id) } else { favoriteFileIds.remove(item.id) }
            case .folder:
                if isFav { favoriteFolderIds.insert(item.id) } else { favoriteFolderIds.remove(item.id) }
            }
            items = items.map { i in
                guard i.id == item.id else { return i }
                switch i {
                case .file(var f):
                    f.isFavorite = isFav
                    return .file(f)
                case .folder(var f):
                    f.isFavorite = isFav
                    return .folder(f)
                }
            }
            errorMessage = error.localizedDescription
        }
    }

    func deleteItem(_ item: DriveItem) async {
        // Remove from list immediately
        items.removeAll { $0.id == item.id }

        do {
            switch item {
            case .file(let file):
                try await repository.deleteFile(fileId: file.id)
            case .folder(let folder):
                try await repository.deleteFolder(folderId: folder.id)
            }
        } catch {
            // Revert on failure - reload full list
            errorMessage = error.localizedDescription
            await loadContents()
        }
    }

    func createFolder() async {
        guard !newFolderName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        var data: [String: Any] = ["folder_name": newFolderName.trimmingCharacters(in: .whitespaces)]
        if let folderId = currentFolderId {
            data["parent_folder_id"] = folderId
        }
        do {
            _ = try await repository.createFolder(data: data)
            newFolderName = ""
            showCreateFolderSheet = false
            await loadContents()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func renameItem(_ item: DriveItem, newName: String) async {
        let trimmed = newName.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        do {
            switch item {
            case .file(let file):
                _ = try await repository.updateFile(fileId: file.id, data: ["file_name": trimmed])
            case .folder(let folder):
                _ = try await repository.updateFolder(folderId: folder.id, data: ["folder_name": trimmed])
            }
            await loadContents()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func moveItem(_ item: DriveItem, toFolder folder: DriveFolder) async {
        do {
            switch item {
            case .file(let file):
                try await repository.moveFile(fileId: file.id, targetFolderId: folder.id)
            case .folder(let movedFolder):
                guard movedFolder.id != folder.id else { return }
                try await repository.bulkMove(
                    items: [["item_id": movedFolder.id, "item_type": "folder"]],
                    targetFolderId: folder.id
                )
            }
            await loadContents()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func generateShareLink(fileId: String) async -> ShareLink? {
        do {
            return try await repository.generateShareLink(fileId: fileId, expiry: "24h")
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    func setSortOption(_ option: SortOption) {
        sortBy = option
        items = sortItems(items)
    }

    // MARK: - Private

    /// Options for /files endpoint (uses folder_id)
    private func buildFileOptions() -> [String: String] {
        var options: [String: String] = [:]
        if let folderId = currentFolderId {
            options["folder_id"] = folderId
        } else {
            // My Drive: only user's own items. Shared: only items shared by others.
            options["quick_filter"] = driveSection == .sharedWithMe ? "shared" : "mine"
        }
        options["sort_by"] = sortBy.apiValue
        options["sort_order"] = "asc"
        return options
    }

    /// Options for /folders endpoint (uses parent_folder_id — NOT folder_id)
    private func buildFolderOptions() -> [String: String] {
        var options: [String: String] = [:]
        if let folderId = currentFolderId {
            options["parent_folder_id"] = folderId
        } else {
            options["quick_filter"] = driveSection == .sharedWithMe ? "shared" : "mine"
        }
        options["sort_by"] = sortBy.apiValue
        options["sort_order"] = "asc"
        return options
    }

    func switchSection(_ section: DriveSection) {
        // Note: Picker binding already sets driveSection before this is called,
        // so we just clear state and reload — no guard needed
        loadTask?.cancel()
        items = []
        breadcrumbs = [BreadcrumbItem(id: nil, name: section.displayName)]
        loadTask = Task { await loadContents() }
    }

    // MARK: - Socket.IO Real-Time Events

    private func setupSocket() {
        guard let session = SessionManager.shared.currentSession else { return }
        socketManager.connect(socketURL: AppConfig.socketURL, projectId: session.projectId)

        // Drive data changes → refetch
        let refreshEvents = [
            "drive:file:added", "drive:file:updated", "drive:file:deleted",
            "drive:folder:created", "drive:folder:updated", "drive:folder:deleted",
        ]
        for event in refreshEvents {
            socketManager.on(event) { [weak self] _ in
                Task { @MainActor in await self?.forceLoadContents() }
            }
        }

        // Sharing events → check if relevant to current user, then refetch
        for event in ["drive:folder:shared", "drive:file:shared"] {
            socketManager.on(event) { [weak self] data in
                Task { @MainActor in
                    guard let dict = data.first as? [String: Any],
                          let sharedWith = dict["shared_with"] as? [String],
                          let userId = SessionManager.shared.currentSession?.userId,
                          sharedWith.contains(userId) else { return }
                    await self?.forceLoadContents()
                }
            }
        }

        // New badge received → update badge counts
        socketManager.on("notification:save") { [weak self] data in
            Task { @MainActor in
                guard let dict = data.first as? [String: Any],
                      let tool = dict["tool"] as? String, tool == "drive_label" else { return }
                let level1 = dict["level_1"] as? String
                let level2 = dict["level_2"] as? String
                self?.handleNewBadge(level1: level1, level2: level2)
            }
        }

        // Badge read sync from other devices
        socketManager.on("notification:level:read") { [weak self] data in
            Task { @MainActor in
                guard let dict = data.first as? [String: Any],
                      let tool = dict["tool"] as? String, tool == "drive_label",
                      let level1 = dict["level_1"] as? String else { return }
                self?.folderBadges.removeValue(forKey: level1)
            }
        }
    }

    // MARK: - Badge Management

    func handleNewBadge(level1: String?, level2: String?) {
        if let folderId = level1 {
            folderBadges[folderId, default: 0] += 1
        }
        if let fileId = level2 {
            fileBadges.insert(fileId)
        }
    }

    func markFolderRead(_ folderId: String) {
        folderBadges.removeValue(forKey: folderId)
        // Remove file badges for files in this folder
        let folderFiles = items.compactMap { item -> String? in
            if case .file(let f) = item, f.folderId == folderId { return f.id }
            return nil
        }
        fileBadges.subtract(folderFiles)

        // Emit socket to sync with backend (matches web connectSocket.js)
        guard let session = SessionManager.shared.currentSession else { return }
        socketManager.emit("notification:level:read", [
            "project_id": session.projectId,
            "tool": "drive_label",
            "unit": "drive_file_label",
            "level_1": folderId,
        ])
    }

    private func sortItems(_ items: [DriveItem]) -> [DriveItem] {
        items.sorted { a, b in
            // Folders first
            if a.isFolder && !b.isFolder { return true }
            if !a.isFolder && b.isFolder { return false }

            switch sortBy {
            case .name:
                return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
            case .date:
                return a.createdOn > b.createdOn
            case .size:
                return a.fileSize > b.fileSize
            }
        }
    }
}

enum SortOption: String, CaseIterable {
    case name, date, size

    var displayName: String {
        switch self {
        case .name: return "Name"
        case .date: return "Date"
        case .size: return "Size"
        }
    }

    var apiValue: String {
        switch self {
        case .name: return "name"
        case .date: return "created_on"
        case .size: return "file_size_bytes"
        }
    }
}

// MARK: - DriveItem extensions

extension DriveItem {
    var isFileItem: Bool {
        if case .file = self { return true }
        return false
    }

    var isFolder: Bool {
        if case .folder = self { return true }
        return false
    }

    var fileSize: Int64 {
        switch self {
        case .file(let f): return f.fileSizeBytes
        case .folder: return 0
        }
    }

    var fileExtension: String {
        switch self {
        case .file(let f): return f.fileExtension
        case .folder: return ""
        }
    }
}
