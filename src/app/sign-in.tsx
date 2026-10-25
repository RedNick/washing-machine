import { useMemo, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';

import { ActionButton } from '../components/ActionButton';
import { spacing, useThemeColours } from '../components/theme';
import { buildLoginPageUrl, isLoginRedirect, parseLoginRedirect } from '../hon/auth/honLogin';
import { useWasher } from '../washer/WasherContext';

/**
 * First screen: sign in with your h.on account, or try the demo machine.
 *
 * Signing in opens Haier's own sign-in page inside the app. You type your
 * email and password into Haier's page, not into this app, and the app
 * only receives the resulting sign-in tokens.
 */
export default function SignInScreen() {
  const { connection, signInWithTokens, startDemo } = useWasher();
  const colours = useThemeColours();
  const [isShowingLoginPage, setIsShowingLoginPage] = useState(false);
  const [isFinishingSignIn, setIsFinishingSignIn] = useState(false);
  const [loginProblem, setLoginProblem] = useState<string | null>(null);
  // A new address (with a fresh nonce) each time the page is opened.
  const loginPageUrl = useMemo(() => (isShowingLoginPage ? buildLoginPageUrl() : ''), [isShowingLoginPage]);

  const errorMessage = loginProblem ?? (connection.kind === 'signedOut' ? connection.message : null);

  /** Watches every page the web view tries to open, looking for the "done" redirect. */
  const handleNavigation = (url: string): boolean => {
    if (!isLoginRedirect(url)) return true;
    const tokens = parseLoginRedirect(url);
    setIsShowingLoginPage(false);
    if (!tokens) {
      setLoginProblem("h.on didn't finish signing you in. Please try again.");
      return false;
    }
    setLoginProblem(null);
    setIsFinishingSignIn(true);
    void signInWithTokens(tokens).finally(() => setIsFinishingSignIn(false));
    return false;
  };

  if (isShowingLoginPage) {
    return (
      <SafeAreaView style={[styles.fill, { backgroundColor: colours.background }]}>
        <WebView
          source={{ uri: loginPageUrl }}
          // Let the web view try to open hon:// so we can catch it.
          originWhitelist={['https://*', 'hon://*']}
          onShouldStartLoadWithRequest={(request) => handleNavigation(request.url)}
          startInLoadingState
          renderLoading={() => <ActivityIndicator style={styles.fill} color={colours.accent} />}
          // Start each sign-in fresh rather than reusing a half-finished one.
          incognito
        />
        <View style={styles.cancelRow}>
          <ActionButton label="Cancel" look="secondary" onPress={() => setIsShowingLoginPage(false)} />
        </View>
      </SafeAreaView>
    );
  }

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
          busy={isFinishingSignIn}
          onPress={() => {
            setLoginProblem(null);
            setIsShowingLoginPage(true);
          }}
        />
      </View>
      <View style={styles.buttonRow}>
        <ActionButton label="Try the demo machine" look="secondary" onPress={() => void startDemo()} />
      </View>
      <Text style={[styles.footnote, { color: colours.mutedText }]}>
        You sign in on Haier's own page. Your password never passes through this app, and your sign-in is kept in
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
  cancelRow: { flexDirection: 'row', padding: spacing.medium },
  footnote: { fontSize: 13, textAlign: 'center', marginTop: spacing.large },
});
