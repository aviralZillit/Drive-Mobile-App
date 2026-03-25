import XCTest
@testable import ZillitDrive

final class SessionManagerTests: XCTestCase {

    private var sut: SessionManager!
    private let testSessionKey = "com.zillit.drive.session"

    override func setUp() {
        super.setUp()
        sut = SessionManager.shared
        // Clean up any leftover session
        sut.clearSession()
    }

    override func tearDown() {
        sut.clearSession()
        super.tearDown()
    }

    // MARK: - Helpers

    private func makeSampleSession(
        userId: String = "user123",
        projectId: String = "proj456",
        deviceId: String = "device789",
        scannerDeviceId: String = "scanner012",
        userName: String = "Test User",
        userEmail: String = "test@example.com"
    ) -> UserSession {
        UserSession(
            userId: userId,
            projectId: projectId,
            deviceId: deviceId,
            scannerDeviceId: scannerDeviceId,
            userName: userName,
            userEmail: userEmail
        )
    }

    // MARK: - Save Session Tests

    func testSaveSession_persistsData() {
        let session = makeSampleSession()

        sut.saveSession(session)

        XCTAssertTrue(sut.isLoggedIn)
        XCTAssertNotNil(sut.currentSession)

        // Verify session persists across reload (now stored in Keychain)
        sut.loadSession()
        XCTAssertNotNil(sut.currentSession)
        XCTAssertEqual(sut.currentSession?.userId, "user123")
    }

    func testSaveSession_setsCurrentSession() {
        let session = makeSampleSession(userId: "unique-user-id")

        sut.saveSession(session)

        XCTAssertEqual(sut.currentSession?.userId, "unique-user-id")
        XCTAssertEqual(sut.currentSession?.projectId, "proj456")
        XCTAssertEqual(sut.currentSession?.deviceId, "device789")
        XCTAssertEqual(sut.currentSession?.scannerDeviceId, "scanner012")
        XCTAssertEqual(sut.currentSession?.userName, "Test User")
        XCTAssertEqual(sut.currentSession?.userEmail, "test@example.com")
    }

    func testSaveSession_updatesIsLoggedIn() {
        XCTAssertFalse(sut.isLoggedIn)

        sut.saveSession(makeSampleSession())

        XCTAssertTrue(sut.isLoggedIn)
    }

    // MARK: - Get Session Tests

    func testGetSession_returnsStoredSession() {
        let session = makeSampleSession(userId: "stored-user")
        sut.saveSession(session)

        // Force reload from storage
        sut.loadSession()

        XCTAssertNotNil(sut.currentSession)
        XCTAssertEqual(sut.currentSession?.userId, "stored-user")
        XCTAssertTrue(sut.isLoggedIn)
    }

    func testGetSession_returnsNilWhenNoSession() {
        sut.clearSession()
        sut.loadSession()

        XCTAssertNil(sut.currentSession)
        XCTAssertFalse(sut.isLoggedIn)
    }

    // MARK: - Clear Session Tests

    func testClearSession_removesSession() {
        sut.saveSession(makeSampleSession())
        XCTAssertTrue(sut.isLoggedIn)
        XCTAssertNotNil(sut.currentSession)

        sut.clearSession()

        XCTAssertFalse(sut.isLoggedIn)
        XCTAssertNil(sut.currentSession)

        // Verify session is cleared from Keychain
        sut.loadSession()
        XCTAssertNil(sut.currentSession)
    }

    func testClearSession_calledWhenNoSession_doesNotCrash() {
        sut.clearSession()

        XCTAssertFalse(sut.isLoggedIn)
        XCTAssertNil(sut.currentSession)
    }

    // MARK: - Current Session Tests

    func testCurrentSession_returnsLatestSession() {
        let session1 = makeSampleSession(userId: "first-user")
        sut.saveSession(session1)
        XCTAssertEqual(sut.currentSession?.userId, "first-user")

        let session2 = makeSampleSession(userId: "second-user")
        sut.saveSession(session2)
        XCTAssertEqual(sut.currentSession?.userId, "second-user")
    }

    // MARK: - Update Project ID Tests

    func testUpdateProjectId_updatesProjectId() {
        let session = makeSampleSession(projectId: "old-project")
        sut.saveSession(session)
        XCTAssertEqual(sut.currentSession?.projectId, "old-project")

        sut.updateProjectId("new-project")

        XCTAssertEqual(sut.currentSession?.projectId, "new-project")
        // Other fields should remain unchanged
        XCTAssertEqual(sut.currentSession?.userId, "user123")
        XCTAssertEqual(sut.currentSession?.userName, "Test User")
        XCTAssertTrue(sut.isLoggedIn)
    }

    func testUpdateProjectId_noCurrentSession_doesNothing() {
        sut.clearSession()

        sut.updateProjectId("new-project")

        XCTAssertNil(sut.currentSession)
        XCTAssertFalse(sut.isLoggedIn)
    }

    // MARK: - Session Encoding/Decoding

    func testSession_encodingDecoding_preservesAllFields() {
        let original = makeSampleSession(
            userId: "uid",
            projectId: "pid",
            deviceId: "did",
            scannerDeviceId: "sid",
            userName: "Name",
            userEmail: "email@test.com"
        )

        sut.saveSession(original)
        sut.loadSession()

        let loaded = sut.currentSession
        XCTAssertEqual(loaded?.userId, "uid")
        XCTAssertEqual(loaded?.projectId, "pid")
        XCTAssertEqual(loaded?.deviceId, "did")
        XCTAssertEqual(loaded?.scannerDeviceId, "sid")
        XCTAssertEqual(loaded?.userName, "Name")
        XCTAssertEqual(loaded?.userEmail, "email@test.com")
    }

    // MARK: - Persistence Tests

    func testSaveAndReload_sessionPersistsAcrossLoads() {
        let session = makeSampleSession(userId: "persistent-user")
        sut.saveSession(session)

        // Simulate "restart" by calling loadSession
        sut.loadSession()

        XCTAssertTrue(sut.isLoggedIn)
        XCTAssertEqual(sut.currentSession?.userId, "persistent-user")
    }
}
