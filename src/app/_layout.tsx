// Registers the background status check. It must load before anything else.
import '../background/statusCheckTask';

import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { ActionButton } from '../components/ActionButton';
import { spacing, useThemeColours } from '../components/theme';
import { WasherProvider, useWasher } from '../washer/WasherContext';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <WasherProvider>
        <StatusBar style="auto" />
        <RootNavigator />
      </WasherProvider>
    </SafeAreaProvider>
  );
}

/** Shows the sign-in screen until a washing machine is connected. */
function RootNavigator() {
  const { connection, retryConnection, signOut } = useWasher();
  const colours = useThemeColours();

  if (connection.kind === 'starting') {
    return (
      <View style={[styles.centred, { backgroundColor: colours.background }]}>
        <ActivityIndicator size="large" color={colours.accent} />
        <Text style={[styles.message, { color: colours.mutedText }]}>Connecting to your washing machine…</Text>
      </View>
    );
  }

  if (connection.kind === 'unreachable') {
    return (
      <View style={[styles.centred, { backgroundColor: colours.background }]}>
        <Text style={[styles.heading, { color: colours.text }]}>Can't reach h.on</Text>
        <Text style={[styles.message, { color: colours.mutedText }]}>{connection.message}</Text>
        <View style={styles.buttonRow}>
          <ActionButton label="Try again" onPress={() => void retryConnection()} />
        </View>
        <View style={styles.buttonRow}>
          <ActionButton label="Sign out" look="secondary" onPress={() => void signOut()} />
        </View>
      </View>
    );
  }

  const isConnected = connection.kind === 'connected';
  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colours.background } }}>
      <Stack.Protected guard={isConnected}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen
          name="program/[programId]"
          options={{
            headerShown: true,
            title: 'Program',
            headerStyle: { backgroundColor: colours.card },
            headerTintColor: colours.text,
          }}
        />
        <Stack.Screen
          name="settings"
          options={{
            headerShown: true,
            title: 'Settings',
            headerStyle: { backgroundColor: colours.card },
            headerTintColor: colours.text,
          }}
        />
      </Stack.Protected>
      <Stack.Protected guard={!isConnected}>
        <Stack.Screen name="sign-in" />
      </Stack.Protected>
    </Stack>
  );
}

const styles = StyleSheet.create({
  centred: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.large },
  heading: { fontSize: 22, fontWeight: '700', marginBottom: spacing.small },
  message: { fontSize: 16, textAlign: 'center', marginTop: spacing.medium },
  buttonRow: { flexDirection: 'row', marginTop: spacing.medium, alignSelf: 'stretch' },
});
