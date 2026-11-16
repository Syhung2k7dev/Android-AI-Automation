# Android AI Automation

An independent Android automation prototype built around explicit user control and local-first workflows. Initial device target: Oppo Reno6 Z 5G running Android 13 / ColorOS. No root is required.

## Current implementation

- Android Accessibility Service used for explicitly user-started tap and swipe gestures.
- Multi-step workflow editor with tap, swipe, and wait actions.
- Visual coordinate picker for tap points and both swipe endpoints.
- Reorder and delete individual workflow steps.
- Save and load named workflows locally.
- Saved workflow library with explicit delete confirmation.
- Import and export workflow files through Android's document picker.
- Validation of imported step types, coordinates, durations, workflow size, and step count.
- Explicit run confirmation, five-second start delay, finite repeats, and Stop control.
- One-shot, user-confirmed screenshot capture followed by on-device OCR using a bundled Latin-script model.
- OCR results are temporarily stored locally for display; the screenshot itself is not saved or uploaded.
- Current workflow limits: 50 steps and 20 repeats.

This remains an early development build, not the complete product. Image matching, OCR-driven workflow conditions, scheduling, and multi-account management are not yet implemented.

## Not implemented

- Local image matching and image-based workflow conditions.
- OCR-driven conditional branching and text-triggered actions.
- Scheduling and foreground execution controls.
- Optional AI-assisted recovery.
- Multi-account workflow management.
- Production release signing and distribution review.

## Platform target

- Initial target: Oppo Reno6 Z 5G, Android 13 / ColorOS.
- Minimum Android version: Android 8.0 (API 26).
- No root requirement.
- A computer is not required to use an installed APK.

## Security and permissions

The app uses Accessibility for user-configured actions. The screenshot capability is used only after a separate in-app confirmation, with a single capture after a short switch window. The image is processed on-device and is not written to storage or uploaded. Recognized text is temporarily stored locally until the app displays it or the user clears it.

Do not capture private or sensitive screens. Some secure or protected windows may prevent screenshots. The app must not hide its operation, bypass Android security controls, evade Play Protect, bypass anti-cheat systems, or interact with protected screens without authorization.

A Play Protect warning must be investigated before distributing or using a build. Do not disable device protection or grant Accessibility to a build Android identifies as harmful. Debug APKs are development artifacts, not reviewed production releases.

Accessibility availability depends on Android and device-manufacturer security policy. A service declaration does not prove that a particular APK is safe or that ColorOS will allow it to be enabled.

Workflow files are plain text and may contain user-configured screen coordinates. Export them only to locations you trust. Imported files are size-limited and validated before they can replace the current workflow.

## Build status

GitHub Actions builds a debug APK for pull requests and pushes to `main`. Check the Actions run and uploaded artifact for the exact commit; a successful source edit alone does not prove that the APK built successfully.

The bundled OCR model increases APK size compared with the earlier minimal prototype. This is intentional so text recognition can work without downloading a model at first use.

## Development approach

The implementation is original and does not copy Smart AutoClicker's source. Any future third-party code or dependency must have its license reviewed before inclusion.

## Roadmap

1. Device compatibility and execution reliability checks.
2. Local image matching and visual conditions.
3. OCR-driven conditions with explicit user configuration.
4. Scheduling with visible status and reliable cancellation.
5. Optional, bounded AI-assisted recovery.
6. Release signing, distribution review, and security verification.
