# Publishing to F-Droid

1. Publish this standalone source under GPL-3.0-or-later in a public Git repository.
   Do not upload `local.properties`, `.gradle-cache`, `.gradle-build-cache`, `.qa-avd`, build folders, personal backups,
   Android databases, media or signing keys. No private web-project files are needed.
2. Confirm the application ID `com.jdinohaila.studentgarden` is available and belongs
   to this project. Keep it stable after the first public release.
3. Run the documented tests, lint and release build. Test recording and alarm behavior
   on a physical Android device, including screen-off/battery-saver behavior. The app
   explains when Android may delay an alarm or when notification permission is absent.
4. Commit all source, the Gradle wrapper, font licenses and Fastlane metadata. Create
   the actual `v0.11.0` release tag. Do not tag a build that still has unresolved failures.
5. Copy `metadata-template.yml` into F-Droid Data's metadata directory with the package
   name. Replace the marked repository fields with your real public URLs.
6. Validate using F-Droid's tools and submit a merge request to F-Droid Data following
   the official guide. Maintainers review eligibility and builds. No submission or
   acceptance has been performed by this local project creation.

Official references:
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/en/docs/Build_Metadata_Reference/

The app has native study workflows, no WebView wrapper, optional direct YouTube video access,
no advertising or tracking SDK, and no Google Play Services dependency. Source and
third-party licenses still need normal review by F-Droid maintainers.

The test-signed debug APK uses the separate `.preview` application ID. It can coexist
with the future F-Droid app. Export from the preview and import into the release to
transfer data; neither app silently deletes the other’s private storage.
