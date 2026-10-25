/**
 * Fixed values used to talk to Haier's h.on cloud.
 *
 * These were worked out by the community pyhOn library (they match what the
 * official h.on app sends). They are the same for every account, so they are
 * not secrets. If Haier changes them, sign-in or commands will start failing
 * and this is the first file to check.
 */

/** Salesforce-based sign-in service used by the h.on app. */
export const HON_AUTH_BASE_URL = 'https://account2.hon-smarthome.com';

/** Main h.on API for appliances, status and commands. */
export const HON_API_BASE_URL = 'https://api-iot.he.services';

/** OAuth client id of the official h.on mobile app. */
export const HON_OAUTH_CLIENT_ID =
  '3MVG9QDx8IX8nP5T2Ha8ofvlmjLZl5L_gvfbT9.' +
  'HJvpHGKoAS_dcMN8LYpTSYeVFCraUnV.2Ag1Ki7m4znVO6';

/**
 * Where the sign-in page sends the browser once you are signed in. It is a
 * custom scheme, so it never loads: the sign-in screen catches it and reads
 * the tokens out of it.
 */
export const HON_LOGIN_REDIRECT_URI = 'hon://mobilesdk/detect/oauth/done';

/** App version the API expects to be talking to. */
export const HON_APP_VERSION = '2.6.5';
export const HON_OS_NAME = 'android';
export const HON_OS_VERSION = 999;
export const HON_DEVICE_MODEL = 'WasherApp';

export const HON_USER_AGENT = 'Chrome/999.999.999.999';

/**
 * h.on tokens last about 8 hours. We refresh a bit before that so a request
 * never goes out with a token that is about to die.
 */
export const HON_TOKEN_REFRESH_AFTER_MS = 7 * 60 * 60 * 1000;

/**
 * How often to ask the cloud for the machine's status while the app is open.
 * Haier asked the Home Assistant integration to stay at 60 seconds or slower,
 * so we only go faster (30 s) while a wash is actually running.
 */
export const STATUS_POLL_WHILE_RUNNING_MS = 30 * 1000;
export const STATUS_POLL_WHILE_IDLE_MS = 60 * 1000;
