import AsyncStorage from '@react-native-async-storage/async-storage';

import { loadDiagnosticsSettings, saveDiagnosticsSettings } from '../diagnosticsSettings';

describe('diagnostics settings', () => {
  beforeEach(() => AsyncStorage.clear());

  it('are all off until you switch them on', async () => {
    expect(await loadDiagnosticsSettings()).toEqual({ sendCrashReports: false, sendWashReports: false });
  });

  it('remember what you chose', async () => {
    await saveDiagnosticsSettings({ sendCrashReports: true, sendWashReports: false });
    expect(await loadDiagnosticsSettings()).toEqual({ sendCrashReports: true, sendWashReports: false });
  });

  it('count anything unreadable as off', async () => {
    await AsyncStorage.setItem('diagnostics.settings', '{"sendCrashReports":"yes"');
    expect(await loadDiagnosticsSettings()).toEqual({ sendCrashReports: false, sendWashReports: false });
  });
});
