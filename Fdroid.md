# F-Droid Publication Plan - Fencing Scores App

## Overview
Publishing the Fencing Scores app to F-Droid, the official Free and Open Source Android app repository, with proper GitHub source code linking and metadata.

## F-Droid Requirements Checklist

### 1. **License & Source Code**
- [ ] Ensure app uses a compatible open-source license (currently: GNU GPLv3) ✅
- [ ] Verify all dependencies are open-source compatible
- [ ] Ensure source code is publicly available on GitHub ✅ (https://github.com/leodp/Fencing_scores)
- [ ] Include LICENSE file in repository root ✅
- [ ] Add CHANGELOG.md with version history ✅

### 2. **App Signing & Versioning**
- [ ] Use a proper release signing key (currently using debug key - needs change)
- [ ] Increment versionCode for each release (current: 12)
- [ ] Follow semantic versioning in versionName (current: 2.22)
- [ ] Document signing process in repository

### 3. **Source Code Metadata** (Required by F-Droid)
- [ ] Create `app/build.gradle` metadata section:
  ```groovy
  android {
    defaultConfig {
      applicationId "com.fencing.scores"
      versionCode 12
      versionName "2.22"
      minSdkVersion 30
      targetSdkVersion 34
    }
  }
  ```
- [ ] Ensure `build.gradle` files are in repository ✅
- [ ] Verify `gradle.properties` is properly configured ✅
- [ ] Check `settings.gradle` exists ✅

### 4. **README & Documentation**
- [ ] Update README.md with:
  - Clear app description
  - Features list
  - Installation instructions
  - Development setup guide
  - License information
- [ ] Add "F-Droid" download link once published
- [ ] Include screenshots/app demo info

### 5. **Remove Debug/Test Files from Repository**
- [ ] Already done: Removed `14.03.57.csv`, `Fence-debug.apk`, `MergedKOdebug.csv` ✅
- [ ] Verify no `.apk` files in git history (keep only releases in GitHub Releases)
- [ ] Check for any hardcoded debug credentials or test data
- [ ] Ensure `local.properties` is in `.gitignore` ✅

### 6. **Code Quality & Security**
- [ ] Run lint checks: `./gradlew lint`
- [ ] Check for hardcoded API keys, credentials (none found - good ✅)
- [ ] Verify ProGuard rules are appropriate (`app/proguard-rules.pro` ✅)
- [ ] Ensure minSdk 30 is appropriate for target audience
- [ ] Test app on actual devices/emulators

### 7. **Dependencies Review**
- [ ] Audit all gradle dependencies for:
  - Open-source compatibility
  - Known vulnerabilities
  - Proprietary/restricted licenses
- [ ] Current key dependencies to verify:
  - AndroidX (Material Design, ViewPager2, ViewModel, LiveData) - All MIT/Apache 2.0 ✅
  - ZXing 3.5.2 (QR codes) - Apache 2.0 ✅
  - Gson - Apache 2.0 ✅

## Implementation Steps

### Phase 1: Prepare App Release (This Week)

#### Step 1.1 - Create Release APK with Production Signing Key
```bash
# Generate release keystore (if not already done)
keytool -genkey -v -keystore ~/android_release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias fencing_scores_release

# Update build.gradle with release signing config
# app/build.gradle: Add signingConfigs section

# Build signed release APK
./gradlew assembleRelease

# Sign with jarsigner (alternative method)
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore ~/android_release.jks \
  app/build/outputs/apk/release/Fence.apk fencing_scores_release
```

#### Step 1.2 - Update Version Numbers
- Update `app/build.gradle`: `versionCode 13`, `versionName "2.23"`
- Reason: Signal new release cycle with proper versioning

#### Step 1.3 - Update README.md
```markdown
# Fencing Scores

**Open-source Android app for fencing pool/round management**

## Features
- Dynamic round management (1-5 rounds)
- Participant name management
- Bout result tracking with matrix editor
- QR code generation & scanning for quick participant input
- CSV import/export
- Automatic backup per round
- Color-coded participant cycling
- Support for merged brackets, knockout, and final rankings

## Installation

### F-Droid (Recommended)
[Get it on F-Droid](https://f-droid.org/packages/com.fencing.scores/)

### GitHub Releases
Download APK directly from [GitHub Releases](https://github.com/leodp/Fencing_scores/releases)

## Development

### Build from Source
```bash
git clone https://github.com/leodp/Fencing_scores.git
cd Fencing_scores
./gradlew assembleDebug
```

### Requirements
- Android SDK 30+
- Gradle 9.2.1+
- Java 21+

### Technologies
- AndroidX (ViewPager2, ViewModel, LiveData)
- Material Design 3
- ZXing QR library
- Gson JSON

## License
GNU General Public License v3.0 - See [LICENSE](LICENSE) file

## Support
Report issues on [GitHub Issues](https://github.com/leodp/Fencing_scores/issues)
```

#### Step 1.4 - Create GitHub Release
```bash
# Tag the release
git tag -a v2.23 -m "Release 2.23: Fixed round-change data corruption"

# Push tag
git push origin v2.23

# On GitHub: Create Release from tag, upload signed APK, add changelog
```

### Phase 2: F-Droid Submission

#### Step 2.1 - Create F-Droid App Metadata Directory
```
app/build/intermediates/fdroid/
  └── com.fencing.scores/
      ├── en-US/
      │   ├── name.txt (max 50 chars)
      │   ├── summary.txt (max 80 chars)
      │   ├── description.txt
      │   ├── phone_screenshots/
      │   │   ├── 1.png (best: 480x854)
      │   │   ├── 2.png
      │   │   └── 3.png
      │   └── feature_graphic.png (1024x500)
      └── ...
```

#### Step 2.2 - Prepare App Metadata

**name.txt:**
```
Fencing Scores
```

**summary.txt:**
```
Pool/round management for fencing competitions
```

**description.txt:**
```
Fencing Scores is an open-source app for managing fencing pools and rounds.

Features:
• Dynamic round management (1-5 rounds)
• Participant name tracking
• Bout result matrix editor
• QR code generation for quick participant input
• CSV import/export for data backup
• Automatic per-round backups
• Merged brackets, knockout, and final ranking support

Perfect for fencing tournament organizers and coaches.

Source code: https://github.com/leodp/Fencing_scores
License: GNU GPLv3
```

#### Step 2.3 - Prepare Screenshots (if submitting directly to F-Droid)
- 3-5 screenshots showing key app screens (480×854 PNG format)
- Feature graphic (1024×500 PNG format)
- Screenshots should highlight key features

#### Step 2.4 - Submit to F-Droid

**Option A: Automatic Submission (Recommended)**
F-Droid automatically tracks GitHub releases. Steps:
1. Ensure repository has proper structure (verified ✅)
2. Add `fastlane/metadata/android/` directory with app metadata
3. F-Droid bot will auto-build after tag is pushed
4. Wait for review (typically 1-2 weeks)

**Option B: Manual Submission**
1. Visit https://f-droid.org
2. Look for "Submit an App" or similar link
3. Fill out submission form with:
   - App name: Fencing Scores
   - GitHub URL: https://github.com/leodp/Fencing_scores
   - Description: (from description.txt above)
   - Screenshots: (if required)
   - License: GNU GPLv3
4. Submit and wait for review

### Phase 3: Maintenance (Ongoing)

#### Step 3.1 - Establish Release Cadence
- Version numbering: `versionCode` always increments, `versionName` follows semantic versioning
- Tag each release on GitHub: `v2.23`, `v2.24`, etc.
- Add release notes to GitHub Releases describing changes
- F-Droid will automatically build and publish new versions

#### Step 3.2 - Update README with F-Droid Link
Once published, update README.md with F-Droid download link

#### Step 3.3 - Monitor F-Droid Build Status
- https://f-droid.org/wiki/page/Build_Server_Setup
- Check build status after each GitHub release
- Respond to any build failures

## Current Status

### ✅ Completed
- GNU GPLv3 license applied
- LICENSE file in repository
- Source code on public GitHub
- CHANGELOG.md with version history
- All dependencies are open-source
- Debug/test files removed from git

### ⏳ To Do
- [ ] Create release signing keystore (production key)
- [ ] Update app version to 2.23
- [ ] Update/enhance README.md with full feature list
- [ ] Create GitHub Release v2.23
- [ ] Set up fastlane metadata structure
- [ ] Create app screenshots
- [ ] Submit to F-Droid
- [ ] Verify auto-build on F-Droid
- [ ] Update README with F-Droid download link

## Files to Create/Update

| File | Action | Details |
|------|--------|---------|
| README.md | Update | Add features, F-Droid link, build instructions |
| app/build.gradle | Update | Increment versionCode to 13, versionName to "2.23" |
| fastlane/metadata/android/en-US/* | Create | App metadata for F-Droid |
| .keystore | Create | Release signing key (store securely, not in git) |
| GitHub Releases | Create | Tag v2.23 with signed APK |

## Security Notes

- **Keystore Protection**: Store release keystore outside git repo, use secure backup
- **Credentials**: Never commit credentials or keystore passwords to git
- **Version Control**: Keep versionCode strictly incrementing
- **Signing Key**: Reuse same signing key for all future updates (F-Droid requirement)

## Timeline Estimate

- Phase 1 (App Release): 1-2 days
- Phase 2 (F-Droid Submission): 1 day  
- Phase 3 (Review & Approval): 1-2 weeks (F-Droid review cycle)

**Total: ~2-3 weeks until app appears on F-Droid**

## References

- F-Droid: https://f-droid.org
- F-Droid Wiki: https://f-droid.org/wiki
- F-Droid Build Server: https://f-droid.org/wiki/page/Build_Server_Setup
- Android Publishing: https://developer.android.com/studio/publish
- Fastlane Metadata: https://docs.fastlane.tools/actions/supply/
