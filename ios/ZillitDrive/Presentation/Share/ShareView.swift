import SwiftUI

// MARK: - File Share View

struct FileShareView: View {
    let fileId: String
    @State private var accessEntries: [FileAccessDTO] = []
    @State private var isLoading = false
    @State private var isSaving = false
    @State private var shareLink: ShareLink?
    @State private var showShareSheet = false
    @State private var errorMessage: String?
    @State private var saveSuccess = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        List {
            // Share Link Section
            Section("Share Link") {
                if let link = shareLink {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(link.url)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(2)

                        if let expires = link.expiresAt {
                            Text("Expires: \(expires)")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }

                        HStack {
                            Button {
                                UIPasteboard.general.string = link.url
                            } label: {
                                Label("Copy Link", systemImage: "doc.on.doc")
                            }
                            .buttonStyle(.bordered)

                            Button {
                                showShareSheet = true
                            } label: {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.bordered)
                            .tint(.orange)
                        }
                    }
                } else {
                    Button {
                        Task { await generateLink() }
                    } label: {
                        Label("Generate Share Link", systemImage: "link")
                    }
                }
            }

            // Access Section
            Section {
                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                } else if accessEntries.isEmpty {
                    Text("No access entries")
                        .foregroundColor(.secondary)
                } else {
                    ForEach($accessEntries, id: \.userId) { $entry in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: "person.circle.fill")
                                    .foregroundColor(.orange)
                                    .font(.title3)
                                Text(entry.userId)
                                    .font(.body)
                                    .lineLimit(1)
                                Spacer()
                                Button(role: .destructive) {
                                    accessEntries.removeAll { $0.userId == entry.userId }
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.caption)
                                        .foregroundColor(.red)
                                }
                                .buttonStyle(.borderless)
                            }

                            HStack(spacing: 16) {
                                Toggle("View", isOn: Binding(
                                    get: { entry.canView ?? false },
                                    set: { entry = FileAccessDTO(
                                        userId: entry.userId,
                                        fileId: entry.fileId,
                                        canView: $0,
                                        canEdit: entry.canEdit,
                                        canDownload: entry.canDownload
                                    ) }
                                ))
                                .toggleStyle(.switch)
                                .tint(.green)
                            }
                            .font(.caption)

                            HStack(spacing: 16) {
                                Toggle("Edit", isOn: Binding(
                                    get: { entry.canEdit ?? false },
                                    set: { entry = FileAccessDTO(
                                        userId: entry.userId,
                                        fileId: entry.fileId,
                                        canView: entry.canView,
                                        canEdit: $0,
                                        canDownload: entry.canDownload
                                    ) }
                                ))
                                .toggleStyle(.switch)
                                .tint(.blue)
                            }
                            .font(.caption)

                            HStack(spacing: 16) {
                                Toggle("Download", isOn: Binding(
                                    get: { entry.canDownload ?? false },
                                    set: { entry = FileAccessDTO(
                                        userId: entry.userId,
                                        fileId: entry.fileId,
                                        canView: entry.canView,
                                        canEdit: entry.canEdit,
                                        canDownload: $0
                                    ) }
                                ))
                                .toggleStyle(.switch)
                                .tint(.purple)
                            }
                            .font(.caption)
                        }
                        .padding(.vertical, 4)
                    }
                }
            } header: {
                Text("Team Access")
            } footer: {
                if !accessEntries.isEmpty {
                    Button {
                        Task { await savePermissions() }
                    } label: {
                        HStack {
                            Spacer()
                            if isSaving {
                                ProgressView()
                                    .padding(.trailing, 4)
                            }
                            Text(isSaving ? "Saving..." : "Save Permissions")
                                .fontWeight(.semibold)
                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .background(Color.orange)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                    .disabled(isSaving)
                    .padding(.top, 8)
                }
            }

            // Status messages
            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
            }

            if saveSuccess {
                Section {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Permissions saved successfully")
                            .foregroundColor(.green)
                    }
                    .font(.caption)
                }
            }
        }
        .navigationTitle("Share")
        .task { await loadAccess() }
        .sheet(isPresented: $showShareSheet) {
            if let url = URL(string: shareLink?.url ?? "") {
                ShareSheet(items: [url])
            }
        }
    }

    private func loadAccess() async {
        isLoading = true
        errorMessage = nil
        do {
            accessEntries = try await repository.getFileAccess(fileId: fileId)
        } catch {
            errorMessage = "Failed to load access: \(error.localizedDescription)"
            #if DEBUG
            print("Load access error: \(error)")
            #endif
        }
        isLoading = false
    }

    private func generateLink() async {
        do {
            shareLink = try await repository.generateShareLink(fileId: fileId, expiry: "24h")
        } catch {
            errorMessage = "Failed to generate link: \(error.localizedDescription)"
        }
    }

    private func savePermissions() async {
        isSaving = true
        saveSuccess = false
        errorMessage = nil
        do {
            let entries: [[String: Any]] = accessEntries.map { entry in
                [
                    "user_id": entry.userId,
                    "can_view": entry.canView ?? true,
                    "can_edit": entry.canEdit ?? false,
                    "can_download": entry.canDownload ?? false
                ]
            }
            try await repository.updateFileAccess(fileId: fileId, entries: entries)
            saveSuccess = true
        } catch {
            errorMessage = "Failed to save: \(error.localizedDescription)"
        }
        isSaving = false
    }
}

// MARK: - Folder Share View

struct FolderShareView: View {
    let folderId: String
    @State private var accessEntries: [FolderAccessDTO] = []
    @State private var isLoading = false
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var saveSuccess = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    private let roles = ["viewer", "editor", "owner"]

    var body: some View {
        List {
            // Access Section
            Section {
                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                } else if accessEntries.isEmpty {
                    Text("No access entries")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(Array(accessEntries.enumerated()), id: \.element.userId) { index, entry in
                        HStack {
                            Image(systemName: "person.circle.fill")
                                .foregroundColor(.orange)
                                .font(.title3)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.userId)
                                    .font(.body)
                                    .lineLimit(1)
                                if entry.inherited == true {
                                    Text("Inherited")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                            }

                            Spacer()

                            Button(role: .destructive) {
                                accessEntries.remove(at: index)
                            } label: {
                                Image(systemName: "trash")
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.borderless)

                            Picker("Role", selection: Binding(
                                get: { entry.role },
                                set: { newRole in
                                    accessEntries[index] = FolderAccessDTO(
                                        userId: entry.userId,
                                        folderId: entry.folderId,
                                        role: newRole,
                                        inherited: entry.inherited
                                    )
                                }
                            )) {
                                ForEach(roles, id: \.self) { role in
                                    Text(role.capitalized).tag(role)
                                }
                            }
                            .pickerStyle(.menu)
                            .tint(roleColor(entry.role))
                        }
                        .padding(.vertical, 4)
                    }
                }
            } header: {
                Text("Team Access")
            } footer: {
                if !accessEntries.isEmpty {
                    Button {
                        Task { await savePermissions() }
                    } label: {
                        HStack {
                            Spacer()
                            if isSaving {
                                ProgressView()
                                    .padding(.trailing, 4)
                            }
                            Text(isSaving ? "Saving..." : "Save Permissions")
                                .fontWeight(.semibold)
                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .background(Color.orange)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                    .disabled(isSaving)
                    .padding(.top, 8)
                }
            }

            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
            }

            if saveSuccess {
                Section {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Permissions saved successfully")
                            .foregroundColor(.green)
                    }
                    .font(.caption)
                }
            }
        }
        .navigationTitle("Folder Access")
        .task { await loadAccess() }
    }

    private func roleColor(_ role: String) -> Color {
        switch role {
        case "owner": return .red
        case "editor": return .blue
        case "viewer": return .green
        default: return .secondary
        }
    }

    private func loadAccess() async {
        isLoading = true
        errorMessage = nil
        do {
            accessEntries = try await repository.getFolderAccess(folderId: folderId)
        } catch {
            errorMessage = "Failed to load access: \(error.localizedDescription)"
        }
        isLoading = false
    }

    private func savePermissions() async {
        isSaving = true
        saveSuccess = false
        errorMessage = nil
        do {
            let entries: [[String: Any]] = accessEntries.map { entry in
                [
                    "user_id": entry.userId,
                    "role": entry.role
                ]
            }
            try await repository.updateFolderAccess(folderId: folderId, entries: entries)
            saveSuccess = true
        } catch {
            errorMessage = "Failed to save: \(error.localizedDescription)"
        }
        isSaving = false
    }
}
