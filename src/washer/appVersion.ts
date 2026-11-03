import Constants from 'expo-constants';

/**
 * The version shown at the bottom of the Now screen, e.g.
 * "h.off 1.0.0 · build 37", so you can tell which build is installed.
 *
 * The build number is the GitHub Actions run number, which is also the
 * Play Store version code. The build workflow passes it in as
 * EXPO_PUBLIC_BUILD_NUMBER, and Expo writes it into the app's JavaScript
 * when it is bundled. Builds made by hand don't have one.
 */
export function describeAppVersion(): string {
  const versionName = Constants.expoConfig?.version ?? 'unknown';
  const buildNumber = process.env.EXPO_PUBLIC_BUILD_NUMBER;
  return buildNumber ? `h.off ${versionName} · build ${buildNumber}` : `h.off ${versionName} · local build`;
}
