import SwiftUI
import UIKit

struct CameraPicker: UIViewControllerRepresentable {
    let onCapture: (URL) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.mediaTypes = ["public.image", "public.movie"]
        picker.videoQuality = .typeHigh
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, dismiss: dismiss)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (URL) -> Void
        let dismiss: DismissAction

        init(onCapture: @escaping (URL) -> Void, dismiss: DismissAction) {
            self.onCapture = onCapture
            self.dismiss = dismiss
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            let tempDir = FileManager.default.temporaryDirectory

            if let videoURL = info[.mediaURL] as? URL {
                let dest = tempDir.appendingPathComponent("capture_\(UUID().uuidString.prefix(8)).\(videoURL.pathExtension)")
                try? FileManager.default.copyItem(at: videoURL, to: dest)
                onCapture(dest)
            } else if let image = info[.originalImage] as? UIImage {
                let fileName = "capture_\(UUID().uuidString.prefix(8)).jpg"
                let dest = tempDir.appendingPathComponent(fileName)
                if let data = image.jpegData(compressionQuality: 0.85) {
                    try? data.write(to: dest)
                    onCapture(dest)
                }
            }

            dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            dismiss()
        }
    }
}
