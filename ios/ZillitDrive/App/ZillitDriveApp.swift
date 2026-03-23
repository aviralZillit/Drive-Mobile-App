import SwiftUI

@main
struct ZillitDriveApp: App {
    @StateObject private var sessionManager = SessionManager.shared

    var body: some Scene {
        WindowGroup {
            if sessionManager.isLoggedIn {
                MainTabView()
                    .environmentObject(sessionManager)
            } else {
                LoginView()
                    .environmentObject(sessionManager)
            }
        }
    }
}

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
