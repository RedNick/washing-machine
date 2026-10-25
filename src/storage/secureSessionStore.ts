import * as SecureStore from 'expo-secure-store';

import type { SavedHonSession, SessionStore } from '../hon/auth/honSession';

const SESSION_STORAGE_KEY = 'hon.session';

/**
 * Keeps the h.on refresh token in Android's encrypted keystore, so other
 * apps can't read it. The password is never stored: Haier's sign-in page
 * handles it.
 */
export const secureSessionStore: SessionStore = {
  async load(): Promise<SavedHonSession | null> {
    const saved = await SecureStore.getItemAsync(SESSION_STORAGE_KEY);
    if (!saved) return null;
    try {
      return JSON.parse(saved) as SavedHonSession;
    } catch {
      return null;
    }
  },
  async save(session: SavedHonSession): Promise<void> {
    await SecureStore.setItemAsync(SESSION_STORAGE_KEY, JSON.stringify(session));
  },
  async clear(): Promise<void> {
    await SecureStore.deleteItemAsync(SESSION_STORAGE_KEY);
  },
};
