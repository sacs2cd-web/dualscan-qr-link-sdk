import Foundation

/// The fields carried by a QR link. Treat both as unverified input.
public struct ZelleQRPayload: Equatable, Sendable {
    public let name: String
    public let token: String
}

public enum ZelleQRLinkError: LocalizedError, Equatable, Sendable {
    case invalidURL
    case unsupportedURL
    case invalidDataParameter
    case invalidPayload

    public var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Enter a complete QR link."
        case .unsupportedURL:
            return "This QR link has an unsupported address."
        case .invalidDataParameter:
            return "The QR link needs one valid data parameter."
        case .invalidPayload:
            return "The QR payload must contain a name and token."
        }
    }
}

/// Parses the observed HTTPS QR format and optional routes for an integrating app.
public struct ZelleQRLinkParser: Sendable {
    private static let canonicalHost = "enroll.zellepay.com"
    private static let maxURLBytes = 8_192
    private static let maxDataBytes = 4_096

    private let bridgeHost: String?
    private let customScheme: String?

    /// Pass an HTTPS hostname you control and your app's registered scheme to enable extra routes.
    /// Invalid optional values leave the corresponding route disabled.
    public init(bridgeHost: String? = nil, customScheme: String? = nil) {
        self.bridgeHost = bridgeHost.flatMap(Self.validBridgeHost)
        self.customScheme = customScheme.flatMap(Self.validCustomScheme)
    }

    public func parse(_ url: URL) throws -> ZelleQRPayload {
        try parse(url.absoluteString)
    }

    public func parse(_ rawLink: String) throws -> ZelleQRPayload {
        guard rawLink.utf8.count <= Self.maxURLBytes,
              rawLink.utf8.allSatisfy({ (0x21...0x7E).contains($0) }),
              !rawLink.contains("#"),
              let schemeSeparator = rawLink.range(of: "://")
        else {
            throw ZelleQRLinkError.invalidURL
        }

        let scheme = rawLink[..<schemeSeparator.lowerBound].lowercased()
        let address = rawLink[schemeSeparator.upperBound...]
        let addressParts = address.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        guard let route = addressParts.first,
              let pathStart = route.firstIndex(of: "/")
        else {
            throw ZelleQRLinkError.unsupportedURL
        }

        let host = route[..<pathStart].lowercased()
        let path = route[pathStart...]
        let isHTTPS = scheme == "https" && (host == Self.canonicalHost || host == bridgeHost)
        let isCustom = scheme == customScheme && host == "qr"
        guard (isHTTPS || isCustom), path == "/qr-codes" || path == "/qr-codes/" else {
            throw ZelleQRLinkError.unsupportedURL
        }

        guard addressParts.count == 2 else {
            throw ZelleQRLinkError.invalidDataParameter
        }
        let data = try Self.decodeBase64(Self.dataParameter(from: addressParts[1]))
        guard let json = String(data: data, encoding: .utf8),
              let payload = try? JSONDecoder().decode(PayloadFields.self, from: data),
              Self.hasUniquePayloadKeys(in: json),
              !payload.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !payload.token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            throw ZelleQRLinkError.invalidPayload
        }

        return ZelleQRPayload(name: payload.name, token: payload.token)
    }

    private static func validBridgeHost(_ host: String) -> String? {
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard host.utf8.count <= 253,
              !labels.isEmpty,
              labels.allSatisfy({ label in
                  (1...63).contains(label.utf8.count)
                      && label.first != "-"
                      && label.last != "-"
                      && label.utf8.allSatisfy({ isASCIILetter($0) || (0x30...0x39).contains($0) || $0 == 0x2D })
              })
        else { return nil }
        return host.lowercased()
    }

    private static func validCustomScheme(_ scheme: String) -> String? {
        guard let first = scheme.utf8.first,
              isASCIILetter(first),
              scheme.utf8.dropFirst().allSatisfy({
                  isASCIILetter($0) || (0x30...0x39).contains($0) || [0x2B, 0x2D, 0x2E].contains($0)
              })
        else { return nil }
        let lowercase = scheme.lowercased()
        return lowercase == "http" || lowercase == "https" ? nil : lowercase
    }

    private static func isASCIILetter(_ byte: UInt8) -> Bool {
        (0x41...0x5A).contains(byte) || (0x61...0x7A).contains(byte)
    }

    private static func dataParameter(from query: Substring) throws -> String {
        var data: String?
        for pair in query.split(separator: "&", omittingEmptySubsequences: false) {
            let parts = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard let key = parts.first?.removingPercentEncoding else {
                throw ZelleQRLinkError.invalidDataParameter
            }
            guard parts.count == 2, let value = parts[1].removingPercentEncoding else {
                if key != "data", parts.count == 1 { continue }
                throw ZelleQRLinkError.invalidDataParameter
            }
            if key == "data" {
                guard data == nil else { throw ZelleQRLinkError.invalidDataParameter }
                // A literal plus is part of a standard Base64 value.
                data = value
            }
        }
        guard let data, !data.isEmpty, data.utf8.count <= maxDataBytes else {
            throw ZelleQRLinkError.invalidDataParameter
        }
        return data
    }

    private static func decodeBase64(_ encoded: String) throws -> Data {
        let body = encoded.prefix { $0 != "=" }
        let padding = encoded.dropFirst(body.count)
        let requiredPadding = (4 - body.count % 4) % 4
        let hasStandardAlphabet = body.contains("+") || body.contains("/")
        let hasURLAlphabet = body.contains("-") || body.contains("_")
        guard requiredPadding != 3,
              padding.isEmpty || (padding.count == requiredPadding && padding.allSatisfy { $0 == "=" }),
              !(hasStandardAlphabet && hasURLAlphabet),
              body.utf8.allSatisfy({ byte in
                  switch byte {
                  case 0x41...0x5A, 0x61...0x7A, 0x30...0x39, 0x2B, 0x2F, 0x2D, 0x5F: return true
                  default: return false
                  }
              })
        else {
            throw ZelleQRLinkError.invalidDataParameter
        }

        let normalized = body.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
            + String(repeating: "=", count: requiredPadding)
        guard let data = Data(base64Encoded: normalized), data.base64EncodedString() == normalized else {
            throw ZelleQRLinkError.invalidDataParameter
        }
        return data
    }

    // Foundation accepts duplicate object keys. Walk the decoded JSON to rule out
    // ambiguous top-level fields, including escaped spellings of the same key.
    private static func hasUniquePayloadKeys(in json: String) -> Bool {
        let bytes = Array(json.utf8)
        var depth = 0
        var position = 0
        var previousToken: UInt8?
        var fields = Set<String>()
        while position < bytes.count {
            let byte = bytes[position]
            switch byte {
            case 0x22:
                let start = position
                position += 1
                while position < bytes.count, bytes[position] != 0x22 {
                    position += bytes[position] == 0x5C ? 2 : 1
                }
                guard position < bytes.count else { return false }
                if depth == 1, previousToken == 0x7B || previousToken == 0x2C,
                   let key = try? JSONDecoder().decode(String.self, from: Data(bytes[start...position])),
                   key == "name" || key == "token",
                   !fields.insert(key).inserted {
                    return false
                }
            case 0x7B, 0x5B:
                depth += 1
            case 0x7D, 0x5D:
                if previousToken == 0x2C { return false }
                depth -= 1
            default:
                break
            }
            if byte != 0x20 && byte != 0x09 && byte != 0x0A && byte != 0x0D {
                previousToken = byte
            }
            position += 1
        }
        return true
    }

    private struct PayloadFields: Decodable {
        let name: String
        let token: String
    }
}
