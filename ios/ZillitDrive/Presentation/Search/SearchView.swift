import SwiftUI

struct SearchView: View {
    @State private var searchText = ""
    @State private var results: [DriveItem] = []
    @State private var isLoading = false
    @State private var hasSearched = false
    @State private var showFilterSheet = false
    @State private var selectedFileTypes: Set<String> = []
    @State private var dateFrom: Date?
    @State private var dateTo: Date?
    private let repository: DriveRepository = DriveRepositoryImpl()

    private var hasActiveFilters: Bool {
        !selectedFileTypes.isEmpty || dateFrom != nil || dateTo != nil
    }

    var body: some View {
        VStack(spacing: 0) {
            // Active filter chips
            if hasActiveFilters {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(selectedFileTypes), id: \.self) { type in
                            FilterChip(label: type.capitalized) {
                                selectedFileTypes.remove(type)
                                Task { await search() }
                            }
                        }
                        if let from = dateFrom {
                            FilterChip(label: "From: \(from.formatted(date: .abbreviated, time: .omitted))") {
                                dateFrom = nil
                                Task { await search() }
                            }
                        }
                        if let to = dateTo {
                            FilterChip(label: "To: \(to.formatted(date: .abbreviated, time: .omitted))") {
                                dateTo = nil
                                Task { await search() }
                            }
                        }
                        Button("Clear all") {
                            selectedFileTypes = []
                            dateFrom = nil
                            dateTo = nil
                            Task { await search() }
                        }
                        .font(.caption)
                        .foregroundColor(.red)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 6)
                }
                .background(Color(.secondarySystemBackground))
            }

            // Results
            if isLoading {
                Spacer()
                ProgressView("Searching...")
                Spacer()
            } else if results.isEmpty && hasSearched {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("No results found")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
                Spacer()
            } else if results.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("Search files and folders")
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
                Spacer()
            } else {
                List(results) { item in
                    DriveItemRow(item: item, isFavorite: false)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Search")
        .searchable(text: $searchText, prompt: "Search files and folders")
        .onSubmit(of: .search) {
            Task { await search() }
        }
        .onChange(of: searchText) { newValue in
            if newValue.isEmpty {
                results = []
                hasSearched = false
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showFilterSheet = true } label: {
                    Image(systemName: hasActiveFilters
                          ? "line.3.horizontal.decrease.circle.fill"
                          : "line.3.horizontal.decrease.circle")
                    .foregroundColor(hasActiveFilters ? .orange : .secondary)
                }
            }
        }
        .sheet(isPresented: $showFilterSheet) {
            SearchFilterSheet(
                selectedFileTypes: $selectedFileTypes,
                dateFrom: $dateFrom,
                dateTo: $dateTo,
                onApply: {
                    showFilterSheet = false
                    Task { await search() }
                }
            )
            .presentationDetents([.medium])
        }
    }

    private func search() async {
        guard !searchText.trimmingCharacters(in: .whitespaces).isEmpty || hasActiveFilters else { return }
        isLoading = true
        hasSearched = true

        do {
            var options: [String: String] = [:]
            if !searchText.isEmpty { options["search"] = searchText }
            let files = try await repository.getFiles(options: options)
            let folders = try await repository.getFolders(options: options)
            var all = folders.map { DriveItem.folder($0) } + files.map { DriveItem.file($0) }

            // Client-side filtering
            if !selectedFileTypes.isEmpty {
                all = all.filter { item in
                    if case .file(let file) = item {
                        let type = FileUtils.fileType(for: file.fileExtension)
                        return selectedFileTypes.contains(type)
                    }
                    return selectedFileTypes.contains("folder")
                }
            }
            if let from = dateFrom {
                let ts = Int64(from.timeIntervalSince1970 * 1000)
                all = all.filter { item in
                    switch item {
                    case .file(let f): return f.createdOn >= ts
                    case .folder(let f): return f.createdOn >= ts
                    }
                }
            }
            if let to = dateTo {
                let ts = Int64(to.timeIntervalSince1970 * 1000)
                all = all.filter { item in
                    switch item {
                    case .file(let f): return f.createdOn <= ts
                    case .folder(let f): return f.createdOn <= ts
                    }
                }
            }

            results = all
        } catch {
            results = []
        }

        isLoading = false
    }
}

// MARK: - Filter Chip

struct FilterChip: View {
    let label: String
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.caption)
            Button { onRemove() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption2)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(Color.orange.opacity(0.15)))
        .foregroundColor(.orange)
    }
}

// MARK: - Filter Sheet

struct SearchFilterSheet: View {
    @Binding var selectedFileTypes: Set<String>
    @Binding var dateFrom: Date?
    @Binding var dateTo: Date?
    let onApply: () -> Void
    @Environment(\.dismiss) private var dismiss

    private let fileTypes = ["image", "video", "audio", "document", "pdf", "folder"]

    var body: some View {
        NavigationStack {
            List {
                Section("File Type") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 90))], spacing: 8) {
                        ForEach(fileTypes, id: \.self) { type in
                            Button {
                                if selectedFileTypes.contains(type) {
                                    selectedFileTypes.remove(type)
                                } else {
                                    selectedFileTypes.insert(type)
                                }
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: iconFor(type))
                                        .font(.caption)
                                    Text(type.capitalized)
                                        .font(.caption)
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule().fill(
                                        selectedFileTypes.contains(type)
                                            ? Color.orange : Color(.secondarySystemBackground)
                                    )
                                )
                                .foregroundColor(selectedFileTypes.contains(type) ? .white : .primary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)
                }

                Section("Date Range") {
                    DatePicker("From", selection: Binding(
                        get: { dateFrom ?? Date() },
                        set: { dateFrom = $0 }
                    ), displayedComponents: .date)

                    DatePicker("To", selection: Binding(
                        get: { dateTo ?? Date() },
                        set: { dateTo = $0 }
                    ), displayedComponents: .date)

                    if dateFrom != nil || dateTo != nil {
                        Button("Clear dates") {
                            dateFrom = nil
                            dateTo = nil
                        }
                        .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") { onApply() }
                        .fontWeight(.semibold)
                        .tint(.orange)
                }
            }
        }
    }

    private func iconFor(_ type: String) -> String {
        switch type {
        case "image": return "photo"
        case "video": return "film"
        case "audio": return "music.note"
        case "document": return "doc.text"
        case "pdf": return "doc.richtext"
        case "folder": return "folder"
        default: return "doc"
        }
    }
}
