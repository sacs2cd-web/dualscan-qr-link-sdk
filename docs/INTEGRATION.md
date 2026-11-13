# Dualscan QR Link SDK integration guide

Dualscan QR Link SDK is an independent reference implementation prepared for review by Early Warning Services, LLC. It reads an observed Zelle QR payload for display in an integrating app. Account enrollment, recipient verification, and payments belong in the participating financial institution’s supported flow. Zelle and the Zelle related marks are the property of Early Warning Services, LLC. [Zelle QR guidance](https://www.zelle.com/faq/how-do-i-use-zelle-qr-code) · [Early Warning trademark guidelines](https://www.earlywarning.com/early-warningr-trademark-guidelines-brand-usage-and-licensing)

## Choose a link route

| QR or browser URL | App delivery | Browser or in-app fallback |
| --- | --- | --- |
| `https://enroll.zellepay.com/qr-codes?data=…` | Direct delivery to an integrating app requires the operator of that exact hostname to associate the signed app and the device to route the link to it. Existing QR symbols retain their encoded URL. | Use the sample’s built-in camera scanner, share the recognized URL with the Android sample, or paste the URL into either sample. External navigation follows the original destination. |
| `https://qr.example.com/qr-codes?data=…` | Replace the example hostname with your deployed host. Publish the app associations there and generate a new QR carrying that hostname. This route supports iOS Universal Links and Android App Links. | Serve the included fallback page at both path variants. It offers a deliberate **Continue to Zelle** action. |
| `dualscan-zelleqr://qr/qr-codes?data=…` | Use the browser demo button or platform test command to exercise URL receipt with the installed sample. Browser and scanner support for custom schemes varies. | Use this route with synthetic data. A different app on the device can register the same scheme. |

The browser demo displays a QR image. A phone camera or scanner reads the URL and lets the user open it. The scanner, browser, and operating system decide where to send the link. The SDK runs after the host app receives a URL, share, paste, or scan result. For iOS, use Camera or a tap from Notes when verifying Universal Links. Safari address-bar navigation stays in Safari, and same-host taps generally do too. [Apple Universal Link diagnostics](https://developer.apple.com/documentation/technotes/tn3155-debugging-universal-links) · [Android App Link verification](https://developer.android.com/training/app-links/verify-applinks)

## URI and payload contract

1. Accept an HTTPS URL on the exact original or configured bridge hostname. The samples can opt into the custom scheme at authority `qr`. Scheme and hostname comparisons are case-insensitive. The literal paths are `/qr-codes` and `/qr-codes/`. Ports, credentials, fragments, and other paths fail validation.
2. Read exactly one case-sensitive `data` query field. Percent-decode query fields once and preserve a literal `+`. A form-query decoder may convert a literal plus to a space, so pass the original URL string or URL object to the SDK. Well-formed additional query fields are ignored.
3. Decode standard Base64 or Base64URL with valid padding or omitted padding. The link limit is 8,192 bytes and the percent-decoded Base64 field limit is 4,096 bytes. The two Base64 alphabets must be used independently. Invalid characters, malformed padding, and nonzero padding bits cause a parse failure.
4. Decode the Base64 result as UTF-8 and read the top-level JSON object. `name` and `token` each occur once and must be nonblank strings. Field order and JSON formatting can vary. Additional JSON fields are ignored by the recipient readers. Preserve both strings, including leading zeroes in tokens and whitespace in otherwise nonblank fields.

The [synthetic fixture](../web/fixtures/links.json) uses the JSON below. Use this fixture in screenshots, issues, logs, and CI tests.

```json
{"name":"Taylor","token":"12025550123"}
```

The corresponding standard Base64 value is:

```text
eyJuYW1lIjoiVGF5bG9yIiwidG9rZW4iOiIxMjAyNTU1MDEyMyJ9
```

For a QR generator, serialize the JSON to UTF-8, Base64-encode the bytes, percent-encode that value for the query, and draw a QR symbol for the resulting URL with a clear quiet zone. Use [canonical.png](../web/fixtures/canonical.png) for a local parsing check. For the controlled-host routing check, generate a new QR with your staging hostname. The [placeholder bridge image](../web/fixtures/bridge-placeholder.png) is a visual fixture.

## Integrate the SDK

Use the same parser for each delivery path. Validate before rendering a result and clear the previous result when a new input fails so the prior recipient cannot be mistaken for the current one.

### iOS

Add the local [Swift package](../ios/ZelleQRLink/Package.swift) and link the `ZelleQRLink` product. Use Xcode 15 or later for the sample. The library supports iOS 13 or later, and the sample uses iOS 16 or later. The sample’s VisionKit camera path requires supported hardware and camera access. Paste and incoming URLs use the same decoder.

```swift
import ZelleQRLink

let parser = ZelleQRLinkParser(bridgeHost: "qr.example.com")
let recipient = try parser.parse(incomingURL)
let displayName = recipient.name
```

Pass the `URL` from SwiftUI’s `onOpenURL`, or pass a raw string received from your scanner to the same `parse` overload. For demo handoff, pass `customScheme: "dualscan-zelleqr"` and register the scheme in the app’s Info.plist. Use `ZelleQRLinkError` for generic parse failures. [iOS setup and full SwiftUI example](../ios/README.md)

### Android

Use the [Gradle library module](../android/zelleqrlink/) or the release AAR together with Gson 2.11.0. Both the library and the sample support Android API 26 or later. The sample scanner uses Google Play services, and it can require a first-use scanner module download. Provide a paste option on devices without the scanner service.

```java
import com.dualscan.zelleqr.ZelleQrLink;

ZelleQrLink parser = new ZelleQrLink("qr.example.com");
ZelleQrLink.Recipient recipient = parser.parse(intent.getDataString());
String displayName = recipient.getName();
```

For an `ACTION_VIEW` intent, call the parser with the data string from both `onCreate` and `onNewIntent`. The sample uses `singleTop` and also accepts a single `text/plain` share. Read `InvalidLinkException.getReason()` to map parser errors to your UI. Use the second constructor argument `true` only for the fixed sample scheme. [Android setup and delivery handlers](../android/README.md)

### Browser

Import the local ES module and pass the resulting link to the QR renderer. The provided demo uses the included QR generator in the browser.

```js
import { createZelleQRLink, readZelleQRLink } from './zelle-qr-link.mjs';

const options = { bridgeHost: 'qr.example.com' };
const link = createZelleQRLink({ name: 'Taylor', token: '12025550123' }, options);
const recipient = readZelleQRLink(link, options);
```

Omit `bridgeHost` to produce the original HTTPS format. On the bridge fallback, `getZelleQRDestinations(link, options)` validates the input and returns the recipient fields, canonical HTTPS URL, and sample app URL. The included fallback uses a fixed destination host and exposes the custom scheme for its synthetic fixture. [Browser deployment and fixtures](../web/README.md)

## Deploy a verified bridge

1. Pick a public staging HTTPS hostname and a matching pair of staging app identities. Set the iOS `BRIDGE_HOST` build setting and Android `-PqrLinkHost` value to the bare hostname. Confirm both signed apps embed the selected host.
2. On iOS, enable Associated Domains for the signed app and configure `applinks:YOUR_HOST`. Update the [Apple association file](../web/.well-known/apple-app-site-association) to use the signed `application-identifier`. Keep both exact QR paths in the file. Inspect a signed iOS build’s entitlements with `codesign -d --entitlements :- /path/to/ZelleQRSample.app`.
3. On Android, keep `android:autoVerify="true"` on the HTTPS receiver and check the installed package and signing certificate. Update the [Digital Asset Links file](../web/.well-known/assetlinks.json) with that package and the certificate’s SHA-256 fingerprint. Use the Play app-signing fingerprint for Play-distributed builds. Keep both exact QR paths in the manifest for platforms using static rules.
4. Publish the association files and browser assets. Serve both association responses publicly over HTTPS with status 200, JSON content type, and no redirects or authentication. Use an internal rewrite for both `/qr-codes` variants to the fallback page and preserve the query. Serve `.mjs` assets with a JavaScript MIME type.
5. Verify actual server responses, signed builds, and browser fallback with the fixture before distributing a QR. A link that opens from a custom scheme command proves delivery to the parser. Host verification requires a separate HTTPS test.

```sh
QR_LINK_HOST=qr.example.com  # Replace with your deployed staging hostname.
curl -sS -i "https://$QR_LINK_HOST/.well-known/apple-app-site-association"
curl -sS -i "https://$QR_LINK_HOST/.well-known/assetlinks.json"

QR_APP_PACKAGE=com.dualscan.zelleqr.sample
adb shell pm verify-app-links --re-verify "$QR_APP_PACKAGE"
adb shell pm get-app-links "$QR_APP_PACKAGE"
```

Run the Android verification commands on API 31 or later, then allow verification to complete before checking for `verified` next to your host. For iOS, reinstall the signed build after publishing and test a Notes long press or use Universal Links diagnostics under Developer settings. Apple and Android cache association state. [Apple associated domains](https://developer.apple.com/documentation/xcode/supporting-associated-domains) · [Android website associations](https://developer.android.com/training/app-links/configure-assetlinks)

## Verify a release candidate

Run `swift test` in `ios/ZelleQRLink` with full Xcode selected. Run `./gradlew :zelleqrlink:testDebugUnitTest` in `android` and `node --test web/zelle-qr-link.test.mjs` from the repository root. Use a signed staging build and physical devices to exercise camera behavior.

| Scenario | Pass condition |
| --- | --- |
| Verified HTTPS QR in the default camera and a supported scanner app | Tapping the result routes into the sample. Test both path variants and repeat with the app closed and already open. |
| Original HTTPS QR | In-app scan or paste reads the fixture. Default scanner navigation follows the original destination unless the domain operator has configured app delivery. |
| App absent or browser retains navigation | The deployed fallback loads with its intended action. Uninstall or disable the sample and retest with the same staging QR. |
| Synthetic custom scheme | Use the browser demo button and the simulator or ADB commands in the platform READMEs. Confirm Taylor appears and **Show token** reveals `12025550123`. |
| Invalid input and recovery | Try a duplicate `data` field, wrong host, malformed Base64, non-string token, and an oversized URL. The app shows a generic error and accepts a subsequent valid fixture. |
| Camera unavailable or permission denied | The sample presents a recoverable error or guidance. Paste the recognized URL to verify the parser remains usable. |

For failed app launches, compare the signed app identity with the live association document, check exact scheme, host, and path, and confirm the device’s link handling preference. Use an unrestricted Android `ACTION_VIEW` test without a forced package to check OS routing. When a tap works and the QR scan fails, check the QR’s quiet zone and try a clear printout or a second display.

Record test OS, scanner, build, and route in the release record. If a deployment breaks handoff, pause new QR distribution, restore the last known-good association and fallback files, and verify fresh installs and existing devices. Keep the web path available during the association cache refresh.

## Data handling

Treat the decoded name and token as unverified input. Base64 is reversible encoding and a verified domain associates an app with a host. Recipient validation and authorization require the institution’s supported flow.

Use the parser on-device. Keep raw URLs, `data`, names, and tokens out of analytics, application logs, exception context, crash reports, and support attachments. Review access logging for the host, CDN, and reverse proxy because the query reaches those layers when the browser loads the fallback. Use `Referrer-Policy: no-referrer` on the fallback and exclude QR query strings from telemetry and access logs. Present decoded fields as text and use generic parser error messages. The samples mask the token by default and allow the user to reveal it.

Use only the synthetic fixture for custom-scheme tests and published evidence. Deploy production app links under a host with controlled TLS and signed mobile identities.
