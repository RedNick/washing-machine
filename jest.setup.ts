// Stand-ins for native modules that jest-expo doesn't mock itself.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

// jest-expo's notification mock hands back listeners without remove(), which
// setNotificationHandler needs, and empty permission answers. Notifications
// themselves aren't under test.
jest.mock('expo-notifications', () => ({
  ...jest.requireActual('expo-notifications'),
  setNotificationHandler: jest.fn(),
  getPermissionsAsync: jest.fn(async () => ({ granted: true })),
  requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
}));

// Crash reporting is tested by checking what the app asks Sentry to do.
jest.mock('@sentry/react-native', () => ({
  init: jest.fn(),
  close: jest.fn(async () => undefined),
}));
