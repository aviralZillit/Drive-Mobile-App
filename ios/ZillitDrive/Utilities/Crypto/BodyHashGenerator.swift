import Foundation
import CryptoKit

/// Generates the bodyhash header matching the web app's generateBodyHash function.
///
/// Web implementation (multipleFunction.js:1808-1818):
///   1. Build object: { payload: requestBody, moduledata: encryptedModuleData }
///   2. JSON.stringify that object
///   3. Append the IV string (salt)
///   4. SHA-256 hash the combined string
///   5. Hex-encode the hash
enum BodyHashGenerator {

    static func generate(requestBody: Any?, moduledata: String, salt: String) -> String {
        // Build the moduleDataString object matching JS: { payload: requestBody, moduledata }
        let payloadValue: Any = requestBody ?? ""
        let moduleDataMap: [String: Any] = [
            "payload": payloadValue,
            "moduledata": moduledata
        ]

        // JSON.stringify equivalent
        let stringToHash: String
        if let jsonData = try? JSONSerialization.data(withJSONObject: moduleDataMap, options: [.sortedKeys]),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            stringToHash = jsonString
        } else {
            stringToHash = "{}"
        }

        // Append salt (IV string)
        let combinedString = stringToHash + salt

        // SHA-256 hash and hex-encode
        return sha256Hex(combinedString)
    }

    private static func sha256Hex(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}
