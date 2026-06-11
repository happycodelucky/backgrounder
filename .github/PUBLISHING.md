# Publishing

Backgrounder ships via two independent channels from `.github/workflows/release.yml`:

- **Maven Central** — Android AAR, `kotlinMultiplatform` metadata, per-target klibs. For Gradle/KMP consumers.
- **GitHub Releases** (via KMMBridge) — the SKIE-enhanced `Backgrounder.xcframework` zip. For pure-Swift SPM consumers.

## Maven Central

**Coordinates:** `com.happycodelucky.backgrounder:backgrounder`

One Gradle invocation publishes:

- The Android AAR.
- The `kotlinMultiplatform` metadata module (`.module` file) that ties every target together.
- Per-target klibs: `backgrounder-iosarm64`, `backgrounder-iossimulatorarm64`, `backgrounder-macosarm64`, `backgrounder-android`.
- Sources / javadoc jars next to each, with detached GPG signatures.

## Release pipeline

Releases are triggered via `workflow_dispatch` on `release.yml`. No one types a version number — the workflow computes it.

1. Choose `bumpType` (patch / minor / major). The version is derived from the latest GitHub Release; the chosen component increments and everything below it resets to zero. An optional `versionSuffix` adds a SemVer pre-release identifier.
2. **`dryRun=true` (default)** — runs `publishToMavenCentral`: uploads to Central Portal staging only. The deployment sits in "validated" state; review it at https://central.sonatype.com/ and click Publish (or Drop). Nothing is tagged and no XCFramework is published. Safe to run frequently.
3. **`dryRun=false`** — runs `publishAndReleaseToMavenCentral` (irreversible), then the SPM steps: `:backgrounder:kmmBridgePublish` builds and uploads `Backgrounder.xcframework.zip` to the `vX.Y.Z` GitHub Release and regenerates `Package.swift`; the workflow rewrites the asset URL to the public form, commits `Package.swift` to `main`, and force-moves the tag onto that commit.

The `automaticRelease = false` flag in `backgrounder/build.gradle.kts` is what makes dry-run behaviour correct. Do not flip it without reading the comment there.

**Secrets:** the four `MAVEN_CENTRAL_*` secrets live on the `continuous-deployment` GitHub environment, not the repository scope. The `release.yml` job binds to it via `environment: continuous-deployment`.

## SPM distribution — KMMBridge → GitHub Releases

Touchlab's KMMBridge publishes the Apple framework to pure-Swift SPM consumers. The pipeline (real publishes only, not dry-run):

1. Gradle builds an `XCFramework` with `iosArm64` + `iosSimulatorArm64` + `macosArm64` slices. No x86. SKIE-enhanced (`produceDistributableFramework()` emits `.swiftinterface` files required by Xcode 26).
2. KMMBridge zips the XCFramework and uploads it as a GitHub Release asset. GitHub *Releases*, not GitHub *Packages* — Packages requires a PAT to download even from public repos; Release assets are public and unauthenticated.
3. KMMBridge regenerates the root `Package.swift` referencing the asset by URL + sha256 checksum. The workflow rewrites KMMBridge's API asset URL to the public `releases/download/…` form, commits `Package.swift` to `main`, and force-moves the version tag onto that commit so the tagged manifest matches the uploaded binary.
4. Swift consumers add this repo's URL as an SPM dependency pinned to a version tag; the tagged `Package.swift` hands them the prebuilt binary.

### Rules

- KMMBridge config lives in the `kmmbridge { }` block in `backgrounder/build.gradle.kts`; the version pin lives in `gradle/libs.versions.toml`. Only `:backgrounder` gets KMMBridge — `:background-monitor` ships klibs via Maven Central only.
- Do **not** redeclare `XCFramework("Backgrounder")` in the `kotlin { }` block: KMMBridge auto-creates the aggregator tasks (`assembleBackgrounder{Debug,Release}XCFramework`) at config time; a second declaration collides.
- Versioning: the release workflow computes the version and passes `-Pversion=X.Y.Z`; KMMBridge tags `v${version}`. KMMBridge's own timestamp versioning is not used.
- Publishing is CI-only: the `kmmBridgePublish` task only exists when `-PENABLE_PUBLISHING=true` is passed (the release workflow does this).
- Don't vendor `XCFramework` zips into the repo. Everything flows through GitHub Release assets + the committed `Package.swift`.
- `Package.swift` is generated — `kmmBridgePublish` writes the released form, `spmDevBuild` the local-dev form. Don't hand-edit it, and never commit the local-dev form.

## Local XCFramework development

The sample apps under `/iOSApp` and `/macOSApp` consume the root `Package.swift` as a local package.

```bash
mise run spm:dev      # rebuild debug XCFramework + flip Package.swift to local path
mise run spm:restore  # restore the committed (remote-binary) Package.swift
mise run xcframework  # rebuild release XCFramework without touching Package.swift
```

Until the first `dryRun=false` release runs, the committed `Package.swift` still points at the local build path — the first real release flips it to the remote-binary form.
