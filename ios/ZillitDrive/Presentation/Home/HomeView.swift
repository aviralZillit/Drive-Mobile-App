import SwiftUI
import UniformTypeIdentifiers

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @EnvironmentObject var sessionManager: SessionManager
    @State private var showSortMenu = false
    @State private var selectedItem: DriveItem?
    @State private var showContextMenu = false
    @State private var showShareSheet = false
    @State private var shareURL: URL?
    @State private var showShareView = false
    @State private var shareItem_: DriveItem?
    @State private var draggedItem: DriveItem?
    @State private var dropTargetId: String?
    @State private var renameItem_: DriveItem?
    @State private var renameText = ""

    var body: some View {
        VStack(spacing: 0) {
            // Section tabs — My Drive / Shared with me
            Picker("Section", selection: $viewModel.driveSection) {
                Text("My Drive").tag(DriveSection.myDrive)
                Label("Shared", systemImage: "person.2").tag(DriveSection.sharedWithMe)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .onChange(of: viewModel.driveSection) { newVal in
                viewModel.switchSection(newVal)
            }

            // Breadcrumbs
            BreadcrumbBar(
                breadcrumbs: viewModel.breadcrumbs,
                onTap: { viewModel.navigateToBreadcrumb($0) }
            )

            // Toolbar
            HStack {
                Button {
                    viewModel.isGridView.toggle()
                } label: {
                    Image(systemName: viewModel.isGridView ? "list.bullet" : "square.grid.2x2")
                }

                Spacer()

                Text("\(viewModel.items.count) items")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Spacer()

                Menu {
                    ForEach(SortOption.allCases, id: \.self) { option in
                        Button {
                            viewModel.setSortOption(option)
                        } label: {
                            HStack {
                                Text(option.displayName)
                                if viewModel.sortBy == option {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)

            // Content
            if viewModel.isLoading && viewModel.items.isEmpty {
                Spacer()
                ProgressView("Loading...")
                Spacer()
            } else if let error = viewModel.errorMessage, viewModel.items.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundColor(.orange)
                    Text(error)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        Task { await viewModel.loadContents() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
                Spacer()
            } else if viewModel.items.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: viewModel.driveSection == .sharedWithMe ? "person.2" : "folder.badge.questionmark")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text(viewModel.driveSection == .sharedWithMe
                         ? "No files shared with you yet"
                         : "This folder is empty")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    if viewModel.driveSection == .sharedWithMe {
                        Text("Files and folders shared with you will appear here")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                }
                Spacer()
            } else {
                if viewModel.isGridView {
                    gridContent
                } else {
                    listContent
                }
            }
        }
        .navigationTitle(viewModel.driveSection.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if viewModel.driveSection == .myDrive {
                    Menu {
                        Button {
                            viewModel.showCreateFolderSheet = true
                        } label: {
                            Label("New Folder", systemImage: "folder.badge.plus")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }

            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    sessionManager.clearSession()
                } label: {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                }
            }
        }
        .refreshable {
            await viewModel.forceLoadContents()
        }
        .task {
            await viewModel.loadContents()
        }
        .sheet(isPresented: $viewModel.showCreateFolderSheet) {
            CreateFolderSheet(
                folderName: $viewModel.newFolderName,
                onCreate: {
                    Task { await viewModel.createFolder() }
                }
            )
            .presentationDetents([.height(200)])
        }
        .sheet(isPresented: $showShareSheet) {
            if let url = shareURL {
                ShareSheet(items: [url])
            }
        }
        .sheet(item: $selectedItem) { item in
            if case .file(let file) = item {
                NavigationStack {
                    FileDetailView(file: file)
                }
            }
        }
        .sheet(item: $shareItem_) { item in
            NavigationStack {
                switch item {
                case .file(let file):
                    FileShareView(fileId: file.id)
                case .folder(let folder):
                    FolderShareView(folderId: folder.id)
                }
            }
        }
        .alert("Rename", isPresented: Binding(
            get: { renameItem_ != nil },
            set: { if !$0 { renameItem_ = nil } }
        )) {
            TextField("Name", text: $renameText)
            Button("Cancel", role: .cancel) { renameItem_ = nil }
            Button("Rename") {
                if let item = renameItem_ {
                    Task { await viewModel.renameItem(item, newName: renameText) }
                    renameItem_ = nil
                }
            }
        } message: {
            Text("Enter a new name")
        }
    }

    // MARK: - List Content

    private var listContent: some View {
        List {
            ForEach(viewModel.items) { item in
                DriveItemRow(
                    item: item,
                    isFavorite: item.isFavorite,
                    isDropTarget: dropTargetId == item.id,
                    currentUserId: sessionManager.currentSession?.userId,
                    badgeCount: viewModel.folderBadges[item.id] ?? 0,
                    hasUnreadBadge: viewModel.fileBadges.contains(item.id),
                    onTap: { handleItemTap(item) },
                    onFavorite: { Task { await viewModel.toggleFavorite(item: item) } },
                    onDelete: { Task { await viewModel.deleteItem(item) } },
                    onShare: { shareItem(item) },
                    onRename: {
                        renameText = item.name
                        renameItem_ = item
                    }
                )
                .onDrag {
                    draggedItem = item
                    return NSItemProvider(object: item.id as NSString)
                }
                .onDrop(of: [UTType.text], isTargeted: dropBinding(for: item)) { providers in
                    handleDrop(onto: item, providers: providers)
                }
            }
        }
        .listStyle(.plain)
    }

    // MARK: - Grid Content

    private var gridContent: some View {
        ScrollView {
            LazyVGrid(columns: [
                GridItem(.adaptive(minimum: 150), spacing: 12)
            ], spacing: 12) {
                ForEach(viewModel.items) { item in
                    DriveItemGridCell(
                        item: item,
                        isFavorite: item.isFavorite,
                        isDropTarget: dropTargetId == item.id,
                        currentUserId: sessionManager.currentSession?.userId,
                        badgeCount: viewModel.folderBadges[item.id] ?? 0,
                        hasUnreadBadge: viewModel.fileBadges.contains(item.id),
                        onTap: { handleItemTap(item) }
                    )
                    .onDrag {
                        draggedItem = item
                        return NSItemProvider(object: item.id as NSString)
                    }
                    .onDrop(of: [UTType.text], isTargeted: dropBinding(for: item)) { providers in
                        handleDrop(onto: item, providers: providers)
                    }
                    .contextMenu {
                        Button { Task { await viewModel.toggleFavorite(item: item) } } label: {
                            Label(
                                item.isFavorite ? "Unfavorite" : "Favorite",
                                systemImage: item.isFavorite ? "star.slash" : "star"
                            )
                        }
                        Button { shareItem(item) } label: {
                            Label("Share", systemImage: "person.badge.plus")
                        }
                        Button {
                            renameText = item.name
                            renameItem_ = item
                        } label: {
                            Label("Rename", systemImage: "pencil")
                        }
                        Divider()
                        Button(role: .destructive) { Task { await viewModel.deleteItem(item) } } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .padding()
        }
    }

    // MARK: - Helpers


    private func handleItemTap(_ item: DriveItem) {
        switch item {
        case .folder(let folder):
            viewModel.navigateToFolder(folder)
        case .file:
            selectedItem = item
        }
    }

    private func shareItem(_ item: DriveItem) {
        shareItem_ = item
    }

    // MARK: - Drag & Drop

    private func dropBinding(for item: DriveItem) -> Binding<Bool> {
        Binding(
            get: { dropTargetId == item.id },
            set: { isTargeted in
                if isTargeted, case .folder = item {
                    dropTargetId = item.id
                } else if !isTargeted, dropTargetId == item.id {
                    dropTargetId = nil
                }
            }
        )
    }

    private func handleDrop(onto target: DriveItem, providers: [NSItemProvider]) -> Bool {
        guard case .folder(let folder) = target,
              let dragged = draggedItem,
              dragged.id != folder.id else {
            return false
        }
        Task {
            await viewModel.moveItem(dragged, toFolder: folder)
            draggedItem = nil
            dropTargetId = nil
        }
        return true
    }
}

// MARK: - Create Folder Sheet

struct CreateFolderSheet: View {
    @Binding var folderName: String
    let onCreate: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                TextField("Folder name", text: $folderName)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                Button("Create") {
                    onCreate()
                }
                .buttonStyle(.borderedProminent)
                .tint(.orange)
                .disabled(folderName.trimmingCharacters(in: .whitespaces).isEmpty)

                Spacer()
            }
            .padding(.top)
            .navigationTitle("New Folder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
