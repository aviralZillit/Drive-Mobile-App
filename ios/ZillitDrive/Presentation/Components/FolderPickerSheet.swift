import SwiftUI

struct FolderPickerSheet: View {
    let excludeFolderId: String?
    let currentFolderId: String?
    let onSelect: (String?) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var folders: [DriveFolder] = []
    @State private var isLoading = false
    @State private var browseFolderId: String?
    @State private var breadcrumbs: [(id: String?, name: String)] = [(id: nil, name: "Root")]
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        VStack(spacing: 0) {
            // Breadcrumb path
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    ForEach(Array(breadcrumbs.enumerated()), id: \.offset) { index, crumb in
                        if index > 0 {
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        Button(crumb.name) {
                            let target = crumb.id
                            breadcrumbs = Array(breadcrumbs.prefix(index + 1))
                            browseFolderId = target
                            Task { await loadFolders(parentId: target) }
                        }
                        .font(.caption)
                        .foregroundColor(index == breadcrumbs.count - 1 ? .primary : .orange)
                    }
                }
                .padding(.horizontal)
                .padding(.vertical, 8)
            }

            Divider()

            if isLoading {
                Spacer()
                ProgressView()
                Spacer()
            } else if folders.isEmpty {
                Spacer()
                Text("No subfolders")
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                List(folders) { folder in
                    Button {
                        breadcrumbs.append((id: folder.id, name: folder.folderName))
                        browseFolderId = folder.id
                        Task { await loadFolders(parentId: folder.id) }
                    } label: {
                        HStack {
                            Image(systemName: "folder.fill")
                                .foregroundColor(.orange)
                            Text(folder.folderName)
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .listStyle(.plain)
            }

            Divider()

            // Move Here button
            Button {
                onSelect(browseFolderId)
                dismiss()
            } label: {
                HStack {
                    Image(systemName: "folder.badge.arrow.forward")
                    Text("Move Here")
                        .fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.orange)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            .padding()
        }
        .navigationTitle("Move to")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
        }
        .task {
            await loadFolders(parentId: nil)
        }
    }

    private func loadFolders(parentId: String?) async {
        isLoading = true
        do {
            var options: [String: String] = ["sort_by": "name", "sort_order": "asc"]
            if let pid = parentId {
                options["parent_folder_id"] = pid
            }
            let allFolders = try await repository.getFolders(options: options)
            // Filter: only folders at this level, exclude the item being moved
            folders = allFolders.filter { folder in
                let matchesParent = parentId == nil
                    ? (folder.parentFolderId == nil || folder.parentFolderId?.isEmpty == true)
                    : folder.parentFolderId == parentId
                let notExcluded = folder.id != excludeFolderId
                let notCurrent = folder.id != currentFolderId
                return matchesParent && notExcluded && notCurrent
            }
        } catch {
            folders = []
        }
        isLoading = false
    }
}
