import XCTest
@testable import ZillitDrive

final class SortOptionTests: XCTestCase {

    // MARK: - Display Names

    func testSortOption_displayNames() {
        XCTAssertEqual(SortOption.name.displayName, "Name")
        XCTAssertEqual(SortOption.date.displayName, "Date")
        XCTAssertEqual(SortOption.size.displayName, "Size")
    }

    // MARK: - API Values

    func testSortOption_apiValues() {
        XCTAssertEqual(SortOption.name.apiValue, "name")
        XCTAssertEqual(SortOption.date.apiValue, "created_on")
        XCTAssertEqual(SortOption.size.apiValue, "file_size_bytes")
    }

    // MARK: - All Cases

    func testSortOption_allCases() {
        let allCases = SortOption.allCases
        XCTAssertEqual(allCases.count, 3)
        XCTAssertTrue(allCases.contains(.name))
        XCTAssertTrue(allCases.contains(.date))
        XCTAssertTrue(allCases.contains(.size))
    }

    // MARK: - Raw Values

    func testSortOption_rawValues() {
        XCTAssertEqual(SortOption.name.rawValue, "name")
        XCTAssertEqual(SortOption.date.rawValue, "date")
        XCTAssertEqual(SortOption.size.rawValue, "size")
    }

    func testSortOption_initFromRawValue() {
        XCTAssertEqual(SortOption(rawValue: "name"), .name)
        XCTAssertEqual(SortOption(rawValue: "date"), .date)
        XCTAssertEqual(SortOption(rawValue: "size"), .size)
        XCTAssertNil(SortOption(rawValue: "invalid"))
    }

    // MARK: - Unique Values

    func testSortOption_displayNamesAreUnique() {
        let displayNames = SortOption.allCases.map { $0.displayName }
        XCTAssertEqual(Set(displayNames).count, displayNames.count)
    }

    func testSortOption_apiValuesAreUnique() {
        let apiValues = SortOption.allCases.map { $0.apiValue }
        XCTAssertEqual(Set(apiValues).count, apiValues.count)
    }
}
