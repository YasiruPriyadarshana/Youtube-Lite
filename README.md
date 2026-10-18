# YouTube Lite

A personal YouTube client for Android. It opens on Home (trending videos, no sign-in needed),
has a Subscriptions tab that asks you to sign in, and hides Shorts by default. Home can be
switched off in Settings, and then the app opens on Subscriptions.

## One-time Google Cloud setup

The app uses the official YouTube Data API. Subscriptions uses Google sign-in, which needs no
key: Google identifies the app by its package name plus the SHA-1 fingerprint of the key that
signs it. Home works without signing in, so it needs an **API key**, which you keep in
`secrets.properties` (git-ignored, never committed).

1. **Get your SHA-1.** Open this project in Android Studio, then in the Terminal tab run
   `.\gradlew signingReport` (Windows) and copy the `SHA1` line under `Variant: debug`.
2. **Create a project** at https://console.cloud.google.com (top bar, project picker, *New project*).
3. **Turn on the API.** *APIs & Services* > *Library*, search **YouTube Data API v3**, click *Enable*.
4. **Set up the consent screen.** *Google Auth Platform* (or *APIs & Services* > *OAuth consent screen*):
   - App name: YouTube Lite, your email as support and developer contact.
   - Audience: **External**, and leave it in **Testing**.
   - Under *Test users*, add your own Gmail and each friend's Gmail (up to 100).
   - Under *Data access* (scopes), add `.../auth/youtube` (needed so the app can like and dislike
     videos as well as read your feeds).
5. **Create the client.** *Clients* (or *Credentials* > *Create credentials* > *OAuth client ID*):
   - Application type: **Android**
   - Package name: `com.slsilverlight.youtubelite`
   - SHA-1: the value from step 1.

6. **Create the API key for Home, search and comments.** *APIs & Services* > *Credentials* > *Create credentials* > *API key*.
   Then edit the key:
   - Application restrictions: **Android apps**, add package `com.slsilverlight.youtubelite`
     with the same SHA-1 as step 5.
   - API restrictions: **Restrict key** to **YouTube Data API v3**.
7. **Add the key to the project.** Copy `secrets.properties.example` to `secrets.properties`
   in the project folder and paste the key after `YOUTUBE_API_KEY=`. Rebuild the app.

## Running it

Plug in your phone (or start an emulator with Google Play), then press **Run** in Android Studio.
To give it to friends, use *Build* > *Build App Bundle(s) / APK(s)* > *Build APK(s)* and send them
the APK. It's signed with your debug key, which matches the SHA-1 you registered.

## Good to know

- **Weekly sign-in:** while the Google project is in Testing mode, Google signs everyone out
  about every 7 days. Just sign in again.
- **Likes cost quota too:** each like or dislike uses 50 units.
- **Shared daily quota:** the API allows 10,000 units a day for everyone using the app together.
  A Subscriptions refresh costs roughly one unit per subscribed channel.
  Each search costs 101 units, so together everyone gets about 90 searches a day.
- **Shorts:** hidden using each channel's long-form uploads list, so it's accurate. If a channel
  doesn't have that list, videos of 3 minutes or less are treated as Shorts.
- **Home:** the API doesn't give apps your personal recommendations, so Home shows trending
  videos for your country, even when you're signed in.
- **Secrets:** `.gitignore` excludes `secrets.properties`, `local.properties`, keystores and
  Google JSON credential files. Only `secrets.properties.example` (no real key) is committed.

## Project layout

```
app/src/main/java/com/slsilverlight/youtubelite/
  MainActivity.kt          entry point and theme
  auth/GoogleAuth.kt       Google sign-in (youtube.readonly scope)
  data/YouTubeApi.kt       YouTube Data API endpoints and models
  data/YouTubeRepository.kt  builds the Subscriptions and Home feeds
  data/SettingsStore.kt    Show Shorts / Show Home switches
  data/AppSignature.kt     signing fingerprint sent with API-key requests
  ui/App.kt                sign-in screen, tabs and navigation
  ui/FeedScreen.kt         video list
  ui/PlayerScreen.kt       embedded YouTube player
  ui/SettingsScreen.kt     settings
```
