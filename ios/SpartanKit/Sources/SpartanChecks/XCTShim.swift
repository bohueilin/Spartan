// Minimal XCTest-compatible shim so the SpartanKit test suite runs as a plain executable
// (`swift run SpartanChecks`) on machines with only Command Line Tools (no XCTest). On a
// machine with Xcode, the identical files under Tests/SpartanKitTests run via `swift test`.

import Foundation

var shimFailureCount = 0
var shimAssertionCount = 0
var shimCurrentTest = ""

private func fail(_ message: String, _ file: StaticString, _ line: UInt) {
    shimFailureCount += 1
    print("  ✗ FAIL [\(shimCurrentTest)] \(file):\(line) — \(message)")
}

open class XCTestCase {
    public init() {}
    open func setUp() {}
}

public struct ShimUnwrapError: Error {}

public func XCTAssertTrue(_ expression: @autoclosure () -> Bool, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    if !expression() { fail("XCTAssertTrue failed. \(message())", file, line) }
}

public func XCTAssertFalse(_ expression: @autoclosure () -> Bool, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    if expression() { fail("XCTAssertFalse failed. \(message())", file, line) }
}

public func XCTAssertEqual<T: Equatable>(_ a: @autoclosure () -> T, _ b: @autoclosure () -> T, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    let (x, y) = (a(), b())
    if x != y { fail("XCTAssertEqual failed: (\(x)) != (\(y)). \(message())", file, line) }
}

public func XCTAssertEqual(_ a: @autoclosure () -> Double, _ b: @autoclosure () -> Double, accuracy: Double, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    let (x, y) = (a(), b())
    if abs(x - y) > accuracy { fail("XCTAssertEqual failed: |\(x) - \(y)| > \(accuracy). \(message())", file, line) }
}

public func XCTAssertNil(_ expression: @autoclosure () -> Any?, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    if expression() != nil { fail("XCTAssertNil failed. \(message())", file, line) }
}

public func XCTAssertNotNil(_ expression: @autoclosure () -> Any?, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    if expression() == nil { fail("XCTAssertNotNil failed. \(message())", file, line) }
}

public func XCTUnwrap<T>(_ expression: @autoclosure () -> T?, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) throws -> T {
    shimAssertionCount += 1
    guard let value = expression() else {
        fail("XCTUnwrap failed: value was nil. \(message())", file, line)
        throw ShimUnwrapError()
    }
    return value
}

public func XCTAssertThrowsError<T>(_ expression: @autoclosure () throws -> T, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line, _ errorHandler: (Error) -> Void = { _ in }) {
    shimAssertionCount += 1
    do {
        _ = try expression()
        fail("XCTAssertThrowsError failed: no error thrown. \(message())", file, line)
    } catch {
        errorHandler(error)
    }
}

public func XCTAssertNoThrow<T>(_ expression: @autoclosure () throws -> T, _ message: @autoclosure () -> String = "", file: StaticString = #filePath, line: UInt = #line) {
    shimAssertionCount += 1
    do { _ = try expression() } catch {
        fail("XCTAssertNoThrow failed: threw \(error). \(message())", file, line)
    }
}
