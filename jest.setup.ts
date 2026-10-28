// Stand-ins for native modules that jest-expo doesn't mock itself.
jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

// jest-expo's notification mock hands back listeners without remove(), which
// setNotificationHandler needs. The handler itself isn't under test.
jest.mock('expo-notifications', () => ({
  ...jest.requireActual('expo-notifications'),
  setNotificationHandler: jest.fn(),
}));

