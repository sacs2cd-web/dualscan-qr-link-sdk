# Third-party notices

The [Dualscan OS Community & Commercial License](LICENSE) applies to Dualscan LLC's original project materials. The third-party components below retain their own terms and attribution.

| Component | Where used | Terms |
| --- | --- | --- |
| qrcode-generator 2.0.4, Kazuhiko Arase | Vendored browser QR generator | [MIT license and copyright notice](web/vendor/LICENSE) |
| Gradle Wrapper 8.13 | Checked-in build bootstrap and launch scripts | [Apache license and accompanying upstream terms](licenses/Gradle-LICENSE.txt) · [Gradle source](https://github.com/gradle/gradle/tree/v8.13.0) |
| Gson 2.11.0 | Android decoder dependency, also packaged in the debug APK | [Apache License 2.0](licenses/Gson-LICENSE.txt) · [Gson source](https://github.com/google/gson/tree/gson-parent-2.11.0) |
| Google Code Scanner 16.1.0 and Google SDK dependencies | Android sample camera handoff | [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner) · [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms) · [Dependency notices](licenses/android-dependencies/) |

The Android Studio project resolves the Android sample's additional dependencies from Google's Maven repository and Maven Central. Resolve the exact versions for a build with `./gradlew :app:dependencies --configuration debugRuntimeClasspath` from `android/`.

Zelle® is a trademark of Early Warning Services, LLC. The project uses that name to identify the QR URL format. Apple, Android, and Google product names identify the platforms and SDKs used by the sample.
