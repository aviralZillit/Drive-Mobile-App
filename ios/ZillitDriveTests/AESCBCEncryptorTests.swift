import XCTest
@testable import ZillitDrive

final class AESCBCEncryptorTests: XCTestCase {

    // 16-byte test key and IV (AES-128)
    private let testKey = "0123456789abcdef"
    private let testIV  = "fedcba9876543210"

    func testEncryptProducesHexOutput() throws {
        let result = try AESCBCEncryptor.encrypt(plaintext: "hello", key: testKey, iv: testIV)
        // Hex string: only 0-9a-f characters, even length
        XCTAssertTrue(result.allSatisfy { "0123456789abcdef".contains($0) })
        XCTAssertTrue(result.count % 2 == 0)
        XCTAssertTrue(result.count >= 32) // At least one AES block (16 bytes = 32 hex chars)
    }

    func testRoundTrip() throws {
        let plaintext = "test roundtrip data"
        let encrypted = try AESCBCEncryptor.encrypt(plaintext: plaintext, key: testKey, iv: testIV)
        let decrypted = try AESCBCEncryptor.decrypt(hexCiphertext: encrypted, key: testKey, iv: testIV)
        XCTAssertEqual(decrypted, plaintext)
    }

    func testEncryptConsistency() throws {
        let result1 = try AESCBCEncryptor.encrypt(plaintext: "same input", key: testKey, iv: testIV)
        let result2 = try AESCBCEncryptor.encrypt(plaintext: "same input", key: testKey, iv: testIV)
        XCTAssertEqual(result1, result2)
    }

    func testDifferentInputsProduceDifferentOutputs() throws {
        let result1 = try AESCBCEncryptor.encrypt(plaintext: "text1", key: testKey, iv: testIV)
        let result2 = try AESCBCEncryptor.encrypt(plaintext: "text2", key: testKey, iv: testIV)
        XCTAssertNotEqual(result1, result2)
    }

    func testEmptyString() throws {
        let encrypted = try AESCBCEncryptor.encrypt(plaintext: "", key: testKey, iv: testIV)
        let decrypted = try AESCBCEncryptor.decrypt(hexCiphertext: encrypted, key: testKey, iv: testIV)
        XCTAssertEqual(decrypted, "")
    }

    func testJSONModuleData() throws {
        let json = "{\"device_id\":\"d1\",\"project_id\":\"p1\",\"user_id\":\"u1\"}"
        let encrypted = try AESCBCEncryptor.encrypt(plaintext: json, key: testKey, iv: testIV)
        let decrypted = try AESCBCEncryptor.decrypt(hexCiphertext: encrypted, key: testKey, iv: testIV)
        XCTAssertEqual(decrypted, json)
    }

    func testInvalidHexDecryptionThrows() {
        XCTAssertThrowsError(try AESCBCEncryptor.decrypt(hexCiphertext: "zzzz", key: testKey, iv: testIV))
    }
}
