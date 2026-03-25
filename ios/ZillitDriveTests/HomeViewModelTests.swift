import XCTest
@testable import ZillitDrive

@MainActor
final class HomeViewModelTests: XCTestCase {

    private var sut: HomeViewModel!
    private var mockRepo: MockDriveRepository!

    override func setUp() {
        super.setUp()
        mockRepo = MockDriveRepository()
        sut = HomeViewModel(repository: mockRepo)
    }

    override func tearDown() {
        sut = nil
        mockRepo = nil
        super.tearDown()
    }

    // MARK: - Helpers

    private func makeSampleFile(
        id: String = "f1",
        name: String = "test.pdf",
        ext: String = "pdf",
        size: Int64 = 1024,
        createdOn: Int64 = 1700000000
    ) -> DriveFile {
        DriveFile(
            id: id, fileName: name, fileExtension: ext,
            fileSizeBytes: size, mimeType: "application/pdf",
            folderId: nil, filePath: nil, description: nil,
            createdBy: "user1", createdOn: createdOn, updatedOn: 0,
            thumbnailUrl: nil
        )
    }

    private func makeSampleFolder(
        id: String = "d1",
        name: String = "Documents",
        createdOn: Int64 = 1700000000
    ) -> DriveFolder {
        DriveFolder(
            id: id, folderName: name, parentFolderId: nil,
            description: nil, createdBy: "user1", createdOn: createdOn, updatedOn: 0,
            fileCount: 5, folderCount: 2
        )
    }

    // MARK: - loadContents Tests

    func testLoadContents_success_updatesItems() async {
        let file = makeSampleFile()
        let folder = makeSampleFolder()
        mockRepo.folderContentsResult = DriveContents(
            folders: [folder], files: [file], totalFolders: 1, totalFiles: 1
        )
        mockRepo.favoriteIdsResult = FavoriteIdsDTO(fileIds: ["f1"], folderIds: [])

        await sut.loadContents()

        XCTAssertEqual(sut.items.count, 2)
        XCTAssertFalse(sut.isLoading)
        XCTAssertNil(sut.errorMessage)
        XCTAssertEqual(mockRepo.getFolderContentsCalled, 1)

        // Check that favorite was applied to file
        XCTAssertTrue(sut.favoriteFileIds.contains("f1"))
    }

    func testLoadContents_failure_setsErrorMessage() async {
        mockRepo.folderContentsError = MockError.forced

        await sut.loadContents()

        XCTAssertNotNil(sut.errorMessage)
        XCTAssertTrue(sut.items.isEmpty)
        XCTAssertFalse(sut.isLoading)
    }

    func testLoadContents_emptyResult_showsEmptyItems() async {
        mockRepo.folderContentsResult = DriveContents(folders: [], files: [], totalFolders: 0, totalFiles: 0)

        await sut.loadContents()

        XCTAssertTrue(sut.items.isEmpty)
        XCTAssertFalse(sut.isLoading)
        XCTAssertNil(sut.errorMessage)
    }

    // MARK: - Navigation Tests

    func testNavigateToFolder_appendsBreadcrumb() {
        let folder = makeSampleFolder(id: "d1", name: "Documents")

        sut.navigateToFolder(folder)

        XCTAssertEqual(sut.breadcrumbs.count, 2)
        XCTAssertEqual(sut.breadcrumbs.last?.id, "d1")
        XCTAssertEqual(sut.breadcrumbs.last?.name, "Documents")
        XCTAssertEqual(sut.currentFolderId, "d1")
    }

    func testNavigateToBreadcrumb_truncatesPath() {
        let folder1 = makeSampleFolder(id: "d1", name: "Folder1")
        let folder2 = makeSampleFolder(id: "d2", name: "Folder2")
        let folder3 = makeSampleFolder(id: "d3", name: "Folder3")

        sut.navigateToFolder(folder1)
        sut.navigateToFolder(folder2)
        sut.navigateToFolder(folder3)

        XCTAssertEqual(sut.breadcrumbs.count, 4) // root + 3

        // Navigate back to folder1 breadcrumb
        let crumb = BreadcrumbItem(id: "d1", name: "Folder1")
        sut.navigateToBreadcrumb(crumb)

        XCTAssertEqual(sut.breadcrumbs.count, 2) // root + folder1
        XCTAssertEqual(sut.currentFolderId, "d1")
    }

    func testNavigateBack_fromSubfolder_removesLastBreadcrumb() {
        let folder = makeSampleFolder(id: "d1", name: "Documents")
        sut.navigateToFolder(folder)

        XCTAssertEqual(sut.breadcrumbs.count, 2)

        let result = sut.navigateBack()

        XCTAssertTrue(result)
        XCTAssertEqual(sut.breadcrumbs.count, 1)
        XCTAssertNil(sut.currentFolderId) // back to root
    }

    func testNavigateBack_fromRoot_returnsFalse() {
        let result = sut.navigateBack()

        XCTAssertFalse(result)
        XCTAssertEqual(sut.breadcrumbs.count, 1)
    }

    // MARK: - Toggle Favorite Tests

    func testToggleFavorite_file_togglesLocalState() async {
        let file = makeSampleFile(id: "f1")
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file], totalFolders: 0, totalFiles: 1
        )
        await sut.loadContents()

        XCTAssertFalse(sut.favoriteFileIds.contains("f1"))

        let item = sut.items.first!
        await sut.toggleFavorite(item: item)

        XCTAssertTrue(sut.favoriteFileIds.contains("f1"))
        // Verify the item in list was updated
        if case .file(let updatedFile) = sut.items.first {
            XCTAssertTrue(updatedFile.isFavorite)
        } else {
            XCTFail("Expected file item")
        }
        XCTAssertEqual(mockRepo.toggleFavoriteCalled, 1)
        XCTAssertEqual(mockRepo.lastToggleFavoriteItemType, "file")
    }

    func testToggleFavorite_folder_togglesLocalState() async {
        let folder = makeSampleFolder(id: "d1")
        mockRepo.folderContentsResult = DriveContents(
            folders: [folder], files: [], totalFolders: 1, totalFiles: 0
        )
        await sut.loadContents()

        XCTAssertFalse(sut.favoriteFolderIds.contains("d1"))

        let item = sut.items.first!
        await sut.toggleFavorite(item: item)

        XCTAssertTrue(sut.favoriteFolderIds.contains("d1"))
        if case .folder(let updatedFolder) = sut.items.first {
            XCTAssertTrue(updatedFolder.isFavorite)
        } else {
            XCTFail("Expected folder item")
        }
        XCTAssertEqual(mockRepo.toggleFavoriteCalled, 1)
        XCTAssertEqual(mockRepo.lastToggleFavoriteItemType, "folder")
    }

    func testToggleFavorite_apiFailure_revertsState() async {
        let file = makeSampleFile(id: "f1")
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file], totalFolders: 0, totalFiles: 1
        )
        await sut.loadContents()

        mockRepo.toggleFavoriteError = MockError.forced

        let item = sut.items.first!
        await sut.toggleFavorite(item: item)

        // Should revert: file was not a favorite, toggled to favorite, then reverted
        XCTAssertFalse(sut.favoriteFileIds.contains("f1"))
        if case .file(let revertedFile) = sut.items.first {
            XCTAssertFalse(revertedFile.isFavorite)
        } else {
            XCTFail("Expected file item")
        }
        XCTAssertNotNil(sut.errorMessage)
    }

    // MARK: - Delete Tests

    func testDeleteItem_removesFromItems() async {
        let file = makeSampleFile(id: "f1")
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file], totalFolders: 0, totalFiles: 1
        )
        await sut.loadContents()
        XCTAssertEqual(sut.items.count, 1)

        let item = sut.items.first!
        await sut.deleteItem(item)

        XCTAssertTrue(sut.items.isEmpty)
        XCTAssertEqual(mockRepo.deleteFileCalled, 1)
        XCTAssertEqual(mockRepo.lastDeleteFileId, "f1")
    }

    func testDeleteItem_failure_reloadsContents() async {
        let file = makeSampleFile(id: "f1")
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file], totalFolders: 0, totalFiles: 1
        )
        await sut.loadContents()
        XCTAssertEqual(sut.items.count, 1)

        mockRepo.deleteFileError = MockError.forced
        let item = sut.items.first!
        await sut.deleteItem(item)

        // Delete API was attempted
        XCTAssertEqual(mockRepo.deleteFileCalled, 1)
        // On failure, the item is restored via loadContents (items not empty)
        // Note: errorMessage and reload happen asynchronously via @MainActor
        XCTAssertEqual(sut.items.count, 1)
    }

    // MARK: - Create Folder Tests

    func testCreateFolder_success_reloadsContents() async {
        sut.newFolderName = "New Folder"

        await sut.createFolder()

        XCTAssertEqual(mockRepo.createFolderCalled, 1)
        XCTAssertEqual(mockRepo.lastCreateFolderData?["folder_name"] as? String, "New Folder")
        XCTAssertEqual(sut.newFolderName, "")
        XCTAssertFalse(sut.showCreateFolderSheet)
        // loadContents called after creation
        XCTAssertEqual(mockRepo.getFolderContentsCalled, 1)
    }

    func testCreateFolder_emptyName_doesNothing() async {
        sut.newFolderName = "   "

        await sut.createFolder()

        XCTAssertEqual(mockRepo.createFolderCalled, 0)
    }

    // MARK: - Sort Tests

    func testSetSortOption_updatesSortAndResortsItems() async {
        let file1 = makeSampleFile(id: "f1", name: "Beta.pdf", size: 500, createdOn: 1000)
        let file2 = makeSampleFile(id: "f2", name: "Alpha.pdf", size: 2000, createdOn: 2000)
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file1, file2], totalFolders: 0, totalFiles: 2
        )
        await sut.loadContents()

        sut.setSortOption(.name)

        XCTAssertEqual(sut.sortBy, .name)
        // Alpha should come first when sorted by name
        XCTAssertEqual(sut.items.first?.name, "Alpha.pdf")
    }

    // MARK: - Move Tests

    func testMoveItem_success_reloadsContents() async {
        let file = makeSampleFile(id: "f1")
        let targetFolder = makeSampleFolder(id: "d2", name: "Target")
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file], totalFolders: 0, totalFiles: 1
        )
        await sut.loadContents()

        let item = sut.items.first!
        await sut.moveItem(item, toFolder: targetFolder)

        XCTAssertEqual(mockRepo.moveFileCalled, 1)
        XCTAssertEqual(mockRepo.lastMoveFileId, "f1")
        XCTAssertEqual(mockRepo.lastMoveTargetFolderId, "d2")
        // loadContents called after move
        XCTAssertEqual(mockRepo.getFolderContentsCalled, 2)
    }

    // MARK: - Share Link Tests

    func testGenerateShareLink_success_returnsLink() async {
        mockRepo.shareLinkResult = ShareLink(url: "https://share.example.com/abc", expiresAt: "2025-01-01")

        let result = await sut.generateShareLink(fileId: "f1")

        XCTAssertNotNil(result)
        XCTAssertEqual(result?.url, "https://share.example.com/abc")
        XCTAssertEqual(mockRepo.generateShareLinkCalled, 1)
        XCTAssertEqual(mockRepo.lastShareLinkFileId, "f1")
        XCTAssertEqual(mockRepo.lastShareLinkExpiry, "24h")
    }

    // MARK: - Force Load Tests

    func testForceLoadContents_callsForceGetFolderContents() async {
        mockRepo.folderContentsResult = DriveContents(folders: [], files: [], totalFolders: 0, totalFiles: 0)

        await sut.forceLoadContents()

        XCTAssertEqual(mockRepo.forceGetFolderContentsCalled, 1)
        XCTAssertEqual(mockRepo.getFolderContentsCalled, 0)
    }

    // MARK: - Sorting Behavior Tests

    func testSortItems_foldersFirst() async {
        let file = makeSampleFile(id: "f1", name: "Alpha.pdf")
        let folder = makeSampleFolder(id: "d1", name: "Zeta")
        mockRepo.folderContentsResult = DriveContents(
            folders: [folder], files: [file], totalFolders: 1, totalFiles: 1
        )

        await sut.loadContents()

        // Folder should always come first regardless of name
        XCTAssertTrue(sut.items.first?.isFolder == true)
        XCTAssertEqual(sut.items.first?.name, "Zeta")
    }

    func testSortItems_byName() async {
        let file1 = makeSampleFile(id: "f1", name: "Charlie.pdf")
        let file2 = makeSampleFile(id: "f2", name: "Alpha.pdf")
        let file3 = makeSampleFile(id: "f3", name: "Bravo.pdf")
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file1, file2, file3], totalFolders: 0, totalFiles: 3
        )

        sut.setSortOption(.name)
        await sut.loadContents()

        XCTAssertEqual(sut.items[0].name, "Alpha.pdf")
        XCTAssertEqual(sut.items[1].name, "Bravo.pdf")
        XCTAssertEqual(sut.items[2].name, "Charlie.pdf")
    }

    func testSortItems_byDate() async {
        let file1 = makeSampleFile(id: "f1", name: "Old.pdf", createdOn: 1000)
        let file2 = makeSampleFile(id: "f2", name: "New.pdf", createdOn: 3000)
        let file3 = makeSampleFile(id: "f3", name: "Mid.pdf", createdOn: 2000)
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file1, file2, file3], totalFolders: 0, totalFiles: 3
        )

        sut.setSortOption(.date)
        await sut.loadContents()

        // Sorted by date descending (newest first)
        XCTAssertEqual(sut.items[0].name, "New.pdf")
        XCTAssertEqual(sut.items[1].name, "Mid.pdf")
        XCTAssertEqual(sut.items[2].name, "Old.pdf")
    }

    func testSortItems_bySize() async {
        let file1 = makeSampleFile(id: "f1", name: "Small.pdf", size: 100)
        let file2 = makeSampleFile(id: "f2", name: "Large.pdf", size: 10000)
        let file3 = makeSampleFile(id: "f3", name: "Medium.pdf", size: 5000)
        mockRepo.folderContentsResult = DriveContents(
            folders: [], files: [file1, file2, file3], totalFolders: 0, totalFiles: 3
        )

        sut.setSortOption(.size)
        await sut.loadContents()

        // Sorted by size descending (largest first)
        XCTAssertEqual(sut.items[0].name, "Large.pdf")
        XCTAssertEqual(sut.items[1].name, "Medium.pdf")
        XCTAssertEqual(sut.items[2].name, "Small.pdf")
    }
}
