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

## Zone 2 HR advisory contract (preview only)

The workspace guide at
`AIDL/FitOS-Equipment-SDK-Integration-Guide-v1/FitOS-Equipment-SDK-Integration-Guide-v1.html`
is the source for these API facts:

- `EquipmentSnapshot.hr` is a display `String` containing BPM. It may be empty
  or otherwise not parseable, so the domain treats missing or invalid values as
  unavailable rather than guessing a value.
- `EquipmentSnapshot.elapsedRealtimeMillis` is the host's
  `SystemClock.elapsedRealtime()` value. It is the timestamp used for sample
  age, freshness, and gaps between callbacks.
- `onEquipmentDataChanged` arrives roughly once per second while the equipment
  reports, with field changes within one machine tick conflated into one push.

The Zone 2 domain contract accepts only a positive, ordered target range that
the user has confirmed (`USER_CONFIRMED`); it does not derive a zone from age,
maximum heart rate, or a medical formula. Evaluation uses caller-provided
`nowElapsedRealtimeMillis` and `staleAfterMillis`, inclusive lower/upper
thresholds, and marks a sample as `HR_SIGNAL_LOST` when its age is at least the
stale threshold. Results are explicitly `PREVIEW_ONLY` and `ADVISORY_ONLY`:
they may suggest incline, suggest reducing effort with manual-stop
availability, hold, or make no adjustment in manual mode. They contain no
motor setpoint, speed/incline command, command acknowledgement, or automatic
control operation.

FitOS does not provide target-zone ownership, hysteresis semantics, or medical
formula approval. The domain therefore labels this behavior
`DIRECT_THRESHOLD_PREVIEW` and `NO_HYSTERESIS_APPROVED`; those product and
safety decisions remain open for customer/FitOS confirmation.
