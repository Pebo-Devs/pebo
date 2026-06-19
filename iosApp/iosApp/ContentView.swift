import UIKit
import SwiftUI
import ComposeApp

/// Wraps the shared Compose UI (Kotlin `MainViewController()` from the `ComposeApp` framework) in a
/// SwiftUI view. `MainViewControllerKt` is the Objective-C class Kotlin/Native generates for the
/// top-level functions in `MainViewController.kt`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose handles the keyboard inset itself
    }
}
