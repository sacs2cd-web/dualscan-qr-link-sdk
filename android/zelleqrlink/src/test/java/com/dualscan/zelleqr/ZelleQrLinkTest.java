package com.dualscan.zelleqr;

import static com.dualscan.zelleqr.ZelleQrLink.Reason.DUPLICATE_DATA;
import static com.dualscan.zelleqr.ZelleQrLink.Reason.INVALID_BASE64;
import static com.dualscan.zelleqr.ZelleQrLink.Reason.INVALID_PAYLOAD;
import static com.dualscan.zelleqr.ZelleQrLink.Reason.INVALID_URL;
import static com.dualscan.zelleqr.ZelleQrLink.Reason.MISSING_DATA;
import static com.dualscan.zelleqr.ZelleQrLink.Reason.UNSUPPORTED_LINK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;

public final class ZelleQrLinkTest {
    private static final String ENROLL = "https://enroll.zellepay.com/qr-codes";
    private static final String QUERY = "?data=";
    private static final String DEMO = "eyJuYW1lIjoiVGF5bG9yIiwidG9rZW4iOiIxMjAyNTU1MDEyMyJ9";
    private static final String STANDARD = ENROLL + QUERY + DEMO;
    private final ZelleQrLink parser = new ZelleQrLink();

    @Test
    public void readsDemoAndOriginalQrJsonShape() throws Exception {
        assertRecipient(parser.parse(STANDARD), "Taylor", "12025550123");
        assertRecipient(parser.parse(ENROLL + "/" + QUERY + encode("{\n  \"name\" : \"Taylor\",\n  \"token\" : \"12025550123\"\n}")),
                "Taylor", "12025550123");
        assertRecipient(parser.parse("HTTPS://ENROLL.ZELLEPAY.COM/qr-codes?track=1&d%61ta=" + DEMO),
                "Taylor", "12025550123");
    }

    @Test
    public void parsesConfiguredBridgeAndOptInDemoScheme() throws Exception {
        String bridge = "https://qr.example.com/qr-codes/" + QUERY + DEMO;
        String custom = "dualscan-zelleqr://qr/qr-codes" + QUERY + DEMO;
        assertRejected(UNSUPPORTED_LINK, bridge);
        assertRejected(UNSUPPORTED_LINK, custom);
        assertRecipient(new ZelleQrLink("QR.Example.Com").parse(bridge), "Taylor", "12025550123");
        assertRecipient(new ZelleQrLink("qr.example.com", true).parse(custom), "Taylor", "12025550123");
        assertThrows(IllegalArgumentException.class, () -> new ZelleQrLink("https://qr.example.com"));
    }

    @Test
    public void preservesUtf8OpaqueFieldsAndStandardBase64Plus() throws Exception {
        String json = "{\"name\":\" A🧾 \" ,\"token\":\" user@example.test \",\"extra\":[42,null]}";
        String encoded = encode(json);
        assertTrue(encoded.contains("+") || encoded.contains("/"));
        assertRecipient(parser.parse(ENROLL + QUERY + encoded), " A🧾 ", " user@example.test ");
        assertRecipient(parser.parse(ENROLL + QUERY + encoded.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")),
                " A🧾 ", " user@example.test ");
        String base64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        assertRecipient(parser.parse(ENROLL + QUERY + base64Url), " A🧾 ", " user@example.test ");
    }

    @Test
    public void rejectsUnexpectedSchemesHostsPathsAndFragments() {
        assertRejected(UNSUPPORTED_LINK, "http://enroll.zellepay.com/qr-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, "https://enroll.zellepay.com.evil.test/qr-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, "https://me@enroll.zellepay.com/qr-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, "https://enroll.zellepay.com:443/qr-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, "https://enroll.zellepay.com:/qr-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, "https://enroll%2ezellepay.com/qr-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, "https://enroll.zellepay.com/%71r-codes" + QUERY + DEMO);
        assertRejected(UNSUPPORTED_LINK, STANDARD + "#");
    }

    @Test
    public void rejectsInvalidOrDuplicateDataQuery() {
        assertRejected(MISSING_DATA, ENROLL);
        assertRejected(MISSING_DATA, ENROLL + "?data");
        assertRejected(DUPLICATE_DATA, STANDARD + "&d%61ta=");
        assertRejected(INVALID_URL, STANDARD + "&ignored=%FE");
        assertRejected(INVALID_URL, STANDARD + "&ignored=é");
        assertRejected(INVALID_URL, ENROLL + QUERY + "%GG");
        assertRejected(INVALID_BASE64, ENROLL + QUERY + "%252B");
        assertRejected(INVALID_BASE64, ENROLL + QUERY + "A+_B");
        assertRejected(INVALID_BASE64, ENROLL + QUERY + "AAB=");
        assertRejected(INVALID_BASE64, ENROLL + QUERY + "TWE==");
        assertRejected(INVALID_BASE64, ENROLL + QUERY + "A".repeat(4097));
        assertRejected(INVALID_URL, STANDARD + "&extra=" + "a".repeat(8192));
    }

    @Test
    public void rejectsInvalidJsonUtf8AndFieldTypes() {
        assertRejected(INVALID_PAYLOAD, ENROLL + QUERY + "/w==");
        for (String malformed : new String[]{
                "[]", "{name:'Taylor',token:'abc'}", "{\"name\":\"Taylor\",\"token\":42}",
                "{\"name\":\"Taylor\",\"token\":null}", "{\"name\":\"Taylor\"}",
                "{\"name\":\"Taylor\",\"name\":\"Morgan\",\"token\":\"abc\"}",
                "{\"name\":\"Taylor\",\"token\":\"abc\",\"tok\\u0065n\":\"def\"}",
                "{\"name\":\"\\uD800\",\"token\":\"abc\"}",
                "{\"name\":\"  \",\"token\":\"abc\"}", "{\"name\":\"Taylor\",\"token\":\"abc\"} false",
                "{\"name\":\"bad\nname\",\"token\":\"abc\"}"}) {
            assertRejected(INVALID_PAYLOAD, ENROLL + QUERY + encode(malformed));
        }
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(ZelleQrLink.Reason reason, String link) {
        assertEquals(reason, assertThrows(ZelleQrLink.InvalidLinkException.class, () -> parser.parse(link)).getReason());
    }

    private static void assertRecipient(ZelleQrLink.Recipient recipient, String name, String token) {
        assertEquals(name, recipient.getName());
        assertEquals(token, recipient.getToken());
    }
}
