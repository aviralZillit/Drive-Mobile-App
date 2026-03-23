import XCTest
@testable import ZillitDrive

final class BodyHashGeneratorTests: XCTestCase {

    private let testSalt = "abcdef0123456789"

    func testConsistentHash() {
        let hash1 = BodyHashGenerator.generate(requestBody: "body", moduledata: "moduledata", salt: testSalt)
        let hash2 = BodyHashGenerator.generate(requestBody: "body", moduledata: "moduledata", salt: testSalt)
        XCTAssertEqual(hash1, hash2)
    }

    func testSHA256Length() {
        let hash = BodyHashGenerator.generate(requestBody: "test", moduledata: "mod", salt: testSalt)
        XCTAssertEqual(hash.count, 64) // SHA-256 = 64 hex chars
    }

    func testHexFormat() {
        let hash = BodyHashGenerator.generate(requestBody: "test", moduledata: "mod", salt: testSalt)
        XCTAssertTrue(hash.allSatisfy { "0123456789abcdef".contains($0) })
    }

    func testDifferentBodiesProduceDifferentHashes() {
        let hash1 = BodyHashGenerator.generate(requestBody: "body1", moduledata: "mod", salt: testSalt)
        let hash2 = BodyHashGenerator.generate(requestBody: "body2", moduledata: "mod", salt: testSalt)
        XCTAssertNotEqual(hash1, hash2)
    }

    func testNilBodyProducesValidHash() {
        let hash = BodyHashGenerator.generate(requestBody: nil, moduledata: "mod", salt: testSalt)
        XCTAssertEqual(hash.count, 64)
    }

    func testEmptyBodyProducesValidHash() {
        let hash = BodyHashGenerator.generate(requestBody: "", moduledata: "mod", salt: testSalt)
        XCTAssertEqual(hash.count, 64)
    }
}
