import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Sentry from '@sentry/react-native';

import { saveDiagnosticsSettings } from '../../storage/diagnosticsSettings';

/** Loads crashReporting.ts fresh, with or without a Sentry DSN built in. */
function loadCrashReporting(sentryDsn: string | undefined) {
  if (sentryDsn) process.env.EXPO_PUBLIC_SENTRY_DSN = sentryDsn;
  else delete process.env.EXPO_PUBLIC_SENTRY_DSN;
  let crashReporting!: typeof import('../crashReporting');
  jest.isolateModules(() => {
    crashReporting = require('../crashReporting');
  });
  return crashReporting;
}

describe('crash reporting', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await AsyncStorage.clear();
  });
  afterAll(() => delete process.env.EXPO_PUBLIC_SENTRY_DSN);

  it("doesn't start Sentry until you switch it on", async () => {
    await loadCrashReporting('https://key@example.ingest.sentry.io/1').startCrashReportingIfAllowed();
    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it('starts Sentry with privacy options when switched on, and stops it when switched off', async () => {
    await saveDiagnosticsSettings({ sendCrashReports: true, sendWashReports: false });
    const crashReporting = loadCrashReporting('https://key@example.ingest.sentry.io/1');

    await crashReporting.startCrashReportingIfAllowed();
    expect(Sentry.init).toHaveBeenCalledWith(
      expect.objectContaining({ sendDefaultPii: false, attachScreenshot: false, attachViewHierarchy: false }),
    );

    crashReporting.setCrashReportingEnabled(false);
    expect(Sentry.close).toHaveBeenCalled();
  });

  it("never starts in a build that hasn't been given a Sentry DSN", async () => {
    await saveDiagnosticsSettings({ sendCrashReports: true, sendWashReports: false });
    await loadCrashReporting(undefined).startCrashReportingIfAllowed();
    expect(Sentry.init).not.toHaveBeenCalled();
  });

  it('removes private details from reports before they are sent', async () => {
    await saveDiagnosticsSettings({ sendCrashReports: true, sendWashReports: false });
    await loadCrashReporting('https://key@example.ingest.sentry.io/1').startCrashReportingIfAllowed();

    const { beforeSend } = (Sentry.init as jest.Mock).mock.calls[0][0];
    const event = beforeSend({ message: 'GET /commands/v1/appliance/ab-cd-ef-01-23-45/history failed' });
    expect(event.message).toBe('GET /commands/v1/appliance/[removed]/history failed');
  });
});
