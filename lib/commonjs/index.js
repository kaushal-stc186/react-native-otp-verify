"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.addListener = addListener;
exports.default = void 0;
exports.getHash = getHash;
exports.getOtp = getOtp;
exports.removeListener = removeListener;
exports.requestHint = requestHint;
exports.requestLegacyPhoneHint = requestLegacyPhoneHint;
exports.requestPhoneHint = requestPhoneHint;
exports.startOtpListener = startOtpListener;
exports.useOtpVerify = void 0;
var _reactNative = require("react-native");
var _react = require("react");
const LINKING_ERROR = `The package 'react-native-phone-sms-retriever' doesn't seem to be linked. Make sure: \n\n` + _reactNative.Platform.select({
  ios: "- You have run 'pod install'\n",
  default: ''
}) + '- You rebuilt the app after installing the package\n' + '- You are not using Expo managed workflow\n';
const EVENT = 'com.phonesmsretriever:otpReceived';
const RNPhoneSmsRetriever = _reactNative.NativeModules.PhoneSmsRetriever ? _reactNative.NativeModules.PhoneSmsRetriever : new Proxy({}, {
  get() {
    throw new Error(LINKING_ERROR);
  }
});
const eventEmitter = new _reactNative.NativeEventEmitter(RNPhoneSmsRetriever);
async function getOtp() {
  if (_reactNative.Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return false;
  }
  return RNPhoneSmsRetriever.getOtp();
}
function startOtpListener(handler) {
  return getOtp().then(() => addListener(handler));
}
const useOtpVerify = ({
  numberOfDigits
} = {
  numberOfDigits: 0
}) => {
  const [message, setMessage] = (0, _react.useState)(null);
  const [otp, setOtp] = (0, _react.useState)(null);
  const [timeoutError, setTimeoutError] = (0, _react.useState)(false);
  const [hash, setHash] = (0, _react.useState)([]);
  const handleMessage = response => {
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
  (0, _react.useEffect)(() => {
    if (_reactNative.Platform.OS === 'ios') {
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
    if (_reactNative.Platform.OS === 'ios') {
      console.warn('Not Supported on iOS');
      return;
    }
    setOtp('');
    setMessage('');
    startOtpListener(handleMessage);
  };
  const stopListener = () => {
    if (_reactNative.Platform.OS === 'ios') {
      console.warn('Not Supported on iOS');
      return;
    }
    removeListener();
  };
  return {
    otp,
    message,
    hash,
    timeoutError,
    stopListener,
    startListener
  };
};
exports.useOtpVerify = useOtpVerify;
async function getHash() {
  if (_reactNative.Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return [];
  }
  return RNPhoneSmsRetriever.getHash();
}

/** New Phone Number Hint first, then legacy Credentials hint as fallback. */
async function requestHint() {
  if (_reactNative.Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return '';
  }
  return RNPhoneSmsRetriever.requestHint();
}

/** New Phone Number Hint API only (no legacy fallback). */
async function requestPhoneHint() {
  if (_reactNative.Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return '';
  }
  return RNPhoneSmsRetriever.requestPhoneHint();
}

/** Legacy Smart Lock / Credentials HintRequest only. */
async function requestLegacyPhoneHint() {
  if (_reactNative.Platform.OS === 'ios') {
    console.warn('Not Supported on iOS');
    return '';
  }
  return RNPhoneSmsRetriever.requestLegacyPhoneHint();
}
function addListener(handler) {
  return eventEmitter.addListener(EVENT, handler);
}
function removeListener() {
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
var _default = exports.default = PhoneSmsRetrieverApi;
//# sourceMappingURL=index.js.map