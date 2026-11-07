# 盐筋 / YanJing for iOS

Native Swift and SwiftUI app for iPhone and iPad, iOS 18 or later. NavigationStack, system tabs (including a search tab), searchable lists, forms, sheets, context menus and ShareLink adopt the platform’s appearance, including Liquid Glass on supported systems. The design follows [Apple’s current design resources](https://developer.apple.com/design/resources/) and [Liquid Glass adoption guidance](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). No compatibility appearance override is used. The CI runner selects its latest installed stable Xcode; the exact version is recorded with each build.

Includes all 16 text versions already present on Android: five Bible texts including Latin, Chinese and Arabic Quran, the Four Books, Heart Sutra, Diamond Sutra, Lotus Sutra, Tao Te Ching and Zhuangzi. Supports reference lookup, original wording, expressions and local Chinese BGE inference, context, edition comparisons, saved passages, sharing and per-corpus reading positions. The native iOS ranking is an initial Swift implementation; the Android retrieval evaluation scores do not measure the iOS implementation.

## Build on a Mac

Install Xcode 26 or later, its iOS platform, Node.js 22 or later and Homebrew. Xcode 27 is recommended when available for the current Apple SDK and design refinements.

```sh
bash ios/bootstrap.sh
open ios/YanJing.xcodeproj
```

Select the YanJing scheme and an iPhone or iPad simulator. `bootstrap.sh` installs XcodeGen if needed, generates the three language bundles, copies the exact Android resources and generates a regular native Xcode project. Resource preparation never downloads or rewrites original text. The generated `Corpus` folder is intentionally excluded from Git to avoid duplicating approximately 100 MB of existing data.

Microsoft’s official ONNX Swift package is pinned to the available stable `1.19.2` release. The model and vector indexes are the same byte-identical files as Android; iOS verifies the model SHA-256 before inference. Android retains its existing runtime. No runtime model downloads, account, tracking or query upload is used. The privacy manifest declares local UserDefaults use.

## Install the unsigned IPA without a paid developer membership

Download `yanjing-1.7.0-ios-unsigned.ipa` from the [Release](https://github.com/KiRenk0/yanjing-bible/releases/latest). Import it into [AltStore](https://faq.altstore.io/) or a compatible local signing tool such as Sideloadly, and follow that tool’s own instructions to sign it with your personal free Apple ID. A paid Apple Developer membership is not required for this personal signing flow. Never send signing credentials to this repository.

The attached IPA is an unsigned arm64 device build, not an App Store, TestFlight or enterprise-signed package. It cannot be installed by tapping it. Free provisioning has Apple-imposed expiry and device/app limits; refresh using your signing tool. This project supplies no shared certificate or bypass. The simulator ZIP runs only in an iOS simulator on a Mac.

## Languages and accessibility

English, Simplified Chinese and Traditional Chinese follow system preferences, with an immediate in-app override in Settings. Changing the interface leaves source passages and selected editions intact. English interface support does not change the Chinese model’s language capabilities; use Original text or references for English queries.

System controls support VoiceOver, native sheets, context menus, dark appearance and system accessibility behavior. Passage typography uses Dynamic Type scaling in addition to the user’s reading-size setting. Arabic original text retains RTL layout and source spelling. Source-specific licenses, including CBETA’s noncommercial share-alike conditions and the CrossWire mapping license, are bundled under Licenses and sources.

## Verification and release

The `iOS native build` GitHub Action runs actual model/tokenizer, corpus/reference/mapping and persistence tests, then launches the three interface languages in a simulator and records screenshots. Only successful tests proceed to device archiving. It produces an unsigned IPA, simulator app, Xcode project, checksums, source commit, Xcode version and test result bundle. Physical-device sideload installation still requires verification using a user-owned device and signing tool.

Translations are maintained in `localization/ios.json`; run `node scripts/generate-localizations.mjs`. Android translations are in `localization/android.json` and `localization/formatting.json`. Validate key and placeholder coverage with `node scripts/validate-localizations.mjs`.
