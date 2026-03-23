import SwiftUI

struct SearchView: View {
    @State private var searchText = ""
    @State private var results: [DriveItem] = []
    @State private var isLoading = false
    @State private var hasSearched = false
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        VStack {
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
    }

    private func search() async {
        guard !searchText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        isLoading = true
        hasSearched = true

        do {
            let options = ["search": searchText]
            let files = try await repository.getFiles(options: options)
            let folders = try await repository.getFolders(options: options)
            results = folders.map { .folder($0) } + files.map { .file($0) }
        } catch {
            results = []
        }

        isLoading = false
    }
}
