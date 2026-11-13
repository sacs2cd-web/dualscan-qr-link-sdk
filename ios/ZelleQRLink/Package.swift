// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ZelleQRLink",
    platforms: [.iOS(.v13), .macOS(.v10_15)],
    products: [.library(name: "ZelleQRLink", targets: ["ZelleQRLink"])],
    targets: [
        .target(name: "ZelleQRLink"),
        .testTarget(name: "ZelleQRLinkTests", dependencies: ["ZelleQRLink"])
    ]
)
