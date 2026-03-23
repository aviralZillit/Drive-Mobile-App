import SwiftUI

struct FavoritesView: View {
    @State private var items: [DriveItem] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        Group {
            if isLoading && items.isEmpty {
                ProgressView("Loading favorites...")
            } else if items.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "star")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("No favorites yet")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    Text("Star files and folders to see them here")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            } else {
                List(items) { item in
                    DriveItemRow(
                        item: item,
                        isFavorite: true,
                        onFavorite: {
                            Task { await toggleFavorite(item) }
                        }
                    )
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Favorites")
        .refreshable { await loadFavorites() }
        .task { await loadFavorites() }
    }

    private func loadFavorites() async {
        isLoading = true
        do {
            let favIds = try await repository.getFavoriteIds()
            let favoriteFileIds = Set(favIds.fileIds ?? [])
            let favoriteFolderIds = Set(favIds.folderIds ?? [])

            var result: [DriveItem] = []

            // Load favorite files
            if !favoriteFileIds.isEmpty {
                let files = try await repository.getFiles(options: [:])
                result += files.filter { favoriteFileIds.contains($0.id) }
                    .map { var f = $0; f.isFavorite = true; return .file(f) }
            }

            // Load favorite folders
            if !favoriteFolderIds.isEmpty {
                let folders = try await repository.getFolders(options: [:])
                result += folders.filter { favoriteFolderIds.contains($0.id) }
                    .map { var f = $0; f.isFavorite = true; return .folder(f) }
            }

            items = result
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func toggleFavorite(_ item: DriveItem) async {
        let itemType = item.isFileItem ? "file" : "folder"
        do {
            try await repository.toggleFavorite(itemId: item.id, itemType: itemType)
            await loadFavorites()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
