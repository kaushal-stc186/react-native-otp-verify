import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import { useEffect, useState } from 'react';

const LINKING_ERROR =
  `The package 'react-native-phone-sms-retriever' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const EVENT = 'com.phonesmsretriever:otpReceived';

const RNPhoneSmsRetriever = NativeModules.PhoneSmsRetriever
  ? NativeModules.PhoneSmsRetriever
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const eventEmitter = new NativeEventEmitter(RNPhoneSmsRetriever);

interface PhoneSmsRetriever {
  getOtp: () => Promise<boolean>;
  getHash: () => Promise<string[]>;
  requestHint: () => Promise<string>;
  requestPhoneHint: () => Promise<string>;
  requestLegacyPhoneHint: () => Promise<string>;
  startOtpListener: (
    handler: (value: string) => any
  ) => Promise<import('react-native').EmitterSubscription>;
  addListener: (
    handler: (value: string) => any
  ) => import('react-native').EmitterSubscription;
  removeListener: () => void;
}

export async function getOtp(): Promise<boolean> {
  if (Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return false;
  }
  return RNPhoneSmsRetriever.getOtp();
}

export function startOtpListener(
  handler: (value: string) => any
): Promise<import('react-native').EmitterSubscription> {
  return getOtp().then(() => addListener(handler));
}

export const useOtpVerify = ({ numberOfDigits } = { numberOfDigits: 0 }) => {
  const [message, setMessage] = useState<string | null>(null);
  const [otp, setOtp] = useState<string | null>(null);
  const [timeoutError, setTimeoutError] = useState<boolean>(false);
  const [hash, setHash] = useState<string[] | null>([]);

  const handleMessage = (response: string) => {
    if (response === 'Timeout Error.') {
      setTimeoutError(true);
    } else {
      setMessage(response);
      if (numberOfDigits && response) {
        const otpDigits = new RegExp(`(\\d{${numberOfDigits}})`, 'g').exec(response);
        if (otpDigits && otpDigits[1]) setOtp(otpDigits[1]);
      }
    }
  };
  useEffect(() => {
    if (Platform.OS === 'ios') {
      console.warn('Not Supported on iOS');
      return;
    }
    getHash().then(setHash);
    startOtpListener(handleMessage);
    return () => {
      removeListener();
    };
  }, []);
  const startListener = () => {
    if (Platform.OS === 'ios') {
      console.warn('Not Supported on iOS');
      return;
    }
    setOtp('');
    setMessage('');
    startOtpListener(handleMessage);
  };
  const stopListener = () => {
    if (Platform.OS === 'ios') {
      console.warn('Not Supported on iOS');
      return;
    }
    removeListener();
  };
  return { otp, message, hash, timeoutError, stopListener, startListener };
};

export async function getHash(): Promise<string[]> {
  if (Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return [];
  }
  return RNPhoneSmsRetriever.getHash();
}

/** New Phone Number Hint first, then legacy Credentials hint as fallback. */
export async function requestHint(): Promise<string> {
  if (Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return '';
  }
  return RNPhoneSmsRetriever.requestHint();
}

/** New Phone Number Hint API only (no legacy fallback). */
export async function requestPhoneHint(): Promise<string> {
  if (Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return '';
  }
  return RNPhoneSmsRetriever.requestPhoneHint();
}

/** Legacy Smart Lock / Credentials HintRequest only. */
export async function requestLegacyPhoneHint(): Promise<string> {
  if (Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return '';
  }
  return RNPhoneSmsRetriever.requestLegacyPhoneHint();
}

export function addListener(
  handler: (value: string) => any
): import('react-native').EmitterSubscription {
  return eventEmitter.addListener(EVENT, handler);
}

export function removeListener(): void {
  return eventEmitter.removeAllListeners(EVENT);
}

const PhoneSmsRetrieverApi: PhoneSmsRetriever = {
  getOtp,
  getHash,
  addListener,
  removeListener,
  startOtpListener,
  requestHint,
  requestPhoneHint,
  requestLegacyPhoneHint,
};

export default PhoneSmsRetrieverApi;
