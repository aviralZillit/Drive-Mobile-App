import Foundation
import CommonCrypto

/// AES-CBC encryption/decryption matching the web app's encryptDecrypt.js.
///
/// Web implementation:
///   - Key: UTF-8 encoded string from VITE_KEY_ENCRYPTION_KEY
///   - IV:  UTF-8 encoded string from VITE_IV_ENCRYPTION_KEY
///   - Algorithm: AES-CBC with PKCS7 padding
///   - Output: hex-encoded ciphertext
enum AESCBCEncryptor {

    enum CryptoError: Error {
        case encryptionFailed(status: CCCryptorStatus)
        case decryptionFailed(status: CCCryptorStatus)
        case invalidHexString
    }

    /// Encrypts plaintext using AES-CBC with PKCS7 padding, returns hex-encoded ciphertext.
    static func encrypt(plaintext: String, key: String, iv: String) throws -> String {
        let keyData = Data(key.utf8)
        let ivData = Data(iv.utf8)
        let inputData = Data(plaintext.utf8)

        let bufferSize = inputData.count + kCCBlockSizeAES128
        var buffer = Data(count: bufferSize)
        var numBytesEncrypted: size_t = 0

        let status = buffer.withUnsafeMutableBytes { bufferPtr in
            inputData.withUnsafeBytes { inputPtr in
                keyData.withUnsafeBytes { keyPtr in
                    ivData.withUnsafeBytes { ivPtr in
                        CCCrypt(
                            CCOperation(kCCEncrypt),
                            CCAlgorithm(kCCAlgorithmAES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyPtr.baseAddress, keyData.count,
                            ivPtr.baseAddress,
                            inputPtr.baseAddress, inputData.count,
                            bufferPtr.baseAddress, bufferSize,
                            &numBytesEncrypted
                        )
                    }
                }
            }
        }

        guard status == kCCSuccess else {
            throw CryptoError.encryptionFailed(status: status)
        }

        buffer.count = numBytesEncrypted
        return buffer.map { String(format: "%02x", $0) }.joined()
    }

    /// Decrypts hex-encoded ciphertext using AES-CBC with PKCS7 padding.
    static func decrypt(hexCiphertext: String, key: String, iv: String) throws -> String {
        let keyData = Data(key.utf8)
        let ivData = Data(iv.utf8)
        let inputData = try hexStringToData(hexCiphertext)

        let bufferSize = inputData.count + kCCBlockSizeAES128
        var buffer = Data(count: bufferSize)
        var numBytesDecrypted: size_t = 0

        let status = buffer.withUnsafeMutableBytes { bufferPtr in
            inputData.withUnsafeBytes { inputPtr in
                keyData.withUnsafeBytes { keyPtr in
                    ivData.withUnsafeBytes { ivPtr in
                        CCCrypt(
                            CCOperation(kCCDecrypt),
                            CCAlgorithm(kCCAlgorithmAES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyPtr.baseAddress, keyData.count,
                            ivPtr.baseAddress,
                            inputPtr.baseAddress, inputData.count,
                            bufferPtr.baseAddress, bufferSize,
                            &numBytesDecrypted
                        )
                    }
                }
            }
        }

        guard status == kCCSuccess else {
            throw CryptoError.decryptionFailed(status: status)
        }

        buffer.count = numBytesDecrypted
        return String(data: buffer, encoding: .utf8) ?? ""
    }

    private static func hexStringToData(_ hex: String) throws -> Data {
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let nextIndex = hex.index(index, offsetBy: 2)
            guard nextIndex <= hex.endIndex,
                  let byte = UInt8(hex[index..<nextIndex], radix: 16) else {
                throw CryptoError.invalidHexString
            }
            data.append(byte)
            index = nextIndex
        }
        return data
    }
}
