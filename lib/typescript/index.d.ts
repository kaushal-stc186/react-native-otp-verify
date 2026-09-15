interface PhoneSmsRetriever {
    getOtp: () => Promise<boolean>;
    getHash: () => Promise<string[]>;
    requestHint: () => Promise<string>;
    requestPhoneHint: () => Promise<string>;
    requestLegacyPhoneHint: () => Promise<string>;
    startOtpListener: (handler: (value: string) => any) => Promise<import('react-native').EmitterSubscription>;
    addListener: (handler: (value: string) => any) => import('react-native').EmitterSubscription;
    removeListener: () => void;
}
export declare function getOtp(): Promise<boolean>;
export declare function startOtpListener(handler: (value: string) => any): Promise<import('react-native').EmitterSubscription>;
export declare const useOtpVerify: ({ numberOfDigits }?: {
    numberOfDigits: number;
}) => {
    otp: string | null;
    message: string | null;
    hash: string[] | null;
    timeoutError: boolean;
    stopListener: () => void;
    startListener: () => void;
};
export declare function getHash(): Promise<string[]>;
/** New Phone Number Hint first, then legacy Credentials hint as fallback. */
export declare function requestHint(): Promise<string>;
/** New Phone Number Hint API only (no legacy fallback). */
export declare function requestPhoneHint(): Promise<string>;
/** Legacy Smart Lock / Credentials HintRequest only. */
export declare function requestLegacyPhoneHint(): Promise<string>;
export declare function addListener(handler: (value: string) => any): import('react-native').EmitterSubscription;
export declare function removeListener(): void;
declare const PhoneSmsRetrieverApi: PhoneSmsRetriever;
export default PhoneSmsRetrieverApi;
