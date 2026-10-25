import { HON_AUTH_BASE_URL, HON_LOGIN_REDIRECT_URI, HON_OAUTH_CLIENT_ID } from '../honConstants';

/**
 * Signing in works like the official app: we open Haier's own sign-in page
 * in a web view, the user types their email and password there, and when it
 * is done the page redirects to HON_LOGIN_REDIRECT_URI with the tokens in
 * the URL. This app never sees the password.
 */

/** Tokens returned by the sign-in page. */
export interface HonLoginTokens {
  accessToken: string;
  refreshToken: string;
  /** Proves who you are to the h.on API; exchanged for a "cognito" token. */
  idToken: string;
}

/** A random value the sign-in service requires, formatted like a UUID. */
function generateNonce(): string {
  const hexDigits = Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join('');
  return [
    hexDigits.slice(0, 8),
    hexDigits.slice(8, 12),
    hexDigits.slice(12, 16),
    hexDigits.slice(16, 20),
    hexDigits.slice(20),
  ].join('-');
}

/** The address of Haier's sign-in page, ready to open in a web view. */
export function buildLoginPageUrl(nonce: string = generateNonce()): string {
  const queryParts = [
    // The literal "+" is how the h.on app asks for both token types.
    'response_type=token+id_token',
    `client_id=${encodeURIComponent(HON_OAUTH_CLIENT_ID)}`,
    `redirect_uri=${encodeURIComponent(HON_LOGIN_REDIRECT_URI)}`,
    'display=touch',
    `scope=${encodeURIComponent('api openid refresh_token web')}`,
    `nonce=${nonce}`,
  ];
  return `${HON_AUTH_BASE_URL}/services/oauth2/authorize/expid_Login?${queryParts.join('&')}`;
}

/** True when the web view is trying to open the "signed in" redirect. */
export function isLoginRedirect(url: string): boolean {
  return url.startsWith(HON_LOGIN_REDIRECT_URI);
}

/**
 * Reads the tokens out of the redirect address, which looks like
 * hon://mobilesdk/detect/oauth/done#access_token=…&refresh_token=…&id_token=…
 * Returns null if any token is missing.
 */
export function parseLoginRedirect(url: string): HonLoginTokens | null {
  const fragment = url.split('#')[1] ?? url.split('?')[1] ?? '';
  const values = new Map<string, string>();
  for (const pair of fragment.split('&')) {
    const separatorIndex = pair.indexOf('=');
    if (separatorIndex <= 0) continue;
    values.set(pair.slice(0, separatorIndex), pair.slice(separatorIndex + 1));
  }

  const accessToken = values.get('access_token');
  const refreshToken = values.get('refresh_token');
  const idToken = values.get('id_token');
  if (!accessToken || !refreshToken || !idToken) return null;

  return {
    accessToken: decodeURIComponent(accessToken),
    refreshToken: decodeURIComponent(refreshToken),
    idToken: decodeURIComponent(idToken),
  };
}
