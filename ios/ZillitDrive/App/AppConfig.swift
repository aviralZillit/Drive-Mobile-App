import Foundation

/// App configuration loaded from build settings / environment.
/// In production, these values come from .xcconfig files injected by CI.
enum AppConfig {
    #if DEBUG
    static let driveBaseURL = "https://driveapi-dev.zillit.com/api"
    static let socketURL = "https://driveapi-dev.zillit.com"
    // Other services use remote dev APIs
    static let projectBaseURL = "https://projectapi-dev.zillit.com/api"
    static let notificationBaseURL = "https://notificationapi-dev.zillit.com/api"
    static let mediaBaseURL = "https://mediaapi-dev.zillit.com/api"
    static let cncBaseURL = "https://cncapi-dev.zillit.com/api"
    static let cncSocketURL = "https://cncapi-dev.zillit.com"
    static let environment = "development"
    #else
    static let driveBaseURL = "" // Set via xcconfig in CI
    static let socketURL = ""
    static let projectBaseURL = ""
    static let notificationBaseURL = ""
    static let mediaBaseURL = ""
    static let cncBaseURL = ""
    static let cncSocketURL = ""
    static let environment = "production"
    #endif

    // These must match VITE_KEY_ENCRYPTION_KEY and VITE_IV_ENCRYPTION_KEY
    // In production, inject via .xcconfig or build settings
    static let encryptionKey: String = {
        if let key = Bundle.main.infoDictionary?["ENCRYPTION_KEY"] as? String, !key.isEmpty {
            return key
        }
        if let key = ProcessInfo.processInfo.environment["ENCRYPTION_KEY"], !key.isEmpty {
            return key
        }
        #if DEBUG
        return "Yz2eI81ZLzCxJwf7BjTsMjyx-_PH5op="
        #else
        return ""
        #endif
    }()

    static let encryptionIv: String = {
        if let iv = Bundle.main.infoDictionary?["ENCRYPTION_IV"] as? String, !iv.isEmpty {
            return iv
        }
        if let iv = ProcessInfo.processInfo.environment["ENCRYPTION_IV"], !iv.isEmpty {
            return iv
        }
        #if DEBUG
        return "Brxd-7fAiRQFYz2e"
        #else
        return ""
        #endif
    }()
}
