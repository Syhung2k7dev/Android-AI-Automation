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
- Local visual matching: import a user-selected image template or learn a crop from a user-confirmed screen region, store it in app-private storage, and add bounded wait-until-image-matches or IF-image steps. Native OpenCV `matchTemplate` is used when its Android native library initializes; the Kotlin matcher remains a fallback.
- Optional Gemini cloud analysis: API key stored with AndroidX encrypted preferences backed by Android Keystore; explicit screenshot-upload consent; multimodal screenshot analysis; bounded retries, persistent failure circuit breaker, rolling RPM and token counters, and one bounded JSON schema-repair request.
- Cloud screenshot analysis is opt-in and requires a separate confirmation each time. Model output is shown as a suggestion only; it is never executed as gestures or used to mutate a running workflow automatically.
- Versioned JSON workflow import/export (version 2), backward-compatible with the earlier line-based format; supports tap, swipe, wait, wait-for-text, wait-for-image, nested IF text/image, ELSE, and END IF blocks.
- Sequential saved-profile queue: up to 20 profiles, explicit confirmation, 50 steps per profile.
- Floating accessibility dashboard with Pause/Resume/Stop controls and a visible foreground notification during user-started execution.
- Perceptual-hash screen inactivity watchdog; after repeated unchanged screen samples during a gesture step, it pauses for user review instead of taking recovery actions.
- One-shot cooldown reminders scheduled with `AlarmManager.setAndAllowWhileIdle`; no periodic polling is performed while waiting, and reminders never auto-start workflows.
- Current workflow limits: 50 steps and 20 repeats.
- OCR condition defaults: 30-second timeout, 2-second polling interval; both are bounded to avoid indefinite capture loops.

This remains an early development build. The current visual recovery module analyzes a screenshot only after explicit user consent and displays a structured suggestion; it does not automatically execute AI output. Template learning requires the user to choose a crop region. Cooldown scheduling currently provides one-shot reminders, not unattended recurring workflow execution. Device-level performance and ColorOS compatibility still require testing.

## Remaining work

- Multi-scale matching, image-absence conditions, and robust visual-region tracking.
- Recurring calendar-like schedules, reboot restoration, and profile-specific cooldown timers.
- Full unattended recovery is intentionally not enabled: Gemini suggestions require review and are never blindly dispatched as gestures.
- Automated template learning from reviewed and user-approved visual examples; current template capture is user-selected and local.
- Production release signing, security review, and Play Protect / distribution verification.

## Platform target

- Initial target: Oppo Reno6 Z 5G, Android 13 / ColorOS.
- Minimum Android version: Android 8.0 (API 26).
- No root requirement.
- A computer is not required to use an installed APK.

## Optional Gemini API settings

Users may enter or remove their own Gemini API key in the app. The key is stored locally with encrypted preferences backed by Android Keystore. A separate user action is required to test the connection. The current integration sends only a short text test prompt, tracks API-reported token usage, retries transient network/server failures with bounded exponential backoff, and opens a five-minute circuit breaker after repeated failures. Cloud use is optional; local workflows remain available without a key. No screenshot is uploaded by the current implementation. API keys and requests are sensitive: do not share them, and review Google's API terms, data handling, and billing before enabling cloud access.

## Runtime controls

A user-started workflow runs with a visible foreground notification and an accessibility overlay dashboard. The pHash watchdog samples only while a gesture step is active; repeated near-identical screens pause the workflow for user review. Pause cancels scheduled polling, and Stop cancels the queue. The cooldown scheduler uses an inexact `AlarmManager` reminder; it does not launch or resume automation by itself. Android and ColorOS may delay alarms or terminate services under battery-management policies.

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

1. Device compatibility and execution reliability checks on Oppo Reno6 Z / ColorOS.
2. Multi-scale visual matching and image-absence conditions.
3. Recurring timers and reboot restoration.
4. User-reviewed template suggestions and improved recovery diagnostics.
5. Release signing, distribution review, and security verification.
