# Android sample and SDK

Open this folder in Android Studio. The project contains the `app` sample and the reusable `zelleqrlink` library. It uses JDK 17 or 21 and Android SDK Platform 36. The app and library support Android 8.0 (API 26) and later.

```sh
./gradlew :zelleqrlink:testDebugUnitTest :zelleqrlink:assembleRelease :app:assembleDebug
adb install -r ../dist/zelle-qr-sample-debug.apk
```

The sample provides **Scan QR code**, a URL field for pasting, and an Android share target for a single plain-text QR link. The camera button uses Google Code Scanner through Google Play services and may need its scanner module to download on first use. Copy the scanned URL into the sample on devices without that service. The token is masked until you select **Show token**. **Try sample link** loads synthetic recipient data.

## Add the SDK to an app

Copy `../dist/zelle-qr-android-sdk.aar` into the host app's `libs` directory. Add these dependencies to the host app's Gradle file and include `mavenCentral()` in the project's dependency repositories.

```groovy
dependencies {
    implementation files('libs/zelle-qr-android-sdk.aar')
    implementation 'com.google.code.gson:gson:2.11.0'
}
```

For source integration, add the `zelleqrlink` module to the host project's `settings.gradle` and use `implementation project(':zelleqrlink')`. That module already declares Gson.

```java
import com.dualscan.zelleqr.ZelleQrLink;

ZelleQrLink sdk = new ZelleQrLink("qr.your-domain.com");
try {
    ZelleQrLink.Recipient recipient = sdk.parse(scannedUrl);
    nameView.setText(recipient.getName());
    // Use recipient.getToken() when the user requests to see the token.
} catch (ZelleQrLink.InvalidLinkException error) {
    // Map error.getReason() to the host app's invalid-link message.
}
```

The no-argument constructor accepts only the original Zelle-format HTTPS link. Supplying a hostname adds the HTTPS bridge origin. Pass `true` as the second argument to opt into the sample scheme `dualscan-zelleqr://qr/qr-codes`. This fixed scheme is for the sample, and any installed app can register it. Use a verified HTTPS App Link in production.

## Receive a scanned link

Register a browsable `ACTION_VIEW` intent filter for your owned HTTPS host with `android:autoVerify="true"` and a `path` of `/qr-codes`. Add a second `path` if you also serve `/qr-codes/`. Use your own activity's `onCreate` for a cold start and `onNewIntent` with `singleTop` for a new link delivered to the foreground activity. For `ACTION_VIEW`, pass `intent.getDataString()` to the parser. `app/src/main/AndroidManifest.xml` and `MainActivity.java` show the complete handlers, including Android's `ACTION_SEND` text-share input.

Build the sample for your own host and check which signing certificate to list in Digital Asset Links.

```sh
./gradlew -PqrLinkHost=qr.your-domain.com :app:assembleDebug :app:signingReport
```

Publish `/.well-known/assetlinks.json` on that host over HTTPS. List the installed APK's application ID and the SHA-256 fingerprint of its signing certificate. Google Play-distributed builds use the Play app-signing certificate. The default `qr.example.com` is a placeholder. Direct capture of the original `enroll.zellepay.com` HTTPS link requires that domain to publish your app's association. Original-format QR links can be read through **Scan QR code**, Android text sharing, or paste.

The synthetic link below exercises the sample's custom-scheme filter independently of DNS. Test the HTTPS App Link from Camera, a browser on a different origin, or another app after deploying Digital Asset Links. On Android 12 or later `adb shell pm get-app-links com.dualscan.zelleqr.sample` reports the association state.

```sh
adb shell am start -W -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'dualscan-zelleqr://qr/qr-codes?data=eyJuYW1lIjoiVGF5bG9yIiwidG9rZW4iOiIxMjAyNTU1MDEyMyJ9'
```
