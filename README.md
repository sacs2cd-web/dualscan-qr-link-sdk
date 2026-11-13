# Dualscan QR Link SDK

Dualscan QR Link SDK is a reference implementation for reading the observed Zelle® QR code URL format in an iOS or Android app. The SDK extracts the encoded recipient name and token locally. The sample apps demonstrate link delivery, camera scanning where supported, and pasting a URL.

**For Early Warning Services, LLC reviewers.** Start with the [deployment guide (PDF)](docs/Dualscan-QR-Link-SDK-Deployment-Guide.pdf) or [editable Word copy](docs/Dualscan-QR-Link-SDK-Deployment-Guide.docx). Use the synthetic fixture to evaluate the parser and app handoff. A pilot on an existing Zelle® domain requires the domain operator to confirm the receiving app identities and publish the associations.

This is an independent Dualscan LLC compatibility sample. It has no affiliation with or endorsement from Early Warning Services, LLC or the U.S. Department of the Treasury. Zelle® is a trademark of Early Warning Services, LLC. References identify the URL format and service being evaluated. For payments and enrollment, use a participating financial institution's supported flow. [Zelle® QR code guidance](https://www.zelle.com/faq/how-do-i-use-zelle-qr-code)

## What's included

| Component | Where to start |
| --- | --- |
| iOS SDK and SwiftUI sample | [iOS instructions](ios/README.md) · [Swift package](ios/ZelleQRLink/) · [Xcode project](ios/SampleApp/ZelleQRSample.xcodeproj) |
| Android SDK and Android Studio sample | [Android instructions](android/README.md) · [Java SDK source](android/zelleqrlink/) · [sample app](android/app/) |
| Android binaries | [Installable debug APK](dist/zelle-qr-sample-debug.apk) · [Release library AAR](dist/zelle-qr-android-sdk.aar) |
| Web generator, fixtures and fallback page | [Browser instructions](web/README.md) · [Association templates](web/.well-known/) · [Sample fixtures](web/fixtures/) |
| Integration and operations | [Integration reference](docs/INTEGRATION.md) · [Deployment guide (PDF)](docs/Dualscan-QR-Link-SDK-Deployment-Guide.pdf) · [Word copy](docs/Dualscan-QR-Link-SDK-Deployment-Guide.docx) |
| Complete download | [Source, builds, and documentation (ZIP)](dist/dualscan-qr-link-sdk.zip) |

## How it works

1. The browser demo encodes a recipient as UTF-8 JSON, Base64-encodes the bytes and URL-encodes the result in the `data` query parameter. It renders the QR locally.
2. A mobile camera or scanner reads the URL. The operating system can route a verified HTTPS URL to an installed app. The app's scanner and paste field also accept the URL directly.
3. The SDK checks the URL route and payload and returns the name and opaque token. The sample masks the token until the user chooses to reveal it. A decoded payload represents what was in the QR code. Recipient identity and enrollment require confirmation through the supported financial institution flow.

| QR destination | Behavior |
| --- | --- |
| `https://enroll.zellepay.com/qr-codes?data=…` | The SDK can parse it when given the URL. Direct camera-to-app routing for this host requires an association published by its operator for the signed app. |
| `https://YOUR_HOST/qr-codes?data=…` | Use a controlled HTTPS host and publish the app associations for new pilot codes. A scanner or browser that keeps navigation on the web gets the fallback page. |
| `dualscan-zelleqr://qr/qr-codes?data=…` | Test app delivery from the demo's **Open sample app** button with synthetic data. Custom-scheme handling varies by scanner and browser. |

## Try it locally

1. **Android.** Install the debug APK on a test device using `adb install -r dist/zelle-qr-sample-debug.apk`, or open `android/` in Android Studio. Build with JDK 17 or 21 and Android SDK 36. The sample runs on API 26 or later.
2. **iOS.** Open `ios/SampleApp/ZelleQRSample.xcodeproj` in Xcode 15 or later and run the `ZelleQRSample` scheme. Choose your development team for a physical device. The sample runs on iOS 16 or later.
3. **Browser.** Run `python3 -m http.server 8080 --directory web` from the project root and open [the local demo](http://localhost:8080/). Display the QR on a second screen or copy the generated URL into the app. The synthetic result is Taylor with token `12025550123`. Tap **Show token** to see it.
4. **Direct app handoff.** Open the demo on the test device from a reachable host and tap **Open sample app**. Repeat with the app closed and while it is running. To test system routing for HTTPS, deploy the domain association files and repeat from Camera or a separate app.

The in-app iOS scanner uses VisionKit on supported hardware. Android uses Google Code Scanner through Google Play services and may need its scanner module. Both samples accept a pasted URL. Android also accepts a shared plain-text URL.

## Enable verified HTTPS routing

- **iOS.** Set `BRIDGE_HOST` in `ios/SampleApp/SampleSettings.xcconfig`, enable the supplied Associated Domains entitlement, and sign with your intended App ID. Publish the Apple association template with the signed application identifier.
- **Android.** Build with `-PqrLinkHost=YOUR_HOST` and publish Digital Asset Links with the intended package and the SHA-256 fingerprint of the installed build's signing certificate. For Play App Signing, use the app-signing certificate.
- **Web.** Serve both `/.well-known/` files over HTTPS with HTTP 200, JSON content type, and no redirects. Route `/qr-codes` and `/qr-codes/` to the fallback while preserving the query. Generate pilot QR codes for this host. Safari can retain same-domain navigation in the browser, so test from Camera and from a separate app.

See the [integration reference](docs/INTEGRATION.md) for the URI contract, source integration, deployment checks, and test plan. [Apple associated domains](https://developer.apple.com/documentation/xcode/supporting-associated-domains) · [Android App Links verification](https://developer.android.com/training/app-links/verify-applinks)

## Verification status

The supplied URL decoded in Swift, Java, and browser JavaScript. Android tests and lint passed. A Pixel API 34 emulator verified cold and warm custom-link delivery, text sharing, package-scoped HTTPS handling, and paste. The browser checks covered static and generated QR codes plus the fallback page. The Swift package built on macOS and its decoder passed a compiled smoke test. Full Xcode build, physical-camera scans, and verified routing with deployed domain associations remain to be tested.

## License

Dualscan LLC distributes its original project materials under the [Dualscan OS Community & Commercial License v1.0](LICENSE). This repository contains a standalone SDK and samples. For distribution as part of a Dualscan OS image, consult the license's OEM component conditions and the [Core OEM Component Manifest](https://github.com/sacs2cd-web/dualscan-os/blob/main/CORE_OEM_COMPONENTS.md). [Third-party notices](THIRD_PARTY_NOTICES.md) identify components governed by their own terms.
