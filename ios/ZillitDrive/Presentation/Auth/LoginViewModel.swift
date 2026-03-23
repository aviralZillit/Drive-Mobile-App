import Foundation

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var userId = ""
    @Published var projectId = ""
    @Published var deviceId = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    var isFormValid: Bool {
        !userId.trimmingCharacters(in: .whitespaces).isEmpty &&
        !projectId.trimmingCharacters(in: .whitespaces).isEmpty &&
        !deviceId.trimmingCharacters(in: .whitespaces).isEmpty
    }

    func login(sessionManager: SessionManager) {
        guard isFormValid else {
            errorMessage = "Please fill in all fields"
            return
        }

        isLoading = true
        errorMessage = nil

        let session = UserSession(
            userId: userId.trimmingCharacters(in: .whitespaces),
            projectId: projectId.trimmingCharacters(in: .whitespaces),
            deviceId: deviceId.trimmingCharacters(in: .whitespaces),
            scannerDeviceId: deviceId.trimmingCharacters(in: .whitespaces),
            userName: "",
            userEmail: ""
        )

        sessionManager.saveSession(session)
        isLoading = false
    }
}
