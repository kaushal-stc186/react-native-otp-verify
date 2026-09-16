package com.phonesmsretriever;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.telephony.TelephonyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.WritableArray;
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name = PhoneSmsRetrieverModule.NAME)
public class PhoneSmsRetrieverModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ActivityEventListener {
    public static final String NAME = "PhoneSmsRetriever";
    private static final String TAG = PhoneSmsRetrieverModule.class.getSimpleName();
    private static final int RESOLVE_HINT = 11001;
    private static final int RESOLVE_HINT_LEGACY = 11002;
    // CredentialsApi.ACTIVITY_RESULT_OTHER_ACCOUNT / NO_HINTS_AVAILABLE
    private static final int LEGACY_RESULT_OTHER_ACCOUNT = 1001;
    private static final int LEGACY_RESULT_NO_HINTS = 1002;

    private Promise requestHintCallback;
    private boolean allowLegacyFallback = true;
    private final ReactApplicationContext reactContext;
    private BroadcastReceiver mReceiver;
    private boolean isReceiverRegistered = false;

    public PhoneSmsRetrieverModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        mReceiver = new OtpBroadcastReceiver(reactContext);
        getReactApplicationContext().addLifecycleEventListener(this);
        registerReceiverIfNecessary(mReceiver);
        reactContext.addActivityEventListener(this);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    /**
     * New Phone Number Hint first, then legacy Credentials HintRequest as fallback
     * when the new API fails before showing UI (not when the user dismisses it).
     */
    @ReactMethod
    public void requestHint(Promise promise) {
        allowLegacyFallback = true;
        requestPhoneHintInternal(promise);
    }

    /**
     * New Phone Number Hint API only (no legacy fallback).
     */
    @ReactMethod
    public void requestPhoneHint(Promise promise) {
        allowLegacyFallback = false;
        requestPhoneHintInternal(promise);
    }

    /**
     * Legacy Smart Lock / Credentials HintRequest only.
     */
    @ReactMethod
    public void requestLegacyPhoneHint(Promise promise) {
        if (!beginHintRequest(promise, false)) {
            return;
        }

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            rejectHint("No Activity Found", "Current Activity Null.");
            return;
        }

        if (isSimUnavailable()) {
            rejectHint("No Sim Avaiable", "Simcard is not available");
            return;
        }

        requestLegacyHint(currentActivity);
    }

    private boolean beginHintRequest(Promise promise, boolean legacyFallback) {
        if (requestHintCallback != null) {
            promise.reject("HINT_IN_PROGRESS", "A phone hint request is already in progress");
            return false;
        }
        allowLegacyFallback = legacyFallback;
        requestHintCallback = promise;
        return true;
    }

    private void requestPhoneHintInternal(Promise promise) {
        if (!beginHintRequest(promise, allowLegacyFallback)) {
            return;
        }

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            rejectHint("No Activity Found", "Current Activity Null.");
            return;
        }

        if (isSimUnavailable()) {
            rejectHint("No Sim Avaiable", "Simcard is not available");
            return;
        }

        try {
            GetPhoneNumberHintIntentRequest request = GetPhoneNumberHintIntentRequest.builder().build();
            Identity.getSignInClient(currentActivity)
                    .getPhoneNumberHintIntent(request)
                    .addOnSuccessListener(result -> {
                        try {
                            currentActivity.startIntentSenderForResult(
                                    result.getIntentSender(), RESOLVE_HINT, null, 0, 0, 0);
                        } catch (Exception e) {
                            Log.e(TAG, "Launching the PendingIntent failed", e);
                            if (allowLegacyFallback) {
                                Log.e(TAG, "trying legacy hint");
                                requestLegacyHint(currentActivity);
                            } else {
                                rejectHint("HINT_LAUNCH_FAILED", "Launching the PendingIntent failed");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Phone Number Hint failed", e);
                        if (allowLegacyFallback) {
                            Log.e(TAG, "Phone Number Hint failed, trying legacy hint", e);
                            requestLegacyHint(currentActivity);
                        } else {
                            rejectHint(
                                    "HINT_UNAVAILABLE",
                                    e.getMessage() != null ? e.getMessage() : "Phone number hint unavailable");
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Phone Number Hint threw", e);
            if (allowLegacyFallback) {
                Log.e(TAG, "Phone Number Hint threw, trying legacy hint", e);
                requestLegacyHint(currentActivity);
            } else {
                rejectHint(
                        "HINT_UNAVAILABLE",
                        e.getMessage() != null ? e.getMessage() : "Phone number hint unavailable");
            }
        }
    }

    /**
     * Legacy Credentials HintRequest via reflection so play-services-auth 21+
     * (Credentials removed) does not crash the app with NoClassDefFoundError.
     */
    private void requestLegacyHint(Activity currentActivity) {
        if (currentActivity == null) {
            rejectHint("No Activity Found", "Current Activity Null.");
            return;
        }
        if (requestHintCallback == null) {
            return;
        }

        try {
            Class<?> hintRequestClass = Class.forName(
                    "com.google.android.gms.auth.api.credentials.HintRequest");
            Class<?> builderClass = Class.forName(
                    "com.google.android.gms.auth.api.credentials.HintRequest$Builder");
            Class<?> credentialsClass = Class.forName(
                    "com.google.android.gms.auth.api.credentials.Credentials");

            Object builder = builderClass.getDeclaredConstructor().newInstance();
            builderClass.getMethod("setPhoneNumberIdentifierSupported", boolean.class)
                    .invoke(builder, true);
            Object hintRequest = builderClass.getMethod("build").invoke(builder);

            Object credentialsClient = credentialsClass
                    .getMethod("getClient", Context.class)
                    .invoke(null, currentActivity);
            Object pendingIntent = credentialsClient.getClass()
                    .getMethod("getHintPickerIntent", hintRequestClass)
                    .invoke(credentialsClient, hintRequest);
            Object intentSender = pendingIntent.getClass()
                    .getMethod("getIntentSender")
                    .invoke(pendingIntent);

            currentActivity.startIntentSenderForResult(
                    (android.content.IntentSender) intentSender,
                    RESOLVE_HINT_LEGACY,
                    null,
                    0,
                    0,
                    0);
            Log.d(TAG, "Legacy phone hint picker launched");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            Log.e(TAG, "Legacy Credentials API not on classpath (play-services-auth 21+)", e);
            rejectHint(
                    "HINT_UNAVAILABLE",
                    "Legacy phone hint unavailable on this Play Services Auth version");
        } catch (Throwable e) {
            Log.e(TAG, "Legacy hint picker failed", e);
            rejectHint(
                    "HINT_UNAVAILABLE",
                    e.getMessage() != null ? e.getMessage() : "Phone number hint unavailable");
        }
    }

    private String getLegacyCredentialId(Intent data) {
        try {
            Class<?> credentialClass = Class.forName(
                    "com.google.android.gms.auth.api.credentials.Credential");
            String extraKey = (String) credentialClass.getField("EXTRA_KEY").get(null);
            Object credential = data.getParcelableExtra(extraKey);
            if (credential == null) {
                return null;
            }
            return (String) credentialClass.getMethod("getId").invoke(credential);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to read legacy credential", e);
            return null;
        }
    }

    private void resolveHint(String phoneNumber) {
        if (requestHintCallback == null) {
            return;
        }
        Promise promise = requestHintCallback;
        requestHintCallback = null;
        promise.resolve(phoneNumber);
    }

    private void rejectHint(String code, String message) {
        if (requestHintCallback == null) {
            return;
        }
        Promise promise = requestHintCallback;
        requestHintCallback = null;
        promise.reject(code, message);
    }

    public boolean isSimUnavailable() {
        try {
            Activity currentActivity = getCurrentActivity();
            if (currentActivity == null) {
                return true;
            }
            TelephonyManager telephonyManager =
                    (TelephonyManager) currentActivity.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager == null) {
                return true;
            }
            return telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY;
        } catch (Exception e) {
            return true;
        }
    }

    @ReactMethod
    public void getOtp(Promise promise) {
        requestOtp(promise);
    }

    @ReactMethod
    public void getHash(Promise promise) {
        try {
            AppSignatureHelper helper = new AppSignatureHelper(reactContext);
            ArrayList<String> signatures = helper.getAppSignatures();
            WritableArray arr = Arguments.createArray();
            for (String s : signatures) {
                arr.pushString(s);
            }
            promise.resolve(arr);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverIfNecessary(BroadcastReceiver receiver) {
        if (isReceiverRegistered || receiver == null) {
            return;
        }
        Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(
                        receiver,
                        new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                        SmsRetriever.SEND_PERMISSION,
                        null,
                        Context.RECEIVER_EXPORTED
                );
            } else {
                activity.registerReceiver(
                        receiver,
                        new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                );
            }
            Log.d(TAG, "Receiver Registered");
            isReceiverRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register SMS receiver", e);
        }
    }

    private void requestOtp(final Promise promise) {
        final AtomicBoolean settled = new AtomicBoolean(false);
        SmsRetrieverClient client = SmsRetriever.getClient(reactContext);
        Task<Void> task = client.startSmsRetriever();

        task.addOnSuccessListener(aVoid -> {
            if (settled.compareAndSet(false, true)) {
                Log.d(TAG, "started sms listener");
                promise.resolve(true);
            }
        });

        task.addOnFailureListener(e -> {
            if (settled.compareAndSet(false, true)) {
                Log.e(TAG, "Could not start sms listener", e);
                promise.reject("SMS_RETRIEVER_FAILED", e.getMessage(), e);
            }
        });

        task.addOnCanceledListener(() -> {
            if (settled.compareAndSet(false, true)) {
                Log.e(TAG, "sms listener cancelled");
                promise.reject("SMS_RETRIEVER_CANCELLED", "SMS retriever was cancelled");
            }
        });
    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        if (!isReceiverRegistered || receiver == null) {
            return;
        }
        Activity activity = getCurrentActivity();
        if (activity == null) {
            // Activity gone; clear flag so a later resume can register again.
            isReceiverRegistered = false;
            return;
        }
        try {
            activity.unregisterReceiver(receiver);
            Log.d(TAG, "Receiver UnRegistered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister SMS receiver", e);
        } finally {
            isReceiverRegistered = false;
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != RESOLVE_HINT && requestCode != RESOLVE_HINT_LEGACY) {
            return;
        }

        if (requestCode == RESOLVE_HINT) {
            // New Phone Number Hint UI was shown (or attempted).
            // RESULT_OK (-1)      → user picked a number
            // RESULT_CANCELED (0) → user dismissed — do NOT try legacy
            // other               → treat as unavailable; may try legacy
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    String phoneNumber = Identity.getSignInClient(activity).getPhoneNumberFromIntent(data);
                    resolveHint(phoneNumber);
                } catch (Exception e) {
                    Log.e(TAG, "New hint result parse failed", e);
                    rejectHint(
                            "HINT_UNAVAILABLE",
                            e.getMessage() != null ? e.getMessage() : "Phone number hint unavailable");
                }
                return;
            }

            if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "New hint dismissed by user (resultCode=0)");
                rejectHint("HINT_CANCELLED", "Phone number hint cancelled by user");
                return;
            }

            Log.e(TAG, "New hint unavailable (resultCode=" + resultCode + ")");
            if (allowLegacyFallback) {
                Log.e(TAG, "trying legacy hint");
                requestLegacyHint(activity);
            } else {
                rejectHint("HINT_UNAVAILABLE", "Phone number hint unavailable");
            }
            return;
        }

        // Legacy Credentials picker result
        if (resultCode == Activity.RESULT_CANCELED || resultCode == LEGACY_RESULT_OTHER_ACCOUNT) {
            rejectHint("HINT_CANCELLED", "Phone number hint cancelled by user");
            return;
        }

        if (resultCode == LEGACY_RESULT_NO_HINTS) {
            rejectHint("HINT_UNAVAILABLE", "No phone number hints available on this device");
            return;
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            rejectHint("HINT_UNAVAILABLE", "Phone number hint unavailable");
            return;
        }

        try {
            String phoneNumber = getLegacyCredentialId(data);
            if (phoneNumber != null) {
                resolveHint(phoneNumber);
            } else {
                rejectHint("HINT_UNAVAILABLE", "Phone number hint unavailable");
            }
        } catch (Throwable e) {
            rejectHint(
                    "HINT_UNAVAILABLE",
                    e.getMessage() != null ? e.getMessage() : "Phone number hint unavailable");
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    @Override
    public void onHostResume() {
        registerReceiverIfNecessary(mReceiver);
    }

    @Override
    public void onHostPause() {
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onHostDestroy() {
        unregisterReceiver(mReceiver);
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Required for RN NativeEventEmitter.
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Required for RN NativeEventEmitter.
    }
}
