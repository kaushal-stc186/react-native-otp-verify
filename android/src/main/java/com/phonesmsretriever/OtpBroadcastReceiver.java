package com.phonesmsretriever;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

public class OtpBroadcastReceiver extends BroadcastReceiver {

    private final ReactApplicationContext mContext;

    private static final String EVENT = "com.phonesmsretriever:otpReceived";
    private static final String TAG = "SMS";

    public OtpBroadcastReceiver(ReactApplicationContext context) {
        mContext = context;
    }

    private boolean canEmit() {
        return mContext != null && mContext.hasActiveCatalystInstance();
    }

    private void emit(String payload) {
        if (!canEmit()) {
            return;
        }
        mContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(EVENT, payload);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
        if (status == null) {
            return;
        }

        switch (status.getStatusCode()) {
            case CommonStatusCodes.SUCCESS:
                String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                if (message != null) {
                    Log.d(TAG, message);
                }
                emit(message);
                break;
            case CommonStatusCodes.TIMEOUT:
                Log.d(TAG, "Timeout error");
                emit("Timeout Error.");
                break;
            default:
                break;
        }
    }
}
