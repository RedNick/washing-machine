import {
  HON_API_BASE_URL,
  HON_APP_VERSION,
  HON_AUTH_BASE_URL,
  HON_DEVICE_MODEL,
  HON_OAUTH_CLIENT_ID,
  HON_OS_NAME,
  HON_OS_VERSION,
  HON_TOKEN_REFRESH_AFTER_MS,
  HON_USER_AGENT,
} from '../honConstants';
import type { HonLoginTokens } from './honLogin';

/** What we keep on the phone between launches. Never the password. */
export interface SavedHonSession {
  refreshToken: string;
  /** Random id for this phone, sent with every request like the h.on app does. */
  mobileId: string;
}

/** Where SavedHonSession lives. The app uses the phone's secure storage. */
export interface SessionStore {
  load(): Promise<SavedHonSession | null>;
  save(session: SavedHonSession): Promise<void>;
  clear(): Promise<void>;
}

export type FetchFunction = typeof fetch;

/** Thrown when we can't sign in, or the saved sign-in no longer works. */
export class HonAuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'HonAuthError';
  }
}

function generateMobileId(): string {
  return Array.from({ length: 16 }, () => Math.floor(Math.random() * 16).toString(16)).join('');
}

/**
 * Keeps the h.on tokens fresh and hands out the headers every API request
 * needs.
 *
 * h.on uses two tokens: an "id token" from the sign-in service, and a
 * "cognito token" that the API gives us in exchange for it. Both last about
 * 8 hours. The long-lived refresh token gets us new ones without asking the
 * user to sign in again.
 */
export class HonSession {
  private idToken = '';
  private cognitoToken = '';
  private tokensIssuedAt = 0;
  /** Shared by concurrent callers so we never refresh twice at once. */
  private refreshInProgress: Promise<void> | null = null;

  private constructor(
    private refreshToken: string,
    readonly mobileId: string,
    private readonly store: SessionStore,
    private readonly fetchFunction: FetchFunction,
  ) {}

  /** Starts a session from the tokens the sign-in page gave us. */
  static async fromLogin(
    loginTokens: HonLoginTokens,
    store: SessionStore,
    fetchFunction: FetchFunction = fetch,
  ): Promise<HonSession> {
    const existing = await store.load();
    const session = new HonSession(
      loginTokens.refreshToken,
      existing?.mobileId ?? generateMobileId(),
      store,
      fetchFunction,
    );
    session.idToken = loginTokens.idToken;
    await session.exchangeIdTokenForCognitoToken();
    await store.save({ refreshToken: session.refreshToken, mobileId: session.mobileId });
    return session;
  }

  /**
   * Picks up where the last launch left off. Returns null when there is no
   * saved sign-in, so the app should show the sign-in screen.
   */
  static async restore(
    store: SessionStore,
    fetchFunction: FetchFunction = fetch,
  ): Promise<HonSession | null> {
    const saved = await store.load();
    if (!saved?.refreshToken) return null;
    const session = new HonSession(saved.refreshToken, saved.mobileId, store, fetchFunction);
    await session.refreshTokens();
    return session;
  }

  /** Headers for an API request, refreshing the tokens first if they're old. */
  async authorizedHeaders(now: number = Date.now()): Promise<Record<string, string>> {
    if (now - this.tokensIssuedAt >= HON_TOKEN_REFRESH_AFTER_MS) {
      await this.refreshTokens();
    }
    return {
      'user-agent': HON_USER_AGENT,
      'Content-Type': 'application/json',
      'cognito-token': this.cognitoToken,
      'id-token': this.idToken,
    };
  }

  /** Gets fresh tokens using the refresh token. */
  refreshTokens(): Promise<void> {
    if (!this.refreshInProgress) {
      this.refreshInProgress = this.performRefresh().finally(() => {
        this.refreshInProgress = null;
      });
    }
    return this.refreshInProgress;
  }

  /** Forgets the saved sign-in. */
  async signOut(): Promise<void> {
    this.idToken = '';
    this.cognitoToken = '';
    this.refreshToken = '';
    await this.store.clear();
  }

  private async performRefresh(): Promise<void> {
    const query = [
      `client_id=${encodeURIComponent(HON_OAUTH_CLIENT_ID)}`,
      `refresh_token=${encodeURIComponent(this.refreshToken)}`,
      'grant_type=refresh_token',
    ].join('&');
    const response = await this.fetchFunction(`${HON_AUTH_BASE_URL}/services/oauth2/token?${query}`, {
      method: 'POST',
      headers: { 'user-agent': HON_USER_AGENT },
    });
    if (!response.ok) {
      throw new HonAuthError(`Your h.on sign-in has expired (refresh failed with ${response.status}).`);
    }
    const tokenResponse = (await response.json()) as { id_token?: string; refresh_token?: string };
    if (!tokenResponse.id_token) {
      throw new HonAuthError('h.on did not return a new sign-in token.');
    }
    this.idToken = tokenResponse.id_token;
    // Salesforce normally keeps the same refresh token, but use a new one if offered.
    if (tokenResponse.refresh_token && tokenResponse.refresh_token !== this.refreshToken) {
      this.refreshToken = tokenResponse.refresh_token;
      await this.store.save({ refreshToken: this.refreshToken, mobileId: this.mobileId });
    }
    await this.exchangeIdTokenForCognitoToken();
  }

  /** The API wants its own "cognito" token, which it swaps for the id token. */
  private async exchangeIdTokenForCognitoToken(): Promise<void> {
    const response = await this.fetchFunction(`${HON_API_BASE_URL}/auth/v1/login`, {
      method: 'POST',
      headers: {
        'user-agent': HON_USER_AGENT,
        'Content-Type': 'application/json',
        'id-token': this.idToken,
      },
      body: JSON.stringify({
        appVersion: HON_APP_VERSION,
        mobileId: this.mobileId,
        os: HON_OS_NAME,
        osVersion: HON_OS_VERSION,
        deviceModel: HON_DEVICE_MODEL,
      }),
    });
    const loginResponse = (await response.json().catch(() => ({}))) as {
      cognitoUser?: { Token?: string };
    };
    const cognitoToken = loginResponse.cognitoUser?.Token;
    if (!response.ok || !cognitoToken) {
      throw new HonAuthError(`h.on refused the sign-in (status ${response.status}).`);
    }
    this.cognitoToken = cognitoToken;
    this.tokensIssuedAt = Date.now();
  }
}
