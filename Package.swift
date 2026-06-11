// swift-tools-version:6.0
//
// GENERATED — do not hand-edit (CLAUDE.md §9).
//
// This manifest is written by KMMBridge:
//   * `kmmBridgePublish` (release workflow) writes the *released* form — a
//     remote `.binaryTarget(url:checksum:)` against the GitHub Release asset.
//     SPM consumers add this repo's URL pinned to a version tag and get the
//     prebuilt, SKIE-enhanced Backgrounder.xcframework. No Kotlin toolchain.
//   * `spmDevBuild` (mise run spm:dev) rewrites it to a local
//     `.binaryTarget(path:)` against backgrounder/build/XCFrameworks/debug/
//     so the in-repo sample apps (iOSApp, macOSApp) pick up local Kotlin
//     edits. This local form is a working-tree convenience — never commit it;
//     `mise run spm:restore` puts the committed version back.
//
// FIRST-RELEASE CAVEAT: until the first `dryRun=false` release runs, the
// committed manifest below is still the local-path form. The first release
// flips it to the remote-binary form; SPM consumption starts at that tag.
//
// KMP consumers (Android / other KMP targets) use Maven Central instead:
//   implementation("com.happycodelucky.backgrounder:backgrounder:X.Y.Z")
// — add mavenCentral() and Gradle resolves the right per-target klib.

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
