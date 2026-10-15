# JournalPix GitHub Actions

This repository uses GitHub Actions for CI/CD automation.

## Workflows

### 1. Continuous Integration (`ci.yml`)
- **Trigger**: Push or PR to `github` or `main` branches
- **Jobs**:
  - Test Rust backend
  - Test Android app
  - Build Android debug APK
- **Artifacts**: Debug APK available for download

### 2. Android Build (`android-build.yml`)
- **Trigger**: Push or PR to `github` or `main` branches (Android files only)
- **Jobs**:
  - Build debug APK
  - Build release APK (unsigned)
  - Create GitHub Release with APKs
- **Artifacts**: Both debug and release APKs available

### 3. Android Release (`android-release.yml`)
- **Trigger**: Push of tag `v*` OR manual workflow dispatch
- **Jobs**:
  - Build signed release APK (if signing config provided)
  - Create GitHub Release
- **Requires**: Android signing secrets configured

### 4. Backend Build (`backend-build.yml`)
- **Trigger**: Push or PR to `github` or `main` branches (backend files only)
- **Jobs**:
  - Test Rust code
  - Build release binary
  - Build and push Docker image (if on main/github branches)
- **Artifacts**: Rust binary and Docker image

## Secrets Required

For full CI/CD functionality, set these GitHub secrets:

### Android Signing (for release builds)
- `ANDROID_KEYSTORE_BASE64` - Base64 encoded keystore file
- `ANDROID_SIGNING_KEY_ALIAS` - Key alias
- `ANDROID_SIGNING_KEY_PASSWORD` - Key password
- `ANDROID_SIGNING_STORE_PASSWORD` - Keystore password

### Docker Hub (for backend image pushes)
- `DOCKER_HUB_USERNAME` - Docker Hub username
- `DOCKER_HUB_TOKEN` - Docker Hub access token

## How to Create a Release

### Option 1: Push a tag
```bash
git tag v1.0.0
git push origin v1.0.0
```

### Option 2: Manual workflow dispatch
1. Go to Actions tab
2. Select "Android Release" workflow
3. Click "Run workflow"
4. Enter version number (e.g., 1.0.0)

## Artifacts

All workflows upload artifacts that can be downloaded:
- Debug APKs: Available for testing, expire after 7 days
- Release APKs: Available for distribution, expire after 30-90 days
- Backend binaries: Available for deployment

## Notes

- Workflows only run when relevant files change (path filtering)
- The `github` branch is the primary working branch
- All changes are tested before release
- Release workflows create GitHub Releases with downloadable APKs
