# Third-Party Notices and Licenses

This project incorporates or depends on components from third-party open-source projects and proprietary SDKs. Below is a summary of their licenses and notices.

---

## 1. BlackBox Container Engine

- **Source**: [Blacks-BlackBox](https://github.com/Black00Z/Blacks-BlackBox) (pinned commit `40282a7bf4500948cfd598fc67e6e63114b26dd9`)
- **License**: Apache License, Version 2.0
- **Location**: `vendor/blackbox/`
- **Notice**: Original license is retained at `vendor/blackbox/LICENSE`. Modifications made for Campus Atlas are documented in `vendor/blackbox/NOTICE-ATLAS.md` and marked in the respective source files.

---

## 2. xDL

- **Source**: [hexhacking/xDL](https://github.com/hexhacking/xDL)
- **License**: MIT License
- **Copyright**: Copyright (c) 2020-2024 HexHacking Team
- **Location**: `vendor/blackbox/Bcore/src/main/cpp/xdl/`
- **License Text**: See `vendor/blackbox/Bcore/src/main/cpp/xdl/LICENSE.xDL`.

---

## 3. Dobby Hook Framework

- **Source**: [jmpews/Dobby](https://github.com/jmpews/Dobby)
- **License**: Apache License, Version 2.0
- **Copyright**: Copyright (c) 2017-2024 jmpews
- **Location**: `vendor/blackbox/Bcore/src/main/cpp/Dobby/`
- **License Text**: See `vendor/blackbox/Bcore/src/main/cpp/Dobby/LICENSE`.

---

## 4. Google ML Kit Text Recognition

- **Component**: `com.google.mlkit:text-recognition-chinese:16.0.1`
- **License**: Provided subject to the [Google APIs Terms of Service](https://developers.google.com/terms) and [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms).
- **Notice**: Used for on-device text recognition. Model assets are downloaded and packaged according to Google's distribution terms.

---

## 5. AutoNavi (AMap) Maps SDK

- **Component**: `com.amap.api:3dmap-location-search:10.1.200_loc6.4.9_sea9.7.4`
- **License**: Proprietary commercial SDK provided under the [AutoNavi Open Platform Service Agreement](https://lbs.amap.com/pages/terms/).
- **Notice**:
  - The AutoNavi SDK is **not** covered by this project's open-source license.
  - This repository provides only Gradle dependency references and does **not** distribute AutoNavi binary artifacts.
  - Users must register their own developer accounts on the AutoNavi Open Platform and configure their own API Key in `local.properties`.

---

## 6. FreeReflection

- **Component**: `com.github.tiann:FreeReflection:3.2.2`
- **Source**: [tiann/FreeReflection](https://github.com/tiann/FreeReflection)
- **License**: Apache License, Version 2.0
- **Copyright**: Copyright (c) 2019-2024 Tiann

---

## 7. toml4j

- **Component**: `com.moandjiezana.toml:toml4j:0.7.2`
- **Source**: [mwanji/toml4j](https://github.com/mwanji/toml4j)
- **License**: MIT License
- **Copyright**: Copyright (c) 2015 Francois Blanc

---

## 8. Test Dependencies

- **JUnit 4**: `junit:junit:4.13.2` — Eclipse Public License 1.0 (EPL-1.0)
- **Robolectric**: `org.robolectric:robolectric:4.16` — MIT License
