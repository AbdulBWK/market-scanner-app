# Market Scanner

Android companion app for the hourly market-signal scan. Polls `data/latest.json`
in this repo every ~30 minutes in the background and fires a local notification
when a new strong buy/sell signal is published. The home screen shows the
latest flagged signals, top movers, and key news.

`data/latest.json` is updated automatically by a scheduled Claude task after
each market scan. The Android app itself never talks to any market-data API
directly - it only reads this JSON file.

Not financial advice. See the in-app disclaimer.

## Build

A GitHub Actions workflow (`.github/workflows/build.yml`) builds a debug APK
on every push to `main` and uploads it as a build artifact. No local Android
SDK setup required.
