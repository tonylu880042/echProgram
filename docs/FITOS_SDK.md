# FitOS equipment SDK boundary

Gate 3A vendors the API v1 Android archive required for the read-only
equipment telemetry boundary. API v1 is the minimum supported read contract;
newer API versions use the same backward-compatible read path.

- Gradle artifact: `app/libs/FitOSEquipmentSDK-v1.aar`
- Source supplied in this workspace: `AIDL/FitOSEquipmentSDK.aar`
- Source shape: an outer ZIP containing
  `FitOSEquipmentSDK-aar/FitOSEquipmentSDK-release.aar`; Gradle references the
  extracted inner AAR rather than the outer wrapper.
- SHA-256: `f9ff88c68542b646b9634d72239c596dfa27af2f11e55b4068548a4b6ab20757`
- API guide: `AIDL/FitOS-Equipment-SDK-Integration-Guide-v1.zip`
- Host package: `com.ucare.fitos`
- API version: `1`

The application manifest declares the host service permission and package
visibility query required by the API guide for target SDK 30 and higher.
This increment consumes only connection, state, limits, and telemetry reads;
workout and equipment-control methods remain outside the application boundary.

The app displays snapshot distance in `KM` when `EquipmentState.isMetric` is
true and `MI` otherwise, and displays calories as `KCAL`. FitOS documents the
console-unit behavior for snapshots via `isMetric`; the exact distance source
semantics should still be confirmed with FitOS before production release.

The adapter reports the service as unavailable when no service callback arrives
within five seconds, and marks a ready telemetry stream stale when the host
timestamp is at least three seconds old. These are conservative app-side
read-state thresholds, not FitOS host guarantees.

The supplied artifact does not contain independent release metadata or a
license notice. FitOS release/version and redistribution terms remain an open
release-readiness question.
