import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Your choices about sending data back to help improve h.off. Both are
 * off until you switch them on in Settings, and a missing or unreadable
 * saved value always counts as off.
 */
export interface DiagnosticsSettings {
  /** Send crash details automatically when the app goes wrong. */
  sendCrashReports: boolean;
  /** Send a short summary at the end of each wash (no account details). */
  sendWashReports: boolean;
}

export const DIAGNOSTICS_SETTINGS_OFF: DiagnosticsSettings = { sendCrashReports: false, sendWashReports: false };

const DIAGNOSTICS_SETTINGS_KEY = 'diagnostics.settings';

export async function loadDiagnosticsSettings(): Promise<DiagnosticsSettings> {
  try {
    const saved = await AsyncStorage.getItem(DIAGNOSTICS_SETTINGS_KEY);
    const parsed = saved ? (JSON.parse(saved) as Partial<DiagnosticsSettings>) : {};
    // Only an explicit true switches a setting on.
    return {
      sendCrashReports: parsed.sendCrashReports === true,
      sendWashReports: parsed.sendWashReports === true,
    };
  } catch {
    return DIAGNOSTICS_SETTINGS_OFF;
  }
}

export async function saveDiagnosticsSettings(settings: DiagnosticsSettings): Promise<void> {
  await AsyncStorage.setItem(DIAGNOSTICS_SETTINGS_KEY, JSON.stringify(settings));
}
