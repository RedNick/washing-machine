import { isLoginRedirect } from '../hon/auth/honLogin';

/**
 * Expo Router calls this for every link that opens the app.
 *
 * When you finish signing in, Chrome opens hon://mobilesdk/detect/oauth/done.
 * The sign-in screen reads the tokens from that link itself, so the router
 * should ignore it instead of looking for a screen with that name.
 */
export function redirectSystemPath({ path }: { path: string; initial: boolean }): string | null {
  try {
    return isLoginRedirect(path) ? null : path;
  } catch {
    return path;
  }
}
