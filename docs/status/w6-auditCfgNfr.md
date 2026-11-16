# Wave 6 audit: config and non-functional

| ID | Status | Note |
| --- | --- | --- |
| CFG-01 | partial | Unchanged. Backup-exclusion scan test (CFG_01 in StateBackupExclusionTest) classifies every getSharedPreferences name as exported or excluded; no personal content exported. Remaining gaps as in SPEC_STATUS. |
| CFG-02 | partial | Unchanged; widget re-bind consent and work-profile configure need a device. |
| CFG-03 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present): nested unknown-field preservation tests in core-data. |
| CFG-04 | partial | Unchanged; Launchpad rule removal not covered by the snapshot hook. |
| CFG-05 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present) (SettingsFocus row landing, Radar index). |
| CFG-06 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present) (OnboardingFlow and OnboardingView renders). |
| CFG-07 | partial | Unchanged; activity recognition and approximate location are direct prompts, a Spec owner decision. |
| CFG-08 | built | Audit found two section 3 table rows with no report row (start activities from the background; uninstall, app info, default-app settings) and keyboard, pointer and controller rows that were Off with no reason, against 'the reason for any that are off'. Added both rows and the reasons; new test CFG_08_everyRowOfTheSection3TableHasAReportRow pins the table and that every Off row carries a reason. Still not seen on a device. |
| CFG-09 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present): CrashLoopMath window tests, SafeModeView renders. Not exercised on a device. |
| CFG-10 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present). |
| CFG-11 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present): settings document is standalone. |
| CFG-12 | partial | Unchanged; no documented third-party backup schema beyond AOSP favorites. |
| CFG-13 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present) (GroundControlNav tests). |
| CFG-14 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present) (SettingsIndex.search shared with Radar). |
| CFG-15 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present): PuckActionsTest and StateScreensTest pin ceiling equal to the layout ceiling (INP-19). |
| CFG-16 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present) (kit screenshot tests light and dark). |
| CFG-17 | partial | Downgraded from built: Spec says 'debug builds'; the Lab category is shown in release builds too during alpha so spikes can run on the release APK. Gap: gate on BuildConfig.DEBUG (or a deliberate owner decision) before a public build. |
| CFG-18 | partial | Unchanged; geometry tests exist, on-device look unverified. |
| CFG-19 | built | Verified against Spec.md and SPEC_STATUS row; claim stands unchanged (code reachable, test present): TranslationCoverageTest source scan. No new strings added by this audit (capability titles are the report's own English text, as the existing rows). |
| CFG-20 | partial | Owner decision, unchanged (docs/decisions/CFG-20.md). |
| NFR-01 | partial | Unchanged: device or cohort measurement only (warm start p95 150 ms); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-02 | partial | Unchanged: device or cohort measurement only (cold start p95 700 ms); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-03 | partial | Unchanged: device or cohort measurement only (palette open 100 ms); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-04 | partial | Unchanged: device or cohort measurement only (keystroke 50 ms); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-05 | partial | Unchanged: device or cohort measurement only (1% janky); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-06 | partial | Unchanged: device or cohort measurement only (180 MB PSS); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-07 | partial | Unchanged: device or cohort measurement only (0.5% battery per day); tooling in tools/ exists. No code claim to check beyond that. Verified no wake locks (BudgetsTest) and that every JobInfo carries constraints (NfrWiringTest). |
| NFR-08 | built | Verified apk-size.gradle.kts is applied from app/build.gradle.kts and BudgetsTest pins it; the APK stands in for download size. |
| NFR-09 | partial | Unchanged: device or cohort measurement only (99.8% crash-free); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-10 | partial | Unchanged: device or cohort measurement only (2 h soak); tooling in tools/ exists. No code claim to check beyond that. |
| NFR-11 | partial | Unchanged; restart tests exist, reboot, package update and hot-plug need a device. |
| NFR-12 | partial | Unchanged; StrictMode on a device not run. |
| NFR-13 | partial | Unchanged; TalkBack run not done. |
| NFR-14 | partial | Unchanged; 200% font scale on device not done. |
| NFR-15 | partial | Unchanged; contrast sampled in tests, not over real wallpapers. |
| NFR-16 | unmet | Unchanged; INTERNET still declared for weather, decision in docs/decisions/NFR-16.md. |
| NFR-17 | partial | Unchanged; source and build-file audits stand. |
| NFR-18 | partial | Verified every SharedPreferences file name in source is classified and excluded from cloud backup, device transfer and full backup rules (StateBackupExclusionTest); widget ids split into device_bound. |
| NFR-19 | partial | Unchanged; predictive back not seen on a device. |
| NFR-20 | built | Verified no systemGestureExclusion remains in main sources (audit test). Not seen on three-button or gesture navigation devices. |
| NFR-21 | built | Verified OEM notes row is in the report (OemNotesTest). Text unconfirmed until SPK-06. |
