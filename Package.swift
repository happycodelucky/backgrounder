// swift-tools-version:6.0
//
// LOCAL DEVELOPMENT ONLY.
//
// This Package.swift exists so sample apps inside this repo (iOSApp, macOSApp)
// can consume the Backgrounder framework via `.package(path: "..")` without
// going through a remote publish step. It references the *debug* XCFramework
// that Gradle writes to `backgrounder/build/XCFrameworks/debug/`.
//
// Rebuild the debug XCFramework before opening Xcode:
//   mise run spm:dev
// or equivalently:
//   ./gradlew :backgrounder:assembleBackgrounderDebugXCFramework
//
// Remote distribution (KMP consumers on Android / other KMP targets):
//   Maven Central — com.happycodelucky.backgrounder:backgrounder:X.Y.Z
//   No extra setup; add mavenCentral() to your repositories and depend on
//   the coordinate from commonMain. Gradle resolves the right per-target
//   klib automatically.
//
// Remote SPM distribution (hosted XCFramework zip via URL + checksum) is
// future work. See CLAUDE.md §9 for the architectural sketch.

import PackageDescription

let packageName = "Backgrounder"

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v18),
        .macOS(.v15),
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]
        ),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            path: "./backgrounder/build/XCFrameworks/debug/\(packageName).xcframework"
        ),
    ]
)
