package com.dualscan.zelleqr;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/** Reads recipient data from a recognized QR link locally. */
public final class ZelleQrLink {
    public static final int MAX_LINK_BYTES = 8192;
    private static final int MAX_DATA_BYTES = 4096;
    private static final String ORIGINAL_HOST = "enroll.zellepay.com";
    private static final String DEMO_SCHEME = "dualscan-zelleqr";
    private static final Pattern DNS_HOST = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/_-]+={0,2}");

    private final String bridgeHost;
    private final boolean allowDemoScheme;

    /** Accepts the original HTTPS QR link. */
    public ZelleQrLink() {
        this(null, false);
    }

    /** Also accepts a configured HTTPS bridge hostname. */
    public ZelleQrLink(String bridgeHost) {
        this(bridgeHost, false);
    }

    /** Also accepts the sample app's custom scheme when explicitly enabled. */
    public ZelleQrLink(String bridgeHost, boolean allowDemoScheme) {
        this.bridgeHost = bridgeHost == null ? null : bridgeHost.toLowerCase(Locale.ROOT);
        if (this.bridgeHost != null
                && (this.bridgeHost.length() > 253 || !DNS_HOST.matcher(this.bridgeHost).matches())) {
            throw new IllegalArgumentException("Provide a DNS hostname without a scheme, port, or path.");
        }
        this.allowDemoScheme = allowDemoScheme;
    }

    /** Parses a scanner result, incoming intent data string, or pasted link. */
    public Recipient parse(String link) throws InvalidLinkException {
        if (link == null || link.length() > MAX_LINK_BYTES
                || link.chars().anyMatch(character -> character < 0x21 || character > 0x7E)) {
            throw invalid(Reason.INVALID_URL);
        }
        URI uri;
        try {
            uri = new URI(link);
        } catch (URISyntaxException e) {
            throw invalid(Reason.INVALID_URL);
        }
        String path = uri.getRawPath();
        if (uri.getRawFragment() != null || !isAllowedOrigin(uri)
                || !("/qr-codes".equals(path) || "/qr-codes/".equals(path))) {
            throw invalid(Reason.UNSUPPORTED_LINK);
        }
        return readRecipient(decodePayload(readData(uri.getRawQuery())));
    }

    private boolean isAllowedOrigin(URI uri) {
        String authority = uri.getRawAuthority();
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return ORIGINAL_HOST.equalsIgnoreCase(authority)
                    || (bridgeHost != null && bridgeHost.equalsIgnoreCase(authority));
        }
        return allowDemoScheme && DEMO_SCHEME.equalsIgnoreCase(uri.getScheme())
                && "qr".equalsIgnoreCase(authority);
    }

    private static String readData(String query) throws InvalidLinkException {
        if (query == null) {
            throw invalid(Reason.MISSING_DATA);
        }
        String data = null;
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String name = separator < 0 ? parameter : parameter.substring(0, separator);
            String key = decodeQueryPart(name);
            String value = separator < 0 ? "" : decodeQueryPart(parameter.substring(separator + 1));
            if ("data".equals(key)) {
                if (data != null) {
                    throw invalid(Reason.DUPLICATE_DATA);
                }
                data = value;
            }
        }
        if (data == null || data.isEmpty()) {
            throw invalid(Reason.MISSING_DATA);
        }
        return data;
    }

    private static String decodeQueryPart(String part) throws InvalidLinkException {
        // URI already validates percent syntax. Decode byte runs as UTF-8 and leave plus unchanged.
        StringBuilder result = new StringBuilder(part.length());
        int offset = 0;
        while (offset < part.length()) {
            if (part.charAt(offset) != '%') {
                result.append(part.charAt(offset++));
                continue;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            do {
                int high = Character.digit(part.charAt(offset + 1), 16);
                int low = Character.digit(part.charAt(offset + 2), 16);
                bytes.write((high << 4) | low);
                offset += 3;
            } while (offset < part.length() && part.charAt(offset) == '%');
            try {
                result.append(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())));
            } catch (CharacterCodingException e) {
                throw invalid(Reason.INVALID_URL);
            }
        }
        return result.toString();
    }

    private static String decodePayload(String data) throws InvalidLinkException {
        boolean standard = data.indexOf('+') >= 0 || data.indexOf('/') >= 0;
        boolean urlSafe = data.indexOf('-') >= 0 || data.indexOf('_') >= 0;
        if (data.length() > MAX_DATA_BYTES || (standard && urlSafe) || !BASE64.matcher(data).matches()) {
            throw invalid(Reason.INVALID_BASE64);
        }
        try {
            Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder();
            Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
            byte[] bytes = decoder.decode(data);
            if (data.indexOf('=') < 0) {
                encoder = encoder.withoutPadding();
            }
            if (!encoder.encodeToString(bytes).equals(data)) {
                throw invalid(Reason.INVALID_BASE64);
            }
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (IllegalArgumentException e) {
            throw invalid(Reason.INVALID_BASE64);
        } catch (CharacterCodingException e) {
            throw invalid(Reason.INVALID_PAYLOAD);
        }
    }

    private static Recipient readRecipient(String json) throws InvalidLinkException {
        String name = null;
        String token = null;
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "name":
                        name = readField(reader, name);
                        break;
                    case "token":
                        token = readField(reader, token);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || name == null || token == null) {
                throw invalid(Reason.INVALID_PAYLOAD);
            }
        } catch (IOException | IllegalStateException e) {
            throw invalid(Reason.INVALID_PAYLOAD);
        }
        return new Recipient(name, token);
    }

    private static String readField(JsonReader reader, String previous)
            throws IOException, InvalidLinkException {
        if (previous != null || reader.peek() != JsonToken.STRING) {
            throw invalid(Reason.INVALID_PAYLOAD);
        }
        String value = reader.nextString();
        if (value.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint) || codePoint == 0x85)
                || value.codePoints().anyMatch(codePoint -> codePoint >= Character.MIN_SURROGATE
                && codePoint <= Character.MAX_SURROGATE)) {
            throw invalid(Reason.INVALID_PAYLOAD);
        }
        return value;
    }

    private static InvalidLinkException invalid(Reason reason) {
        return new InvalidLinkException(reason);
    }

    /** Unverified text decoded from the link. The token is returned unchanged. */
    public static final class Recipient {
        private final String name;
        private final String token;

        private Recipient(String name, String token) {
            this.name = name;
            this.token = token;
        }

        public String getName() {
            return name;
        }

        public String getToken() {
            return token;
        }
    }

    public enum Reason {
        INVALID_URL,
        UNSUPPORTED_LINK,
        MISSING_DATA,
        DUPLICATE_DATA,
        INVALID_BASE64,
        INVALID_PAYLOAD
    }

    public static final class InvalidLinkException extends Exception {
        private final Reason reason;

        private InvalidLinkException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }
}
