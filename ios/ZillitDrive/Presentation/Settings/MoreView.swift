import SwiftUI

struct MoreView: View {
    @EnvironmentObject var sessionManager: SessionManager
    @State private var storageUsage: StorageUsage?
    @State private var teamMembers: [FolderAccessDTO] = []
    @State private var isLoading = false
    @State private var userProfile: ProjectUser?
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        List {
            // User Profile Section
            Section {
                if let profile = userProfile {
                    HStack(spacing: 16) {
                        UserAvatarView(user: profile, size: 56)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(profile.fullName)
                                .font(.title3.bold())
                            Text(profile.email)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            if let designation = profile.designationName, !designation.isEmpty {
                                Text(designation)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(.vertical, 8)
                } else if let session = sessionManager.currentSession {
                    HStack(spacing: 16) {
                        ZStack {
                            Circle().fill(Color.orange).frame(width: 56, height: 56)
                            Text(String(session.userId.prefix(1)).uppercased())
                                .font(.title2.bold()).foregroundColor(.white)
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Loading...").font(.title3.bold())
                            Text(session.userId).font(.caption).foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }

            // Storage Section
            Section("Storage") {
                if let storage = storageUsage {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Used")
                            Spacer()
                            Text("\(FileUtils.formatFileSize(storage.usedBytes)) / \(FileUtils.formatFileSize(storage.totalBytes))")
                                .foregroundColor(.secondary)
                        }

                        ProgressView(value: storageProgress)
                            .tint(storageProgress > 0.9 ? .red : .orange)

                        Text("\(storage.fileCount) files")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                } else if isLoading {
                    ProgressView()
                } else {
                    Text("Unable to load storage info")
                        .foregroundColor(.secondary)
                }
            }

            // Features Section
            Section("Features") {
                NavigationLink {
                    TrashView()
                } label: {
                    Label("Trash", systemImage: "trash")
                }

                NavigationLink {
                    ActivityView()
                } label: {
                    Label("Activity", systemImage: "clock.arrow.circlepath")
                }
            }

            // Team Members Section
            if !teamMembers.isEmpty {
                Section("Team Members") {
                    ForEach(teamMembers, id: \.userId) { member in
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(Color.orange)
                                    .frame(width: 36, height: 36)
                                Text(String(member.userId.prefix(1)).uppercased())
                                    .font(.subheadline.bold())
                                    .foregroundColor(.white)
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(member.userId)
                                    .font(.body)
                                Text(member.role.capitalized)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }

                            Spacer()

                            if member.role == "owner" {
                                Text("Owner")
                                    .font(.caption)
                                    .foregroundColor(.orange)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(Color.orange.opacity(0.1))
                                    .cornerRadius(4)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            // Account Section
            Section("Account") {
                if let session = sessionManager.currentSession {
                    if !session.userName.isEmpty {
                        HStack {
                            Text("Name")
                            Spacer()
                            Text(session.userName)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }
                    if !session.userEmail.isEmpty {
                        HStack {
                            Text("Email")
                            Spacer()
                            Text(session.userEmail)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                    }
                    HStack {
                        Text("User ID")
                        Spacer()
                        Text(session.userId)
                            .foregroundColor(.secondary)
                            .font(.caption)
                            .lineLimit(1)
                    }
                    HStack {
                        Text("Project ID")
                        Spacer()
                        Text(session.projectId)
                            .foregroundColor(.secondary)
                            .font(.caption)
                            .lineLimit(1)
                    }
                    HStack {
                        Text("Device ID")
                        Spacer()
                        Text(session.deviceId)
                            .foregroundColor(.secondary)
                            .font(.caption)
                            .lineLimit(1)
                    }
                    HStack {
                        Text("Environment")
                        Spacer()
                        Text(session.environment.capitalized)
                            .foregroundColor(.secondary)
                    }
                }

                Button(role: .destructive) {
                    sessionManager.clearSession()
                } label: {
                    Label("Logout", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }

            // App Info
            Section("About") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text("1.0.0")
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle("More")
        .task {
            await loadUserProfile()
            await loadStorage()
        }
    }

    private var storageProgress: Double {
        guard let s = storageUsage, s.totalBytes > 0 else { return 0 }
        return Double(s.usedBytes) / Double(s.totalBytes)
    }

    private func loadStorage() async {
        isLoading = true
        do {
            storageUsage = try await repository.getStorageUsage()
        } catch {
            // Silently handle - UI shows fallback
        }
        isLoading = false
    }

    private func loadUserProfile() async {
        guard let userId = sessionManager.currentSession?.userId else { return }
        do {
            let allUsers = try await repository.getProjectUsers()
            userProfile = allUsers.first(where: { $0.id == userId })
        } catch {
            print("Failed to load user profile: \(error)")
        }
    }
}

// MARK: - Activity View

struct ActivityView: View {
    @State private var activities: [DriveActivity] = []
    @State private var isLoading = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        Group {
            if isLoading && activities.isEmpty {
                ProgressView("Loading activity...")
            } else if activities.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("No recent activity")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
            } else {
                List(activities) { activity in
                    HStack(spacing: 12) {
                        Image(systemName: activityIcon(activity.action))
                            .foregroundColor(.orange)
                            .frame(width: 28)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(activity.action.replacingOccurrences(of: "_", with: " ").capitalized)
                                .font(.body)
                            if let details = activity.details {
                                Text(details)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(2)
                            }
                            Text(formatDate(activity.createdOn))
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Activity")
        .refreshable { await loadActivity() }
        .task { await loadActivity() }
    }

    private func loadActivity() async {
        isLoading = true
        do {
            activities = try await repository.getActivity(options: [:])
        } catch {
            // Silently handle
        }
        isLoading = false
    }

    private func activityIcon(_ action: String) -> String {
        if action.contains("upload") { return "arrow.up.circle" }
        if action.contains("delete") { return "trash" }
        if action.contains("create") { return "plus.circle" }
        if action.contains("move") { return "arrow.right.circle" }
        if action.contains("rename") { return "pencil.circle" }
        if action.contains("share") { return "square.and.arrow.up.circle" }
        return "clock.arrow.circlepath"
    }

    private func formatDate(_ timestamp: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(timestamp) / 1000)
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
