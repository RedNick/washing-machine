import * as Linking from 'expo-linking';
import * as WebBrowser from 'expo-web-browser';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ActionButton } from '../components/ActionButton';
import { spacing, useThemeColours } from '../components/theme';
import { buildLoginPageUrl, isLoginRedirect, parseLoginRedirect } from '../hon/auth/honLogin';
import { HON_LOGIN_REDIRECT_URI } from '../hon/honConstants';
import { useWasher } from '../washer/WasherContext';

/**
 * Android can close the app while you're signing in in Chrome. It then
 * reopens it with the "signed in" link as the launch link. This remembers
 * whether that launch link has been used, so a later sign-out doesn't sign
 * you straight back in with the same, already-used tokens.
 */
let hasCheckedLaunchLink = false;

/**
 * First screen: sign in with your h.on account, or try the demo machine.
 *
 * Signing in opens Haier's own sign-in page in a Chrome tab. You sign in
 * there (with your email and password, or "Sign in with Google", which can
 * use the Google accounts already on the phone), and Chrome hands the app
 * only the resulting sign-in tokens.
 */
export default function SignInScreen() {
  const { connection, signInWithTokens, startDemo } = useWasher();
  const colours = useThemeColours();
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [loginProblem, setLoginProblem] = useState<string | null>(null);

  const errorMessage = loginProblem ?? (connection.kind === 'signedOut' ? connection.message : null);

  /** Reads the tokens from the "signed in" link and connects to the machine. */
  const finishSignIn = useCallback(
    async (redirectUrl: string) => {
      const tokens = parseLoginRedirect(redirectUrl);
      if (!tokens) {
        setLoginProblem("h.on didn't finish signing you in. Please try again.");
        return;
      }
      setLoginProblem(null);
      await signInWithTokens(tokens);
    },
    [signInWithTokens],
  );

  // Pick up a sign-in that finished while the app was closed (see above).
  useEffect(() => {
    if (hasCheckedLaunchLink) return;
    hasCheckedLaunchLink = true;
    void Linking.getInitialURL().then((launchUrl) => {
      if (!launchUrl || !isLoginRedirect(launchUrl)) return;
      setIsSigningIn(true);
      void finishSignIn(launchUrl).finally(() => setIsSigningIn(false));
    });
  }, [finishSignIn]);

  const openLoginPage = async () => {
    setLoginProblem(null);
    setIsSigningIn(true);
    try {
      // A new address (with a fresh nonce) each time. The promise resolves
      // when Chrome opens the "signed in" link or you close the tab.
      const result = await WebBrowser.openAuthSessionAsync(buildLoginPageUrl(), HON_LOGIN_REDIRECT_URI);
      if (result.type === 'success') await finishSignIn(result.url);
    } catch {
      setLoginProblem("Couldn't open the sign-in page. Check that Chrome is installed and try again.");
    } finally {
      setIsSigningIn(false);
    }
  };

  return (
    <SafeAreaView style={[styles.fill, styles.welcome, { backgroundColor: colours.background }]}>
      <Text style={styles.emoji} accessibilityElementsHidden>
        🧺
      </Text>
      <Text style={[styles.title, { color: colours.text }]}>Washer</Text>
      <Text style={[styles.subtitle, { color: colours.mutedText }]}>
        A quicker way to run your h.on washing machine.
      </Text>

      {errorMessage ? (
        <View style={[styles.errorBox, { backgroundColor: colours.warningBackground }]}>
          <Text style={{ color: colours.warningText }}>{errorMessage}</Text>
        </View>
      ) : null}

      <View style={styles.buttonRow}>
        <ActionButton
          label="Sign in with h.on"
          busy={isSigningIn}
          onPress={() => void openLoginPage()}
        />
      </View>
      <View style={styles.buttonRow}>
        <ActionButton label="Try the demo machine" look="secondary" onPress={() => void startDemo()} />
      </View>
      <Text style={[styles.footnote, { color: colours.mutedText }]}>
        You sign in on Haier's own page in Chrome, where you can also use your Google account. Your password never passes through this app, and your sign-in is kept in
        the phone's secure storage.
      </Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  welcome: { justifyContent: 'center', padding: spacing.large },
  emoji: { fontSize: 64, textAlign: 'center' },
  title: { fontSize: 34, fontWeight: '800', textAlign: 'center', marginTop: spacing.small },
  subtitle: { fontSize: 17, textAlign: 'center', marginTop: spacing.small, marginBottom: spacing.large },
  errorBox: { borderRadius: 12, padding: spacing.medium, marginBottom: spacing.medium },
  buttonRow: { flexDirection: 'row', marginTop: spacing.medium },
  footnote: { fontSize: 13, textAlign: 'center', marginTop: spacing.large },
});
