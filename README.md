# Android AI Automation

An independent Android automation prototype built around explicit user control and local-first workflows. Initial device target: Oppo Reno6 Z 5G running Android 13 / ColorOS. No root is required.

## Current implementation

- Android Accessibility Service used for explicitly user-started tap and swipe gestures.
- Multi-step workflow editor with tap, swipe, wait, bounded OCR text conditions, and local image-template conditions.
- Visual coordinate picker for tap points and both swipe endpoints.
- Reorder and delete individual workflow steps.
- Save and load named workflows locally.
- Saved workflow library with explicit delete confirmation.
- Import and export workflow files through Android's document picker.
- Validation of imported step types, coordinates, durations, workflow size, and step count.
- Explicit run confirmation, five-second start delay, finite repeats, and Stop control.
- User-confirmed one-shot OCR plus workflow text conditions that poll screenshots locally until a configured phrase appears or the timeout expires.
- OCR results are temporarily stored locally for display; screenshots used by local OCR and image matching are processed on-device and are not uploaded.
- Local visual matching: import a user-selected image template, store it in app-private storage, and add a bounded wait-until-image-matches workflow step. Matching uses downscaled grayscale normalized cross-correlation on-device; it is not the OpenCV library.
- Optional Gemini cloud foundation: API key stored with AndroidX encrypted preferences backed by Android Keystore, explicit connection test, bounded retries, persistent failure circuit breaker, and token/request counters.
- Cloud API use is opt-in and explicit. The current integration tests text-only requests; it does not upload screenshots or automatically trigger cloud recovery.
- Current workflow limits: 50 steps and 20 repeats.
- OCR condition defaults: 30-second timeout, 2-second polling interval; both are bounded to avoid indefinite capture loops.

This remains an early development build, not the complete product. OCR and basic local image conditions are implemented as bounded wait steps; branching, persistent scheduled timers, and a dedicated foreground dashboard are not yet implemented. The current queue runs saved workflows sequentially only while the Accessibility Service remains connected.

## Not implemented

- Advanced OpenCV-accelerated matching, multi-scale matching, and image-absence conditions.
- OCR branching beyond the current wait-until-text-appears step.
- Scheduling and foreground execution controls.
- Screenshot-based Gemini recovery and structured workflow mutation are not yet implemented; the current cloud integration is a text-only connection foundation.
- Sequential queue execution for up to 20 saved workflow profiles after explicit confirmation.
- Persistent scheduled cooldown timers and a dedicated foreground dashboard.
- Production release signing and distribution review.

## Platform target

- Initial target: Oppo Reno6 Z 5G, Android 13 / ColorOS.
- Minimum Android version: Android 8.0 (API 26).
- No root requirement.
- A computer is not required to use an installed APK.

## Optional Gemini API settings

Users may enter or remove their own Gemini API key in the app. The key is stored locally with encrypted preferences backed by Android Keystore. A separate user action is required to test the connection. The current integration sends only a short text test prompt, tracks API-reported token usage, retries transient network/server failures with bounded exponential backoff, and opens a five-minute circuit breaker after repeated failures. Cloud use is optional; local workflows remain available without a key. No screenshot is uploaded by the current implementation. API keys and requests are sensitive: do not share them, and review Google's API terms, data handling, and billing before enabling cloud access.

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
2. Advanced OpenCV-based image matching and visual conditions.
3. Expand OCR conditions into explicit if/else branching.
4. Scheduling with visible status and reliable cancellation.
5. Optional, bounded AI-assisted recovery.
6. Release signing, distribution review, and security verification.
