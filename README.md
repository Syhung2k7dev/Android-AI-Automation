# Android AI Automation

An independent Android automation project targeting Android 13+ and devices such as the Oppo Reno6 Z 5G. The app is designed around explicit user control and local-first execution.

## Current implementation

The current development build is an early prototype, not the complete product.

Implemented:
- Accessibility service declaration for user-authorized gesture automation.
- Coordinate-based tap sequences with a minimum 500 ms interval and a bounded tap count.
- Start and stop controls.
- Local save/load of named coordinate-tap profiles.

Not implemented yet:
- Visual tap-point selection and overlay controls.
- On-device image matching.
- Offline OCR.
- A validated automation-script format and editor.
- Scheduling and foreground execution controls.
- Optional AI-assisted recovery.
- Multi-account workflow management.

## Device and platform target

- Initial target: Oppo Reno6 Z 5G, Android 13 / ColorOS.
- Minimum Android version: Android 8.0 (API 26).
- No root requirement.
- A computer is not required to use the installed app.

## Security and permissions

The app uses Android Accessibility only for actions explicitly started by the user. It must not attempt to hide its operation, bypass Android security controls, evade Play Protect, or bypass anti-cheat and other protected-system safeguards.

A Play Protect warning must be investigated before distributing or using a build. Do not disable device protection or grant Accessibility to a build that Android identifies as harmful. Debug APKs are development artifacts and are not equivalent to a reviewed production release.

Accessibility availability depends on Android and device-manufacturer security policy. A service declaration alone does not prove that a particular APK is safe or that ColorOS will allow it to be enabled.

## Build status

GitHub Actions builds a debug APK when changes are pushed to `main`. A successful source commit does not by itself mean that the APK built successfully; check the Actions run and its uploaded artifact.

## Development approach

The implementation is original and does not copy Smart AutoClicker's source. The project may use its publicly described architectural ideas as reference. Any future third-party code or dependency must have its license reviewed before inclusion.

## Planned milestones

1. Stable build and device compatibility checks.
2. Clear Accessibility connection and permission-state diagnostics.
3. Visual point selection and transparent overlay controls.
4. Local image matching and offline OCR.
5. Validated automation profiles and scheduling.
6. Optional, bounded AI-assisted recovery.
7. Release signing, distribution review, and security verification.
