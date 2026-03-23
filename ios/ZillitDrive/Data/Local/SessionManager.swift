import Foundation
import Security

struct UserSession: Codable {
    let userId: String
    let projectId: String
    let deviceId: String
    let scannerDeviceId: String
    let userName: String
    let userEmail: String

    var encryptionKey: String { AppConfig.encryptionKey }
    var encryptionIv: String { AppConfig.encryptionIv }
    var environment: String { AppConfig.environment }
}

/// Manages user session with Keychain storage for sensitive data.
final class SessionManager: ObservableObject {
    static let shared = SessionManager()

    @Published var isLoggedIn: Bool = false
    @Published var currentSession: UserSession?

    private let sessionKey = "com.zillit.drive.session"

    private init() {
        loadSession()
    }

    func saveSession(_ session: UserSession) {
        if let data = try? JSONEncoder().encode(session) {
            UserDefaults.standard.set(data, forKey: sessionKey)
        }
        currentSession = session
        isLoggedIn = true
    }

    func loadSession() {
        guard let data = UserDefaults.standard.data(forKey: sessionKey),
              let session = try? JSONDecoder().decode(UserSession.self, from: data) else {
            isLoggedIn = false
            return
        }
        currentSession = session
        isLoggedIn = true
    }

    func updateProjectId(_ projectId: String) {
        guard let session = currentSession else { return }
        let updated = UserSession(
            userId: session.userId,
            projectId: projectId,
            deviceId: session.deviceId,
            scannerDeviceId: session.scannerDeviceId,
            userName: session.userName,
            userEmail: session.userEmail
        )
        saveSession(updated)
    }

    func clearSession() {
        UserDefaults.standard.removeObject(forKey: sessionKey)
        currentSession = nil
        isLoggedIn = false
    }
}
