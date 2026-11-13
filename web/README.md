# Browser QR integration

From the SDK root, run `python3 -m http.server 8080 --directory web` and open [the browser demo](http://localhost:8080/). Generate a QR code on the computer and scan the display using a phone. The browser generates QR images locally. The bundled [qrcode-generator 2.0.4](https://github.com/kazuhikoarase/qrcode-generator) is distributed under the [MIT license](vendor/LICENSE).

- Choose **Original Zelle URL** to create an `https://enroll.zellepay.com/qr-codes?data=…` QR. The sample apps can decode it when given the link. The host owner controls whether scanning it can launch an app.
- Choose **Your verified app link host** and enter your deployed hostname to create a QR that can launch your installed and associated SDK-enabled app.
- **Open sample app** uses the sample custom scheme with a synthetic recipient. A scanner may choose to display or ignore a custom scheme, so use the button to test the sample handoff from a browser.
- The [canonical](fixtures/canonical.png) and [placeholder bridge](fixtures/bridge-placeholder.png) PNGs contain the synthetic fixture documented in [links.json](fixtures/links.json). Generate a new bridge QR for your actual host.

## Use the URL helper

Place `zelle-qr-link.mjs` on your browser origin and import it from a module script. Feed the result to a QR generator on the page.

```js
import { createZelleQRLink, readZelleQRLink } from './zelle-qr-link.mjs';

const recipient = { name: 'Taylor', token: '12025550123' };
const canonicalURL = createZelleQRLink(recipient);
const bridgeURL = createZelleQRLink(recipient, { bridgeHost: 'qr.example.com' });

const decoded = readZelleQRLink(bridgeURL, { bridgeHost: 'qr.example.com' });
```

`getZelleQRDestinations(link, options)` provides the decoded fields and URLs for the canonical destination and the sample custom scheme. It carries the validated Base64 data forward, including fields that the SDK did not read. The browser fallback exposes the custom scheme only for the fixed synthetic fixture.

## Deploy on a controlled HTTPS host

1. Serve this directory at the root of a dedicated HTTPS host and configure the same host in your iOS and Android samples.
2. Serve both `/qr-codes` and `/qr-codes/` from `qr-codes/index.html`. An internal rewrite on the host preserves the scanned path and query. The fallback uses the browser's HTTPS host and path as delivered.
3. Set the Apple team ID and bundle ID in `/.well-known/apple-app-site-association` to match your signed app. In `/.well-known/assetlinks.json`, enter the Android package and SHA-256 certificate fingerprint of the signing key actually used for the installed app. For a Play-distributed build with Play App Signing, use the app-signing certificate.
4. Serve both association files directly over HTTPS with `Content-Type: application/json` and a 200 response. Android path scope also comes from the app manifest, especially on versions earlier than Android 15. Keep the QR receiver's path filters scoped to the two supported paths.
5. Serve `.mjs` files as JavaScript. Set `Referrer-Policy: no-referrer` on the fallback and omit the QR query from access logs and analytics where your host allows. The QR payload is readable data. Scan a QR from the demo on a device after installing a signed build and verifying the association.

The fallback offers a **Continue to Zelle** action when the OS keeps the link in the browser. Platform setup details are in the [Apple associated domains guide](https://developer.apple.com/documentation/xcode/supporting-associated-domains) and [Android website association guide](https://developer.android.com/training/app-links/configure-assetlinks).

## Verify

Run `node --test web/zelle-qr-link.test.mjs` from the SDK root. Regenerate the PNGs with `python3 web/fixtures/generate.py` when Python `qrcode[pil]` is installed.
