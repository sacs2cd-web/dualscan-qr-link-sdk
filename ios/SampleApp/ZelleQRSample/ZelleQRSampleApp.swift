import AVFoundation
import Foundation
import SwiftUI
import VisionKit
import ZelleQRLink

@main
struct ZelleQRSampleApp: App {
    private let parser = ZelleQRLinkParser(
        bridgeHost: Bundle.main.object(forInfoDictionaryKey: "QRBridgeHost") as? String,
        customScheme: Bundle.main.object(forInfoDictionaryKey: "QRCustomScheme") as? String
    )

    var body: some Scene {
        WindowGroup {
            LinkReaderView(parser: parser)
        }
    }
}

@MainActor
private struct LinkReaderView: View {
    let parser: ZelleQRLinkParser

    @State private var input = ""
    @State private var result: Result<ZelleQRPayload, Error>?
    @State private var showingScanner = false
    @State private var scannerNotice = ""
    @State private var tokenVisible = false
    @FocusState private var inputFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section("Read a QR link") {
                    Button {
                        Task { await openScanner() }
                    } label: {
                        Label("Scan QR code", systemImage: "qrcode.viewfinder")
                    }
                    if !scannerNotice.isEmpty {
                        Text(scannerNotice).foregroundStyle(.secondary)
                    }
                    TextField("Paste a QR link", text: $input, axis: .vertical)
                        .lineLimit(2...4)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .focused($inputFocused)
                    Button("Decode link") {
                        let link = input.trimmingCharacters(in: .whitespacesAndNewlines)
                        input = ""
                        read(link)
                    }
                    .disabled(input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                Section("Result") {
                    switch result {
                    case .success(let payload):
                        LabeledContent("Name", value: payload.name)
                        LabeledContent("Token", value: tokenVisible ? payload.token : "Hidden")
                            .textSelection(.enabled)
                        Button(tokenVisible ? "Hide token" : "Show token") { tokenVisible.toggle() }
                        Text("Unverified fields from the QR code.")
                            .foregroundStyle(.secondary)
                        Button("Clear result", role: .destructive) { result = nil }
                    case .failure(let error):
                        Label(error.localizedDescription, systemImage: "exclamationmark.circle")
                    case nil:
                        Text("Scan, open, or paste a QR link.")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("QR Link Reader")
        }
        .onOpenURL { url in read(url.absoluteString) }
        .sheet(isPresented: $showingScanner) {
            NavigationStack {
                QRCodeScanner(
                    onLink: { read($0) },
                    onUnavailable: {
                        scannerNotice = "Camera scanning is unavailable. Check camera access in Settings or paste a link."
                        showingScanner = false
                    }
                )
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("Scan QR code")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showingScanner = false }
                    }
                }
            }
        }
    }

    private func read(_ rawLink: String) {
        showingScanner = false
        inputFocused = false
        scannerNotice = ""
        tokenVisible = false
        result = Result { try parser.parse(rawLink) }
    }

    @MainActor
    private func openScanner() async {
        scannerNotice = ""
        guard DataScannerViewController.isSupported else {
            scannerNotice = "Camera scanning is unavailable on this device. You can paste a QR link here."
            return
        }
        if AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined {
            _ = await AVCaptureDevice.requestAccess(for: .video)
        }
        guard DataScannerViewController.isAvailable else {
            scannerNotice = "Camera scanning is unavailable. Check camera access in Settings or paste a link."
            return
        }
        inputFocused = false
        showingScanner = true
    }
}
