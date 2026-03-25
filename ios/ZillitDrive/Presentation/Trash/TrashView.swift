import SwiftUI

struct TrashView: View {
    @State private var items: [TrashItemDTO] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showEmptyConfirm = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        Group {
            if isLoading && items.isEmpty {
                ProgressView("Loading trash...")
            } else if items.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "trash")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("Trash is empty")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
            } else {
                List {
                    ForEach(items) { item in
                        HStack(spacing: 12) {
                            Image(systemName: item.itemType == "folder" ? "folder.fill" : "doc")
                                .foregroundColor(item.itemType == "folder" ? .orange : .blue)
                                .frame(width: 32)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name ?? "Unknown")
                                    .font(.body)
                                    .lineLimit(1)
                                if let deletedOn = item.deletedOn {
                                    Text("Deleted \(formatDate(deletedOn))")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }

                            Spacer()

                            Menu {
                                Button {
                                    Task { await restore(item) }
                                } label: {
                                    Label("Restore", systemImage: "arrow.uturn.backward")
                                }
                                Button(role: .destructive) {
                                    Task { await permanentDelete(item) }
                                } label: {
                                    Label("Delete Permanently", systemImage: "trash.slash")
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Trash")
        .toolbar {
            if !items.isEmpty {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Empty Trash") {
                        showEmptyConfirm = true
                    }
                    .foregroundColor(.red)
                }
            }
        }
        .alert("Empty Trash?", isPresented: $showEmptyConfirm) {
            Button("Cancel", role: .cancel) {}
            Button("Empty", role: .destructive) {
                Task { await emptyAllTrash() }
            }
        } message: {
            Text("This will permanently delete all items in trash. This action cannot be undone.")
        }
        .refreshable { await loadTrash() }
        .task { await loadTrash() }
    }

    private func loadTrash() async {
        isLoading = true
        do {
            items = try await repository.getTrash()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func restore(_ item: TrashItemDTO) async {
        do {
            try await repository.restoreTrashItem(type: item.itemType, itemId: item.id)
            await loadTrash()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func permanentDelete(_ item: TrashItemDTO) async {
        do {
            try await repository.permanentDeleteTrashItem(type: item.itemType, itemId: item.id)
            await loadTrash()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func emptyAllTrash() async {
        do {
            try await repository.emptyTrash()
            await loadTrash()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func formatDate(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
