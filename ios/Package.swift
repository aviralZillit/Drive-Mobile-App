// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ZillitDrive",
    platforms: [.iOS(.v16)],
    dependencies: [
        .package(url: "https://github.com/onevcat/Kingfisher.git", from: "7.12.0"),
        .package(url: "https://github.com/socketio/socket.io-client-swift.git", from: "16.1.1"),
    ],
    targets: [
        .target(
            name: "ZillitDrive",
            dependencies: [
                "Kingfisher",
                .product(name: "SocketIO", package: "socket.io-client-swift"),
            ],
            path: "ZillitDrive",
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "ZillitDriveTests",
            dependencies: ["ZillitDrive"],
            path: "ZillitDriveTests"
        ),
    ]
)
