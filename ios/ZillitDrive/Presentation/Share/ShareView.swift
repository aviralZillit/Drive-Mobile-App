import SwiftUI
import Kingfisher

// MARK: - User Avatar View

private struct UserAvatarView: View {
    let user: ProjectUser?
    let size: CGFloat

    private var initials: String {
        user?.initials ?? "?"
    }

    private var avatarColor: Color {
        guard let user = user else { return .gray }
        let hash = abs(user.id.hashValue)
        let colors: [Color] = [.orange, .blue, .green, .purple, .pink, .red, .teal, .indigo]
        return colors[hash % colors.count]
    }

    var body: some View {
        if let user = user, let urlStr = user.profileImage, !urlStr.isEmpty,
           let url = URL(string: urlStr) {
            KFImage(url)
                .resizable()
                .scaledToFill()
                .frame(width: size, height: size)
                .clipShape(Circle())
        } else {
            ZStack {
                Circle()
                    .fill(avatarColor.opacity(0.2))
                Text(initials)
                    .font(.system(size: size * 0.4, weight: .semibold))
                    .foregroundColor(avatarColor)
            }
            .frame(width: size, height: size)
        }
    }
}

// MARK: - File Share View

struct FileShareView: View {
    let fileId: String
    let fileName: String
    @Environment(\.dismiss) private var dismiss
    @State private var accessEntries: [FileAccessDTO] = []
    @State private var projectUsers: [ProjectUser] = []
    @State private var searchText = ""
    @State private var isLoading = false
    @State private var isLoadingUsers = false
    @State private var isSaving = false
    @State private var shareLink: ShareLink?
    @State private var showShareSheet = false
    @State private var errorMessage: String?
    @State private var saveSuccess = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    private var currentUserId: String? {
        SessionManager.shared.currentSession?.userId
    }

    /// Users already in the access list
    private var accessUserIds: Set<String> {
        Set(accessEntries.map(\.userId))
    }

    /// Available project users (excludes already added + self). Filtered by search if typed.
    private var searchResults: [ProjectUser] {
        let available = projectUsers.filter { user in
            !accessUserIds.contains(user.id)
            && user.id != currentUserId
        }
        guard !searchText.isEmpty else { return available }
        let query = searchText.lowercased()
        return available.filter {
            $0.fullName.lowercased().contains(query)
            || $0.email.lowercased().contains(query)
        }
    }

    /// Resolve a userId to a ProjectUser
    private func userFor(_ userId: String) -> ProjectUser? {
        projectUsers.first(where: { $0.id == userId })
    }

    var body: some View {
        NavigationStack {
            List {
                // Search Section
                Section {
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.secondary)
                        TextField("Search people...", text: $searchText)
                            .textFieldStyle(.plain)
                            .autocorrectionDisabled()
                        if !searchText.isEmpty {
                            Button {
                                searchText = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                            .buttonStyle(.borderless)
                        }
                    }

                    if isLoadingUsers {
                        HStack {
                            Spacer()
                            ProgressView("Loading team members...")
                                .font(.caption)
                            Spacer()
                        }
                    }

                    // Search Results
                    if !searchText.isEmpty {
                        if searchResults.isEmpty && !isLoadingUsers {
                            Text("No matching users found")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        } else {
                            ForEach(searchResults) { user in
                                HStack(spacing: 12) {
                                    UserAvatarView(user: user, size: 36)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(user.fullName)
                                            .font(.body)
                                        Text(user.email)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    Spacer()

                                    Button {
                                        addUser(user)
                                    } label: {
                                        Image(systemName: "plus.circle.fill")
                                            .font(.title3)
                                            .foregroundColor(.orange)
                                    }
                                    .buttonStyle(.borderless)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    }
                }

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

                // People with access
                Section {
                    if isLoading {
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                    } else if accessEntries.isEmpty {
                        Text("No one has access yet")
                            .foregroundColor(.secondary)
                            .font(.subheadline)
                    } else {
                        ForEach($accessEntries, id: \.userId) { $entry in
                            let isOwner = entry.userId == currentUserId
                            let user = userFor(entry.userId)

                            VStack(alignment: .leading, spacing: 10) {
                                HStack(spacing: 12) {
                                    UserAvatarView(user: user, size: 40)

                                    VStack(alignment: .leading, spacing: 2) {
                                        HStack(spacing: 4) {
                                            Text(user?.fullName ?? entry.userId)
                                                .font(.body)
                                                .lineLimit(1)
                                            if isOwner {
                                                Text("(You)")
                                                    .font(.caption)
                                                    .foregroundColor(.secondary)
                                            }
                                        }
                                        if let email = user?.email, !email.isEmpty {
                                            Text(email)
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                                .lineLimit(1)
                                        }
                                    }

                                    Spacer()

                                    if !isOwner {
                                        Button(role: .destructive) {
                                            accessEntries.removeAll { $0.userId == entry.userId }
                                        } label: {
                                            Image(systemName: "trash")
                                                .font(.caption)
                                                .foregroundColor(.red)
                                        }
                                        .buttonStyle(.borderless)
                                    }
                                }

                                if !isOwner {
                                    HStack(spacing: 16) {
                                        permissionToggle("View", isOn: Binding(
                                            get: { entry.canView ?? false },
                                            set: { newVal in
                                                entry = FileAccessDTO(
                                                    userId: entry.userId,
                                                    fileId: entry.fileId,
                                                    canView: newVal,
                                                    canEdit: entry.canEdit,
                                                    canDownload: entry.canDownload
                                                )
                                            }
                                        ), color: .green)

                                        permissionToggle("Edit", isOn: Binding(
                                            get: { entry.canEdit ?? false },
                                            set: { newVal in
                                                entry = FileAccessDTO(
                                                    userId: entry.userId,
                                                    fileId: entry.fileId,
                                                    canView: entry.canView,
                                                    canEdit: newVal,
                                                    canDownload: entry.canDownload
                                                )
                                            }
                                        ), color: .blue)

                                        permissionToggle("Download", isOn: Binding(
                                            get: { entry.canDownload ?? false },
                                            set: { newVal in
                                                entry = FileAccessDTO(
                                                    userId: entry.userId,
                                                    fileId: entry.fileId,
                                                    canView: entry.canView,
                                                    canEdit: entry.canEdit,
                                                    canDownload: newVal
                                                )
                                            }
                                        ), color: .purple)
                                    }
                                } else {
                                    Text("Owner - Full Access")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                } header: {
                    Text("People with access")
                } footer: {
                    if !accessEntries.isEmpty {
                        Button {
                            Task { await savePermissions() }
                        } label: {
                            HStack {
                                Spacer()
                                if isSaving {
                                    ProgressView()
                                        .tint(.white)
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
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }

                if saveSuccess {
                    Section {
                        Label("Permissions saved successfully", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("Share")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text("Share")
                            .font(.headline)
                        Text(fileName)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .task {
                await loadData()
            }
            .sheet(isPresented: $showShareSheet) {
                if let url = URL(string: shareLink?.url ?? "") {
                    ShareSheet(items: [url])
                }
            }
        }
    }

    // MARK: - Subviews

    private func permissionToggle(_ label: String, isOn: Binding<Bool>, color: Color) -> some View {
        Toggle(isOn: isOn) {
            Text(label)
                .font(.caption)
        }
        .toggleStyle(.switch)
        .controlSize(.mini)
        .tint(color)
    }

    // MARK: - Actions

    private func loadData() async {
        isLoading = true
        isLoadingUsers = true
        errorMessage = nil

        async let accessTask: () = loadAccess()
        async let usersTask: () = loadProjectUsers()

        await accessTask
        await usersTask
    }

    private func loadAccess() async {
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

    private func loadProjectUsers() async {
        do {
            projectUsers = try await repository.getProjectUsers()
        } catch {
            #if DEBUG
            print("Load project users error: \(error)")
            #endif
        }
        isLoadingUsers = false
    }

    private func addUser(_ user: ProjectUser) {
        let newEntry = FileAccessDTO(
            userId: user.id,
            fileId: fileId,
            canView: true,
            canEdit: false,
            canDownload: false
        )
        accessEntries.append(newEntry)
        searchText = ""
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
            // Auto-dismiss success message after 2 seconds
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                saveSuccess = false
            }
        } catch {
            errorMessage = "Failed to save: \(error.localizedDescription)"
        }
        isSaving = false
    }
}

// MARK: - Folder Share View

struct FolderShareView: View {
    let folderId: String
    let folderName: String
    @Environment(\.dismiss) private var dismiss
    @State private var accessEntries: [FolderAccessDTO] = []
    @State private var projectUsers: [ProjectUser] = []
    @State private var searchText = ""
    @State private var isLoading = false
    @State private var isLoadingUsers = false
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var saveSuccess = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    private let roles = ["viewer", "editor", "owner"]

    private var currentUserId: String? {
        SessionManager.shared.currentSession?.userId
    }

    private var accessUserIds: Set<String> {
        Set(accessEntries.map(\.userId))
    }

    private var searchResults: [ProjectUser] {
        guard !searchText.isEmpty else { return [] }
        let query = searchText.lowercased()
        return projectUsers.filter { user in
            !accessUserIds.contains(user.id)
            && user.id != currentUserId
            && (user.fullName.lowercased().contains(query)
                || user.email.lowercased().contains(query))
        }
    }

    private func userFor(_ userId: String) -> ProjectUser? {
        projectUsers.first(where: { $0.id == userId })
    }

    var body: some View {
        NavigationStack {
            List {
                // Search Section
                Section {
                    HStack {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.secondary)
                        TextField("Search people...", text: $searchText)
                            .textFieldStyle(.plain)
                            .autocorrectionDisabled()
                        if !searchText.isEmpty {
                            Button {
                                searchText = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                            .buttonStyle(.borderless)
                        }
                    }

                    if isLoadingUsers {
                        HStack {
                            Spacer()
                            ProgressView("Loading team members...")
                                .font(.caption)
                            Spacer()
                        }
                    }

                    // Search Results
                    if !searchText.isEmpty {
                        if searchResults.isEmpty && !isLoadingUsers {
                            Text("No matching users found")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        } else {
                            ForEach(searchResults) { user in
                                HStack(spacing: 12) {
                                    UserAvatarView(user: user, size: 36)

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(user.fullName)
                                            .font(.body)
                                        Text(user.email)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    Spacer()

                                    Button {
                                        addUser(user)
                                    } label: {
                                        Image(systemName: "plus.circle.fill")
                                            .font(.title3)
                                            .foregroundColor(.orange)
                                    }
                                    .buttonStyle(.borderless)
                                }
                                .padding(.vertical, 2)
                            }
                        }
                    }
                }

                // People with access
                Section {
                    if isLoading {
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                    } else if accessEntries.isEmpty {
                        Text("No one has access yet")
                            .foregroundColor(.secondary)
                            .font(.subheadline)
                    } else {
                        ForEach(Array(accessEntries.enumerated()), id: \.element.userId) { index, entry in
                            let isOwner = entry.userId == currentUserId
                            let user = userFor(entry.userId)

                            HStack(spacing: 12) {
                                UserAvatarView(user: user, size: 40)

                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 4) {
                                        Text(user?.fullName ?? entry.userId)
                                            .font(.body)
                                            .lineLimit(1)
                                        if isOwner {
                                            Text("(You)")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                    if let email = user?.email, !email.isEmpty {
                                        Text(email)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                    if entry.inherited == true {
                                        Text("Inherited")
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                            .italic()
                                    }
                                }

                                Spacer()

                                if !isOwner {
                                    Button(role: .destructive) {
                                        accessEntries.remove(at: index)
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.caption)
                                            .foregroundColor(.red)
                                    }
                                    .buttonStyle(.borderless)
                                }

                                if isOwner {
                                    Text("Owner")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color(.systemGray5))
                                        .cornerRadius(6)
                                } else {
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
                            }
                            .padding(.vertical, 4)
                        }
                    }
                } header: {
                    Text("People with access")
                } footer: {
                    if !accessEntries.isEmpty {
                        Button {
                            Task { await savePermissions() }
                        } label: {
                            HStack {
                                Spacer()
                                if isSaving {
                                    ProgressView()
                                        .tint(.white)
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
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }

                if saveSuccess {
                    Section {
                        Label("Permissions saved successfully", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("Folder Access")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text("Share")
                            .font(.headline)
                        Text(folderName)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .task {
                await loadData()
            }
        }
    }

    // MARK: - Helpers

    private func roleColor(_ role: String) -> Color {
        switch role {
        case "owner": return .red
        case "editor": return .blue
        case "viewer": return .green
        default: return .secondary
        }
    }

    // MARK: - Actions

    private func loadData() async {
        isLoading = true
        isLoadingUsers = true
        errorMessage = nil

        async let accessTask: () = loadAccess()
        async let usersTask: () = loadProjectUsers()

        await accessTask
        await usersTask
    }

    private func loadAccess() async {
        do {
            accessEntries = try await repository.getFolderAccess(folderId: folderId)
        } catch {
            errorMessage = "Failed to load access: \(error.localizedDescription)"
        }
        isLoading = false
    }

    private func loadProjectUsers() async {
        do {
            projectUsers = try await repository.getProjectUsers()
        } catch {
            #if DEBUG
            print("Load project users error: \(error)")
            #endif
        }
        isLoadingUsers = false
    }

    private func addUser(_ user: ProjectUser) {
        let newEntry = FolderAccessDTO(
            userId: user.id,
            folderId: folderId,
            role: "viewer",
            inherited: false
        )
        accessEntries.append(newEntry)
        searchText = ""
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
            Task {
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                saveSuccess = false
            }
        } catch {
            errorMessage = "Failed to save: \(error.localizedDescription)"
        }
        isSaving = false
    }
}
