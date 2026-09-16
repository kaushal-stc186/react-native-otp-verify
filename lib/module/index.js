import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import { useCallback, useEffect, useRef, useState } from 'react';
const LINKING_ERROR = `The package 'react-native-phone-sms-retriever' doesn't seem to be linked. Make sure: \n\n` + Platform.select({
  ios: "- You have run 'pod install'\n",
  default: ''
}) + '- You rebuilt the app after installing the package\n' + '- You are not using Expo managed workflow\n';
const EVENT = 'com.phonesmsretriever:otpReceived';
const IOS_UNSUPPORTED = 'Not Supported on iOS';
const RNPhoneSmsRetriever = NativeModules.PhoneSmsRetriever ? NativeModules.PhoneSmsRetriever : new Proxy({}, {
  get() {
    throw new Error(LINKING_ERROR);
  }
});
const eventEmitter = new NativeEventEmitter(RNPhoneSmsRetriever);
function rejectIosUnsupported() {
  console.warn(IOS_UNSUPPORTED);
  return Promise.reject(new Error(IOS_UNSUPPORTED));
}
export async function getOtp() {
  if (Platform.OS === 'ios') {
    console.warn(IOS_UNSUPPORTED);
    return false;
  }
  return RNPhoneSmsRetriever.getOtp();
}
export function startOtpListener(handler) {
  return getOtp().then(() => addListener(handler));
}
export const useOtpVerify = ({
  numberOfDigits
} = {
  numberOfDigits: 0
}) => {
  const [message, setMessage] = useState(null);
  const [otp, setOtp] = useState(null);
  const [timeoutError, setTimeoutError] = useState(false);
  const [hash, setHash] = useState([]);
  const numberOfDigitsRef = useRef(numberOfDigits);
  numberOfDigitsRef.current = numberOfDigits;
  const handleMessage = useCallback(response => {
    if (response === 'Timeout Error.') {
      setTimeoutError(true);
      return;
    }
    setTimeoutError(false);
    setMessage(response);
    const digits = numberOfDigitsRef.current;
    if (digits && response) {
      const otpDigits = new RegExp(`(\\d{${digits}})`).exec(response);
      if (otpDigits && otpDigits[1]) {
        setOtp(otpDigits[1]);
      }
    }
  }, []);
  useEffect(() => {
    if (Platform.OS === 'ios') {
      console.warn(IOS_UNSUPPORTED);
      return;
    }
    getHash().then(setHash);
    startOtpListener(handleMessage);
    return () => {
      removeListener();
    };
  }, [handleMessage]);
  const startListener = useCallback(() => {
    if (Platform.OS === 'ios') {
      console.warn(IOS_UNSUPPORTED);
      return;
    }
    removeListener();
    setOtp('');
    setMessage('');
    setTimeoutError(false);
    startOtpListener(handleMessage);
  }, [handleMessage]);
  const stopListener = useCallback(() => {
    if (Platform.OS === 'ios') {
      console.warn(IOS_UNSUPPORTED);
      return;
    }
    removeListener();
  }, []);
  return {
    otp,
    message,
    hash,
    timeoutError,
    stopListener,
    startListener
  };
};
export async function getHash() {
  if (Platform.OS === 'ios') {
    console.warn(IOS_UNSUPPORTED);
    return [];
  }
  return RNPhoneSmsRetriever.getHash();
}

/** New Phone Number Hint first, then legacy Credentials hint as fallback. */
export async function requestHint() {
  if (Platform.OS === 'ios') {
    return rejectIosUnsupported();
  }
  return RNPhoneSmsRetriever.requestHint();
}

/** New Phone Number Hint API only (no legacy fallback). */
export async function requestPhoneHint() {
  if (Platform.OS === 'ios') {
    return rejectIosUnsupported();
  }
  return RNPhoneSmsRetriever.requestPhoneHint();
}

/** Legacy Smart Lock / Credentials HintRequest only. */
export async function requestLegacyPhoneHint() {
  if (Platform.OS === 'ios') {
    return rejectIosUnsupported();
  }
  return RNPhoneSmsRetriever.requestLegacyPhoneHint();
}
export function addListener(handler) {
  return eventEmitter.addListener(EVENT, handler);
}
export function removeListener() {
  return eventEmitter.removeAllListeners(EVENT);
}
const PhoneSmsRetrieverApi = {
  getOtp,
  getHash,
  addListener,
  removeListener,
  startOtpListener,
  requestHint,
  requestPhoneHint,
  requestLegacyPhoneHint
};
export default PhoneSmsRetrieverApi;
//# sourceMappingURL=index.js.map