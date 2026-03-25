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

    private let keychainService = "com.zillit.drive"
    private let keychainAccount = "session"

    private init() {
        loadSession()
    }

    func saveSession(_ session: UserSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }

        // Delete existing item first
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        // Add new item
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemAdd(addQuery as CFDictionary, nil)

        currentSession = session
        isLoggedIn = true
    }

    func loadSession() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess,
              let data = result as? Data,
              let session = try? JSONDecoder().decode(UserSession.self, from: data) else {
            // Migrate from UserDefaults if exists
            if let legacyData = UserDefaults.standard.data(forKey: "com.zillit.drive.session"),
               let session = try? JSONDecoder().decode(UserSession.self, from: legacyData) {
                saveSession(session) // Move to Keychain
                UserDefaults.standard.removeObject(forKey: "com.zillit.drive.session")
                return
            }
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
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
        ]
        SecItemDelete(query as CFDictionary)
        // Also clean up legacy UserDefaults if any
        UserDefaults.standard.removeObject(forKey: "com.zillit.drive.session")
        currentSession = nil
        isLoggedIn = false
    }
}
