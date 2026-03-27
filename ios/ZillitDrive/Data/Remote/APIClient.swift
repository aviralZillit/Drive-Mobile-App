import Foundation

/// URLSession-based API client with moduledata/bodyhash header injection.
/// Mirrors the web app's interceptor.js pattern.
final class APIClient {
    static let shared = APIClient()

    private let session: URLSession
    private let baseURL: String
    private let pathPrefix: String
    private let sessionManager = SessionManager.shared

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)
        self.baseURL = AppConfig.driveBaseURL
        self.pathPrefix = "/v2/drive/"
    }

    /// Init with custom base URL and path prefix (for calling PM, CNC, etc.)
    init(baseURL: String, pathPrefix: String = "/v2/") {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        config.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: config)
        self.baseURL = baseURL
        self.pathPrefix = pathPrefix
    }

    // MARK: - Public API

    func request<T: Decodable>(
        endpoint: String,
        method: HTTPMethod = .get,
        body: [String: Any]? = nil,
        queryParams: [String: String]? = nil
    ) async throws -> APIResponse<T> {
        let url = try buildURL(endpoint: endpoint, queryParams: queryParams)
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue

        // Set body
        var bodyString = ""
        if let body = body, method != .get {
            let bodyData = try JSONSerialization.data(withJSONObject: body)
            request.httpBody = bodyData
            bodyString = String(data: bodyData, encoding: .utf8) ?? ""
        }

        // Inject headers
        try await injectHeaders(into: &request, bodyString: bodyString)

        // Execute request
        #if DEBUG
        // Print full curl command for debugging
        var curlParts = ["curl '\(url.absoluteString)'"]
        if method != .get { curlParts.append("-X \(method.rawValue)") }
        for (key, value) in request.allHTTPHeaderFields ?? [:] {
            curlParts.append("-H '\(key): \(value)'")
        }
        if let body = request.httpBody, let bodyStr = String(data: body, encoding: .utf8) {
            curlParts.append("-d '\(bodyStr)'")
        }
        print("[CURL] \(curlParts.joined(separator: " \\\n  "))")
        #endif
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        #if DEBUG
        print("[API] \(method.rawValue) \(url.path) → \(httpResponse.statusCode) (\(data.count) bytes)")
        if let body = String(data: data, encoding: .utf8) {
            print("[RESPONSE] \(String(body.prefix(1000)))")
        }
        #endif

        if httpResponse.statusCode == 401 {
            sessionManager.clearSession()
            throw APIError.unauthorized
        }

        if httpResponse.statusCode == 403 {
            throw APIError.forbidden
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            throw APIError.httpError(statusCode: httpResponse.statusCode)
        }

        let decoder = JSONDecoder()
        do {
            return try decoder.decode(APIResponse<T>.self, from: data)
        } catch {
            #if DEBUG
            if let jsonString = String(data: data, encoding: .utf8) {
                print("🔴 Decode error for \(T.self): \(error)")
                print("🔴 Raw response: \(String(jsonString.prefix(500)))")
            }
            #endif
            throw APIError.decodingError(error)
        }
    }

    /// Raw request returning Data (for streaming/downloads)
    func rawRequest(
        url: URL,
        method: HTTPMethod = .get
    ) async throws -> (Data, URLResponse) {
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        return try await session.data(for: request)
    }

    // MARK: - Header Injection

    private func injectHeaders(into request: inout URLRequest, bodyString: String) async throws {
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json, text/html", forHTTPHeaderField: "Accept")
        request.setValue("1.0.0", forHTTPHeaderField: "appVersion")

        guard let userSession = sessionManager.currentSession else { return }

        // Build moduledata payload
        var moduleDataDict: [String: Any] = [
            "device_id": userSession.deviceId,
            "project_id": userSession.projectId,
            "user_id": userSession.userId,
            "scanner_device_id": userSession.scannerDeviceId
        ]

        if userSession.environment == "development" || userSession.environment == "preprod" {
            moduleDataDict["time_stamp"] = Int(Date().timeIntervalSince1970 * 1000)
        }

        let moduleDataJSON = try JSONSerialization.data(withJSONObject: moduleDataDict, options: [.sortedKeys])
        let moduleDataString = String(data: moduleDataJSON, encoding: .utf8) ?? "{}"

        // AES-CBC encrypt moduledata
        let encryptedModuleData = try AESCBCEncryptor.encrypt(
            plaintext: moduleDataString,
            key: userSession.encryptionKey,
            iv: userSession.encryptionIv
        )

        // Generate bodyhash
        let requestBody: Any = request.httpMethod == "GET" ? "" : (bodyString.isEmpty ? "" : bodyString)
        let bodyHash = BodyHashGenerator.generate(
            requestBody: requestBody,
            moduledata: encryptedModuleData,
            salt: userSession.encryptionIv
        )

        request.setValue(encryptedModuleData, forHTTPHeaderField: "moduledata")
        request.setValue(bodyHash, forHTTPHeaderField: "bodyhash")
    }

    // MARK: - URL Building

    private func buildURL(endpoint: String, queryParams: [String: String]?) throws -> URL {
        var urlString = "\(baseURL)\(pathPrefix)\(endpoint)"
        if let params = queryParams, !params.isEmpty {
            let queryString = params
                .filter { !$0.value.isEmpty }
                .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }
                .joined(separator: "&")
            if !queryString.isEmpty {
                urlString += "?\(queryString)"
            }
        }
        guard let url = URL(string: urlString) else {
            throw APIError.invalidURL(urlString)
        }
        return url
    }
}

// MARK: - Supporting Types

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
}

enum APIError: Error, LocalizedError {
    case invalidURL(String)
    case invalidResponse
    case unauthorized
    case forbidden
    case httpError(statusCode: Int)
    case decodingError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL(let url): return "Invalid URL: \(url)"
        case .invalidResponse: return "Invalid server response"
        case .unauthorized: return "Session expired. Please login again."
        case .forbidden: return "Access denied"
        case .httpError(let code): return "Server error (\(code))"
        case .decodingError(let error): return "Data parsing error: \(error.localizedDescription)"
        }
    }
}
