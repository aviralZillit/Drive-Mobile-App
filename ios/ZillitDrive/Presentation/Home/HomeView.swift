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
    @State private var itemToDelete: DriveItem?
    @State private var moveItem_: DriveItem?
    @State private var isSelecting = false
    @State private var selectedIds: Set<String> = []

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

            // Storage used (root level only)
            if let storage = viewModel.storageUsage, viewModel.currentFolderId == nil {
                HStack(spacing: 6) {
                    Image(systemName: "externaldrive.fill")
                        .font(.caption2)
                        .foregroundColor(.orange)
                    Text("\(FileUtils.formatFileSize(storage.usedBytes)) used")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
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

                // Select / Done button
                if !viewModel.items.isEmpty {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isSelecting.toggle()
                            if !isSelecting { selectedIds = [] }
                        }
                    } label: {
                        Text(isSelecting ? "Done" : "Select")
                            .font(.subheadline.weight(.medium))
                            .foregroundColor(.orange)
                    }
                }

                Spacer()

                Text(isSelecting ? "\(selectedIds.count) selected" : "\(viewModel.items.count) items")
                    .font(.caption)
                    .foregroundColor(isSelecting ? .orange : .secondary)

                Spacer()

                // Tag filter
                if !viewModel.allTags.isEmpty {
                    Menu {
                        Button { viewModel.setTagFilter(nil) } label: {
                            HStack {
                                Text("All")
                                if viewModel.selectedTag == nil { Image(systemName: "checkmark") }
                            }
                        }
                        Divider()
                        ForEach(viewModel.allTags) { tag in
                            Button {
                                viewModel.setTagFilter(tag)
                            } label: {
                                HStack {
                                    Circle().fill(Color.orange).frame(width: 8, height: 8)
                                    Text(tag.name)
                                    if viewModel.selectedTag?.id == tag.id {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        Image(systemName: viewModel.selectedTag != nil ? "tag.fill" : "tag")
                            .foregroundColor(viewModel.selectedTag != nil ? .orange : .secondary)
                    }
                }

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

            // Bulk action bar (shown when items selected)
            if isSelecting && !selectedIds.isEmpty {
                HStack(spacing: 20) {
                    Text("\(selectedIds.count) selected")
                        .font(.subheadline.bold())
                        .foregroundColor(.primary)

                    Spacer()

                    Button {
                        let items = viewModel.items.filter { selectedIds.contains($0.id) }
                        Task {
                            for item in items { await viewModel.deleteItem(item) }
                            selectedIds = []
                            isSelecting = false
                        }
                    } label: {
                        VStack(spacing: 2) {
                            Image(systemName: "trash")
                            Text("Delete").font(.caption2)
                        }
                    }
                    .foregroundColor(.red)

                    Button {
                        // Move all selected — use first item for the sheet
                        if let first = viewModel.items.first(where: { selectedIds.contains($0.id) }) {
                            moveItem_ = first
                        }
                    } label: {
                        VStack(spacing: 2) {
                            Image(systemName: "folder.badge.arrow.forward")
                            Text("Move").font(.caption2)
                        }
                    }
                    .foregroundColor(.orange)

                    Button {
                        selectedIds = []
                        isSelecting = false
                    } label: {
                        VStack(spacing: 2) {
                            Image(systemName: "xmark.circle")
                            Text("Cancel").font(.caption2)
                        }
                    }
                    .foregroundColor(.secondary)
                }
                .padding(.horizontal)
                .padding(.vertical, 10)
                .background(Color(.secondarySystemBackground))
            }
        }
        .navigationTitle(viewModel.driveSection.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if !isSelecting {
                    Menu {
                        if viewModel.driveSection == .myDrive {
                            Button {
                                viewModel.showCreateFolderSheet = true
                            } label: {
                                Label("New Folder", systemImage: "folder.badge.plus")
                            }
                        }
                        if !viewModel.items.isEmpty {
                            Button {
                                Task { await viewModel.downloadAllAsZip() }
                            } label: {
                                Label("Download All", systemImage: "arrow.down.circle")
                            }
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
            switch item {
            case .file(let file):
                FileShareView(fileId: file.id, fileName: file.fileName)
            case .folder(let folder):
                FolderShareView(folderId: folder.id, folderName: folder.folderName)
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
        .confirmationDialog(
            "Delete \(itemToDelete?.name ?? "")?",
            isPresented: Binding(get: { itemToDelete != nil }, set: { if !$0 { itemToDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let item = itemToDelete {
                    Task { await viewModel.deleteItem(item) }
                }
                itemToDelete = nil
            }
            Button("Cancel", role: .cancel) { itemToDelete = nil }
        } message: {
            Text("This will move the item to trash.")
        }
        .sheet(item: $moveItem_) { item in
            NavigationStack {
                FolderPickerSheet(
                    excludeFolderId: item.id,
                    currentFolderId: viewModel.currentFolderId,
                    onSelect: { targetFolderId in
                        Task { await viewModel.moveItemToFolder(item, targetFolderId: targetFolderId) }
                        moveItem_ = nil
                    }
                )
            }
        }
        .alert("Error", isPresented: Binding(
            get: { viewModel.errorMessage != nil && !viewModel.items.isEmpty },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK") { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    // MARK: - List Content

    private var listContent: some View {
        List {
            ForEach(viewModel.items) { item in
                HStack(spacing: 8) {
                    if isSelecting {
                        Image(systemName: selectedIds.contains(item.id) ? "checkmark.circle.fill" : "circle")
                            .foregroundColor(selectedIds.contains(item.id) ? .orange : .secondary)
                            .font(.title3)
                            .onTapGesture { toggleSelection(item.id) }
                    }

                    DriveItemRow(
                        item: item,
                        isFavorite: item.isFavorite,
                        isDropTarget: dropTargetId == item.id,
                        currentUserId: sessionManager.currentSession?.userId,
                        badgeCount: viewModel.folderBadges[item.id] ?? 0,
                        hasUnreadBadge: viewModel.fileBadges.contains(item.id),
                        onTap: {
                            if isSelecting { toggleSelection(item.id) }
                            else { handleItemTap(item) }
                        },
                        onFavorite: { Task { await viewModel.toggleFavorite(item: item) } },
                        onDelete: { itemToDelete = item },
                        onShare: { shareItem(item) },
                        onMove: { moveItem_ = item },
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
                    ZStack(alignment: .topLeading) {
                        DriveItemGridCell(
                            item: item,
                            isFavorite: item.isFavorite,
                            isDropTarget: dropTargetId == item.id,
                            currentUserId: sessionManager.currentSession?.userId,
                            badgeCount: viewModel.folderBadges[item.id] ?? 0,
                            hasUnreadBadge: viewModel.fileBadges.contains(item.id),
                            onTap: {
                                if isSelecting { toggleSelection(item.id) }
                                else { handleItemTap(item) }
                            }
                        )
                        .overlay(
                            isSelecting && selectedIds.contains(item.id)
                                ? RoundedRectangle(cornerRadius: 12).stroke(Color.orange, lineWidth: 2)
                                : nil
                        )

                        if isSelecting {
                            Image(systemName: selectedIds.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                .foregroundColor(selectedIds.contains(item.id) ? .orange : .white.opacity(0.8))
                                .font(.title3)
                                .shadow(radius: 2)
                                .padding(6)
                                .onTapGesture { toggleSelection(item.id) }
                        }
                    }
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
                        Button { moveItem_ = item } label: {
                            Label("Move", systemImage: "folder.badge.arrow.forward")
                        }
                        Divider()
                        Button(role: .destructive) { itemToDelete = item } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .padding()
        }
    }

    // MARK: - Helpers

    private func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

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
