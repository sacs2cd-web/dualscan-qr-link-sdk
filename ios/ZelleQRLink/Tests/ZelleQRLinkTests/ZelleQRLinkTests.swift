import Foundation
import XCTest
@testable import ZelleQRLink

final class ZelleQRLinkTests: XCTestCase {
    private let canonical = "https://enroll.zellepay.com/qr-codes"
    private let parser = ZelleQRLinkParser(bridgeHost: "qr.example.com", customScheme: "dualscan-zelleqr")
    private let example = "ewogICJuYW1lIiA6ICJUYXlsb3IiLAogICJ0b2tlbiIgOiAiMTIwMjU1NTAxMjMiCn0%3D"

    func testCanonicalAndConfiguredRoutes() throws {
        let expected = ZelleQRPayload(name: "Taylor", token: "12025550123")
        for route in [
            canonical, canonical + "/", "HTTPS://ENROLL.ZELLEPAY.COM/qr-codes",
            "https://qr.example.com/qr-codes", "dualscan-zelleqr://qr/qr-codes"
        ] {
            let link = route + "?data=" + example
            XCTAssertEqual(try parser.parse(link), expected)
            XCTAssertEqual(try parser.parse(XCTUnwrap(URL(string: link))), expected)
        }
    }

    func testPreservesPlusAndSupportsBase64URL() throws {
        let json = #"{"name":"x¾","token":" 00012 "}"#
        let standard = Data(json.utf8).base64EncodedString()
        XCTAssertTrue(standard.contains("+"))
        let expected = ZelleQRPayload(name: "x¾", token: " 00012 ")
        XCTAssertEqual(try parser.parse(canonical + "?data=" + standard), expected)
        XCTAssertEqual(
            try parser.parse(canonical + "?v=1&d%61ta=" + standard.replacingOccurrences(of: "+", with: "%2B")),
            expected
        )
        let base64URL = standard.replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
        XCTAssertEqual(try parser.parse(canonical + "?data=" + base64URL), expected)
        XCTAssertEqual(
            try parser.parse(canonical + "?data=" + base64URL.replacingOccurrences(of: "=", with: "")),
            expected
        )
    }

    func testInvalidConfigurationLeavesRouteDisabled() {
        for host in ["person@qr.example.com", "qr.example.com:443", ".qr.example.com", "qr.example.com."] {
            let configured = ZelleQRLinkParser(bridgeHost: host)
            XCTAssertThrowsError(try configured.parse("https://" + host + "/qr-codes?data=" + example))
        }
        for scheme in ["https", "http", "1qr", "dualscan_qr", ""] {
            let configured = ZelleQRLinkParser(customScheme: scheme)
            XCTAssertThrowsError(try configured.parse(scheme + "://qr/qr-codes?data=" + example))
        }
    }

    func testRejectsUnsupportedRoutes() {
        for route in [
            "http://enroll.zellepay.com/qr-codes",
            "https://enroll.zellepay.com.evil.example/qr-codes",
            "https://%65nroll.zellepay.com/qr-codes",
            "https://enroll.zellepay.com:/qr-codes",
            "https://enroll.zellepay.com:443/qr-codes",
            "https://person@enroll.zellepay.com/qr-codes",
            "https://enroll.zellepay.com/QR-codes",
            "https://enroll.zellepay.com/%71r-codes",
            "https://enroll.zellepay.com/qr-codes//",
            "https://enroll.zellepay.com/other",
            "dualscan-zelleqr://other/qr-codes"
        ] {
            XCTAssertThrowsError(try parser.parse(route + "?data=" + example), route)
        }
        XCTAssertThrowsError(try parser.parse(canonical + "?data=" + example + "#"))
        XCTAssertThrowsError(try ZelleQRLinkParser().parse("https://qr.example.com/qr-codes?data=" + example))
        XCTAssertThrowsError(try ZelleQRLinkParser().parse("dualscan-zelleqr://qr/qr-codes?data=" + example))
    }

    func testRejectsInvalidQueryAndBase64() {
        for suffix in [
            "", "?", "?id=" + example, "?data", "?data=", "?data=a",
            "?data=AAAA==", "?data=ab%3D%3D", "?data=%FF", "?data=++__",
            "?data=%252B", "?data=!!!", "?data=x y", "?data=" + example + "&d%61ta=" + example,
            "?data=" + String(repeating: "a", count: 4_097),
            "?data=" + example + "&extra=" + String(repeating: "a", count: 8_193),
            "?data=" + example + "&extra=%"
        ] {
            XCTAssertThrowsError(try parser.parse(canonical + suffix), String(suffix.prefix(100)))
        }
    }

    func testRejectsInvalidJSONAndFields() {
        for json in [
            "null", "[]", "{}", #"{"name":"Taylor"}"#, #"{"name":"Taylor","token":123}"#,
            #"{"name":"  ","token":"123"}"#, #"{"name":"Taylor","token":"\n"}"#,
            #"{"name":"Taylor","n\u0061me":"Other","token":"123"}"#,
            #"{"name":"Taylor","token":"123","token":"456"}"#,
            #"{"name":"Taylor","token":"123",}"#
        ] {
            XCTAssertThrowsError(try parser.parse(canonical + "?data=" + Data(json.utf8).base64EncodedString()), json)
        }

        let invalidUTF8 = Data([0xFF, 0xFE, 0xFD])
        XCTAssertThrowsError(try parser.parse(canonical + "?data=" + invalidUTF8.base64EncodedString()))
    }

    func testAllowsAdditionalJSONFields() throws {
        let json = #"{"name":"Taylor","nested":{"name":"Other","token":1},"token":"123","list":["name","token"]}"#
        XCTAssertEqual(
            try parser.parse(canonical + "?data=" + Data(json.utf8).base64EncodedString()),
            ZelleQRPayload(name: "Taylor", token: "123")
        )
    }
}
