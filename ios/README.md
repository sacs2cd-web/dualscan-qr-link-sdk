# iOS QR Link SDK and sample

The Swift package decodes the observed Zelle QR link format on-device. The sample runs on iOS 16 or later and uses one SwiftUI URL callback for Universal Links and the registered custom scheme.

## Run the sample

1. Open `SampleApp/ZelleQRSample.xcodeproj` in Xcode 15 or later. The project includes a shared `ZelleQRSample` scheme and resolves the Swift package from the neighboring `ZelleQRLink` folder.
2. Select a simulator or a connected iPhone. For an iPhone, select your team in the sample target’s **Signing & Capabilities** tab. Set a unique bundle identifier if required.
3. Run the app. Paste an HTTPS QR link and tap **Decode link**, or use **Scan QR code** on an iPhone or iPad with A12 Bionic or later and camera access. The result shows the name and offers a control to reveal the token.

To test an OS-delivered link on a booted simulator, install the app from Xcode and run this command with the sample’s default scheme. Repeat with the app open to test delivery to the running app.

```sh
xcrun simctl openurl booted 'dualscan-zelleqr://qr/qr-codes?data=ewogICJuYW1lIiA6ICJUYXlsb3IiLAogICJ0b2tlbiIgOiAiMTIwMjU1NTAxMjMiCn0%3D'
```

## Set up a Universal Link for device Camera

1. Use an HTTPS hostname you control. Set `BRIDGE_HOST` in `SampleApp/SampleSettings.xcconfig` to that hostname. Use the same hostname for the QR link and the website association.
2. Enable Associated Domains for the signing App ID. Uncomment `CODE_SIGN_ENTITLEMENTS` in `SampleSettings.xcconfig`. The supplied entitlements file expands `BRIDGE_HOST` into the app’s associated domain.
3. Publish the [AASA template](../web/.well-known/apple-app-site-association) at `/.well-known/apple-app-site-association` on the bridge host. Replace the App ID placeholder with your signed app’s application identifier, usually `TEAMID.BUNDLEID`. Serve the file over HTTPS as JSON and directly at that URL.
4. Reinstall the signed app after deployment. On a physical device, scan a QR whose text is `https://YOUR_BRIDGE_HOST/qr-codes?data=...` with Camera and tap its link. You can also test by tapping the link from Notes. A same-host link tapped in Safari generally stays in Safari. [Apple’s Universal Link setup](https://developer.apple.com/documentation/xcode/supporting-associated-domains) and [test guide](https://developer.apple.com/documentation/technotes/tn3155-debugging-universal-links) cover the association and cache behavior.

A QR that already encodes `https://enroll.zellepay.com/qr-codes?data=...` can be read using the sample’s scanner or paste field. To launch an SDK-enabled app directly from a default scanner using that exact host, the operator of `enroll.zellepay.com` must publish an association for the signed app. A registered custom scheme can also be used by a scanner or browser that allows it.

## Integrate the SDK

Add `ZelleQRLink` as a local package and select the library product of the same name. Pass an incoming URL from SwiftUI’s `onOpenURL`, or the raw link string from your scanner, to the same parser. Configure an optional bridge hostname or registered scheme only when your app uses those routes.

```swift
import SwiftUI
import ZelleQRLink

struct QRReader: View {
    private let parser = ZelleQRLinkParser(
        bridgeHost: "qr.example.com",
        customScheme: "dualscan-zelleqr"
    )
    @State private var status = "Open a QR link"

    var body: some View {
        Text(status)
            .onOpenURL { url in
                do {
                    let payload = try parser.parse(url)
                    status = payload.name
                } catch {
                    status = error.localizedDescription
                }
            }
    }
}
```

`ZelleQRPayload` exposes the unverified `name` and `token` fields as strings. Run the parser tests with `swift test` from the `ios/ZelleQRLink` folder on a Mac with full Xcode selected.
