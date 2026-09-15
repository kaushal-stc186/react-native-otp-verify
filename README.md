# react-native-phone-sms-retriever

Android [SMS Retriever](https://developers.google.com/identity/sms-retriever/overview) OTP auto-read and Google phone-number hint for React Native.

**Platform:** Android only. On iOS, methods log `Not Supported on iOS` and return empty/false — they do not throw.

Fork of [react-native-otp-verify](https://github.com/faizalshap/react-native-otp-verify). Native module: `PhoneSmsRetriever` (`com.phonesmsretriever`).

## Install

```bash
npm install react-native-phone-sms-retriever
# or
yarn add react-native-phone-sms-retriever
```

Rebuild the native app after install (autolinking, RN ≥ 0.60). Does not work in Expo Go — use a dev client or bare workflow.

## SMS OTP

### 1. Get your app hash

Run once on a debug/release build of **your** app (hashes differ by signing key):

```javascript
import { getHash } from 'react-native-phone-sms-retriever';

getHash().then(console.log); // e.g. ["FA+9qCX9VSu"]
```

Give this string to whoever sends the OTP SMS.

### 2. SMS body format

Google requires this pattern ([docs](https://developers.google.com/identity/sms-retriever/verify)):

```
<#> Your OTP is 1234
FA+9qCX9VSu
```

- First line: message text containing the OTP digits.
- Second line: 11-character app hash from `getHash()`.
- `<#>` prefix is required.

SMS must arrive on the device within ~5 minutes of starting the listener.

### 3. Listen in JS

```javascript
import { useEffect } from 'react';
import { startOtpListener, removeListener } from 'react-native-phone-sms-retriever';

useEffect(() => {
  startOtpListener((message) => {
    if (message === 'Timeout Error.') {
      // no SMS within retriever window — restart listener or ask user to re-request OTP
      return;
    }
    const otp = /(\d{4})/.exec(message)?.[1];
    if (otp) setOtp(otp);
  });
  return () => removeListener();
}, []);
```

Or use the hook (starts listener on mount, calls `removeListener` on unmount):

```javascript
import { useOtpVerify } from 'react-native-phone-sms-retriever';

const { hash, otp, message, timeoutError, startListener, stopListener } =
  useOtpVerify({ numberOfDigits: 4 });
```

Event name (if you use `addListener` directly): `com.phonesmsretriever:otpReceived`.

## Phone number hint

Shows a Google picker so the user can select a number instead of typing it.

```javascript
import { requestHint } from 'react-native-phone-sms-retriever';

requestHint()
  .then((phone) => setPhone(phone))
  .catch((err) => {
    // err.code — see table below
  });
```

| Method | Behavior |
|--------|----------|
| `requestHint()` | New [Phone Number Hint](https://developers.google.com/identity/phone-number-hint/android) API, then legacy Credentials picker if that fails |
| `requestPhoneHint()` | New API only |
| `requestLegacyPhoneHint()` | Legacy Credentials `HintRequest` only |

### When hint fails (not a bug)

Many SIMs do not store the phone number (MSISDN). Play Services then returns errors like `ApiException: 16: No phone number is found on this device`. **Use manual entry** — there is no client-side fix.

### Hint error codes (`err.code`)

| Code | When | What to do |
|------|------|------------|
| `No Sim Avaiable` | SIM not `SIM_STATE_READY` | Ask user to check SIM |
| `No Activity Found` | No foreground Activity | Call from a mounted screen, not at app launch |
| `HINT_CANCELLED` | User dismissed picker | Manual entry |
| `HINT_UNAVAILABLE` | No number on device, legacy API missing on classpath, or parse failed | Manual entry |
| `HINT_LAUNCH_FAILED` | Could not open hint UI (new API only path) | Retry or manual entry |

Legacy picker needs `com.google.android.gms.auth.api.credentials.HintRequest`, removed in `play-services-auth` **21.0.0+**. This package pins **20.7.0** (see below).

## play-services-auth 20.7.0

This library's `android/build.gradle` already depends on and **forces** `com.google.android.gms:play-services-auth:20.7.0` app-wide:

```gradle
implementation "com.google.android.gms:play-services-auth:20.7.0"

rootProject.allprojects {
    configurations.all {
        resolutionStrategy {
            force "com.google.android.gms:play-services-auth:20.7.0"
        }
    }
}
```

If Firebase or Google Sign-In still resolves **21.x** in your app, add the same `resolutionStrategy` block to your Android **root** `build.gradle`, then rebuild and retest sign-in flows.

## Use with `react-native-otp-verify`

Both packages can be installed together. They use different Java packages and native module names, so you avoid dex duplicate errors on `AppSignatureHelper`:

| Package | Native module | Java package |
|---------|---------------|--------------|
| `react-native-phone-sms-retriever` | `PhoneSmsRetriever` | `com.phonesmsretriever` |
| `react-native-otp-verify` | `OtpVerify` | `com.faizal.OtpVerify` |

Import SMS/hint APIs from whichever package your screen uses — do not assume both expose the same JS module name.

## API

```typescript
getHash(): Promise<string[]>
getOtp(): Promise<boolean>
startOtpListener(handler: (message: string) => void): Promise<EmitterSubscription>
addListener(handler: (message: string) => void): EmitterSubscription
removeListener(): void

requestHint(): Promise<string>
requestPhoneHint(): Promise<string>
requestLegacyPhoneHint(): Promise<string>

useOtpVerify({ numberOfDigits?: number })
// → { hash, otp, message, timeoutError, startListener, stopListener }
```

## Debug

```bash
adb logcat -s PhoneSmsRetrieverModule:D PhoneSmsRetrieverModule:E SMS:D
```

## License

MIT
