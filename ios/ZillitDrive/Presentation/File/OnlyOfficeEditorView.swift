import SwiftUI
import WebKit

struct OnlyOfficeEditorView: View {
    let fileId: String
    let fileName: String
    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = true
    @State private var error: String?
    @State private var editorURL: URL?
    private let repository: DriveRepository = DriveRepositoryImpl()

    var body: some View {
        NavigationStack {
            ZStack {
                if let url = editorURL {
                    CollaboraWebView(url: url, isLoading: $isLoading, error: $error)
                        .ignoresSafeArea()
                } else if let error = error {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundColor(.orange)
                        Text(error)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                        Button("Retry") { Task { await loadEditor() } }
                            .buttonStyle(.bordered)
                    }
                    .padding()
                }

                if isLoading {
                    ProgressView("Loading editor...")
                        .padding()
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                }
            }
            .navigationTitle(fileName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task { await loadEditor() }
    }

    private func loadEditor() async {
        isLoading = true
        error = nil
        do {
            let token = try await repository.getEditorPageToken(fileId: fileId)
            let urlString = "\(AppConfig.driveBaseURL)/v2/drive/editor/\(fileId)/page?token=\(token)"
            guard let url = URL(string: urlString) else {
                error = "Invalid editor URL"
                isLoading = false
                return
            }
            editorURL = url
        } catch {
            self.error = "Failed to load editor: \(error.localizedDescription)"
            isLoading = false
        }
    }
}

// MARK: - Collabora-optimized WKWebView

struct CollaboraWebView: UIViewRepresentable {
    let url: URL
    @Binding var isLoading: Bool
    @Binding var error: String?

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        // Allow Collabora iframe to work properly
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs

        // Enable clipboard access for copy/paste in editor
        config.preferences.javaScriptCanOpenWindowsAutomatically = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.scrollView.isScrollEnabled = true
        webView.scrollView.bounces = false

        // Allow mixed content (Collabora may use HTTP internally)
        webView.configuration.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")

        // Disable zoom — Collabora handles its own zoom
        webView.scrollView.minimumZoomScale = 1.0
        webView.scrollView.maximumZoomScale = 1.0

        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(isLoading: $isLoading, error: $error)
    }

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        @Binding var isLoading: Bool
        @Binding var error: String?

        init(isLoading: Binding<Bool>, error: Binding<String?>) {
            _isLoading = isLoading
            _error = error
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            isLoading = false
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            isLoading = false
            self.error = "Editor failed to load: \(error.localizedDescription)"
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            isLoading = false
            self.error = "Cannot connect to editor: \(error.localizedDescription)"
        }

        // Allow Collabora iframe navigation (cross-origin)
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            decisionHandler(.allow)
        }

        // Handle Collabora opening new windows (popups for insert image, etc.)
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            // Load popup content in the same webview
            if navigationAction.targetFrame == nil {
                webView.load(navigationAction.request)
            }
            return nil
        }

        // Allow self-signed certs in development
        func webView(_ webView: WKWebView, didReceive challenge: URLAuthenticationChallenge, completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
            #if DEBUG
            if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
               let serverTrust = challenge.protectionSpace.serverTrust {
                completionHandler(.useCredential, URLCredential(trust: serverTrust))
                return
            }
            #endif
            completionHandler(.performDefaultHandling, nil)
        }
    }
}
