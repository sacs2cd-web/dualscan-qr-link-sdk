import SwiftUI
import UIKit
import Vision
import VisionKit

@MainActor
struct QRCodeScanner: UIViewControllerRepresentable {
    let onLink: (String) -> Void
    let onUnavailable: () -> Void

    func makeUIViewController(context: Context) -> ScannerController {
        ScannerController(onLink: onLink, onUnavailable: onUnavailable)
    }

    func updateUIViewController(_ uiViewController: ScannerController, context: Context) {
        uiViewController.onLink = onLink
        uiViewController.onUnavailable = onUnavailable
    }

    final class ScannerController: UIViewController, DataScannerViewControllerDelegate {
        private let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: true,
            isHighlightingEnabled: true
        )
        private var completed = false

        var onLink: (String) -> Void
        var onUnavailable: () -> Void

        init(onLink: @escaping (String) -> Void, onUnavailable: @escaping () -> Void) {
            self.onLink = onLink
            self.onUnavailable = onUnavailable
            super.init(nibName: nil, bundle: nil)
        }

        required init?(coder: NSCoder) {
            fatalError("Create this controller through QRCodeScanner.")
        }

        override func viewDidLoad() {
            super.viewDidLoad()
            scanner.delegate = self
            addChild(scanner)
            scanner.view.frame = view.bounds
            scanner.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(scanner.view)
            scanner.didMove(toParent: self)
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard !completed else { return }
            do {
                try scanner.startScanning()
            } catch {
                reportUnavailable()
            }
        }

        override func viewWillDisappear(_ animated: Bool) {
            scanner.stopScanning()
            super.viewWillDisappear(animated)
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            guard !completed else { return }
            for item in addedItems {
                guard case .barcode(let barcode) = item, let link = barcode.payloadStringValue else {
                    continue
                }
                completed = true
                dataScanner.stopScanning()
                onLink(link)
                return
            }
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable
        ) {
            reportUnavailable()
        }

        private func reportUnavailable() {
            guard !completed else { return }
            completed = true
            scanner.stopScanning()
            onUnavailable()
        }
    }
}
