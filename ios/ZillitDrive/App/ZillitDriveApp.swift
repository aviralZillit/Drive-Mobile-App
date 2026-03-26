import SwiftUI
import UserNotifications

@main
struct ZillitDriveApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var sessionManager = SessionManager.shared
    @StateObject private var deepLink = DeepLinkState()

    var body: some Scene {
        WindowGroup {
            if sessionManager.isLoggedIn {
                MainTabView()
                    .environmentObject(sessionManager)
                    .environmentObject(deepLink)
                    .onOpenURL { url in
                        handleDeepLink(url)
                    }
            } else {
                LoginView()
                    .environmentObject(sessionManager)
            }
        }
    }

    private func handleDeepLink(_ url: URL) {
        // zillit-drive://file/{fileId} or zillit-drive://folder/{folderId}
        guard url.scheme == "zillit-drive" else { return }
        let host = url.host ?? ""
        let pathId = url.pathComponents.dropFirst().first ?? ""

        switch host {
        case "file":
            deepLink.pendingFileId = pathId
        case "folder":
            deepLink.pendingFolderId = pathId
        default:
            break
        }
    }
}

// MARK: - Deep Link State

class DeepLinkState: ObservableObject {
    @Published var pendingFileId: String?
    @Published var pendingFolderId: String?
}

// MARK: - App Delegate (Push Notifications)

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        #if DEBUG
        print("[Push] APNs token: \(token)")
        #endif
        // TODO: Send token to backend via POST /notifications/register-device
        // when Firebase is configured, forward to Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        #if DEBUG
        print("[Push] Failed to register: \(error)")
        #endif
    }

    // Handle notification when app is in foreground
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .badge, .sound])
    }

    // Handle notification tap
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        if let fileId = userInfo["file_id"] as? String {
            DeepLinkState().pendingFileId = fileId
        } else if let folderId = userInfo["folder_id"] as? String {
            DeepLinkState().pendingFolderId = folderId
        }
        completionHandler()
    }
}

// MARK: - Main Tab View

struct MainTabView: View {
    @EnvironmentObject var sessionManager: SessionManager

    var body: some View {
        TabView {
            NavigationStack {
                HomeView()
            }
            .tabItem {
                Label("Files", systemImage: "folder.fill")
            }

            NavigationStack {
                SearchView()
            }
            .tabItem {
                Label("Search", systemImage: "magnifyingglass")
            }

            NavigationStack {
                UploadView()
            }
            .tabItem {
                Label("Upload", systemImage: "arrow.up.circle.fill")
            }

            NavigationStack {
                FavoritesView()
            }
            .tabItem {
                Label("Favorites", systemImage: "star.fill")
            }

            NavigationStack {
                MoreView()
            }
            .tabItem {
                Label("More", systemImage: "ellipsis.circle.fill")
            }
        }
        .tint(Color("AccentColor"))
        .environmentObject(sessionManager)
    }
}
