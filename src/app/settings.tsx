import { useEffect, useState } from 'react';
import { Platform, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { ActionButton } from '../components/ActionButton';
import { Card } from '../components/Card';
import { spacing, useThemeColours } from '../components/theme';
import { buildDiagnosticReport } from '../diagnostics/buildDiagnosticReport';
import { setCrashReportingEnabled } from '../diagnostics/crashReporting';
import { isCrashReportingSetUp, isWashReportingSetUp } from '../diagnostics/reportingServices';
import { shareDiagnosticReport } from '../diagnostics/shareDiagnosticReport';
import {
  DIAGNOSTICS_SETTINGS_OFF,
  loadDiagnosticsSettings,
  saveDiagnosticsSettings,
  type DiagnosticsSettings,
} from '../storage/diagnosticsSettings';
import { describeAppVersion } from '../washer/appVersion';
import { useConnectedWasher } from '../washer/WasherContext';

/** Choices about sending data back to help improve h.off. Everything is off by default. */
export default function SettingsScreen() {
  const { washer, machineInfo } = useConnectedWasher();
  const colours = useThemeColours();

  const [settings, setSettings] = useState<DiagnosticsSettings>(DIAGNOSTICS_SETTINGS_OFF);
  const [isSharingReport, setIsSharingReport] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);

  useEffect(() => {
    void loadDiagnosticsSettings().then(setSettings);
  }, []);

  const changeSetting = (settingName: keyof DiagnosticsSettings, isOn: boolean) => {
    const changedSettings = { ...settings, [settingName]: isOn };
    setSettings(changedSettings);
    void saveDiagnosticsSettings(changedSettings);
  };

  const sendReport = async () => {
    setIsSharingReport(true);
    setReportError(null);
    try {
      const report = await buildDiagnosticReport({
        washer,
        machineInfo,
        appVersion: describeAppVersion(),
        platform: Platform.OS,
        osVersion: String(Platform.Version),
      });
      await shareDiagnosticReport(report);
    } catch (error) {
      setReportError(error instanceof Error ? error.message : String(error));
    } finally {
      setIsSharingReport(false);
    }
  };

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Card title="Report a problem">
        <Text style={[styles.explanation, { color: colours.text }]}>
          Makes a file with what h.off can see of your machine: its model, current readings, recent activity and program
          list. Sign-in details, the machine's name, MAC address and serial number are removed. You choose where to send
          it.
        </Text>
        <View style={styles.buttonRow}>
          <ActionButton label="Send a report" busy={isSharingReport} onPress={() => void sendReport()} />
        </View>
        {reportError ? <Text style={{ color: colours.danger }}>{reportError}</Text> : null}
      </Card>

      <Card title="Automatic reports">
        <SettingSwitch
          label="Crash reports"
          explanation="Sends details of what went wrong to Sentry (sentry.io) when h.off crashes. Private details are removed first."
          isOn={settings.sendCrashReports}
          isAvailable={isCrashReportingSetUp}
          onChange={(isOn) => {
            changeSetting('sendCrashReports', isOn);
            setCrashReportingEnabled(isOn);
          }}
        />
        <SettingSwitch
          label="Wash reports"
          explanation="At the end of each wash, sends the program, how long it took and the machine's readings. No account details."
          isOn={settings.sendWashReports}
          isAvailable={isWashReportingSetUp}
          onChange={(isOn) => changeSetting('sendWashReports', isOn)}
        />
      </Card>
    </ScrollView>
  );
}

/** One on/off setting with a short explanation underneath. */
function SettingSwitch({
  label,
  explanation,
  isOn,
  isAvailable,
  onChange,
}: {
  label: string;
  explanation: string;
  isOn: boolean;
  /** False until the service behind it is set up, so it can't be switched on yet. */
  isAvailable: boolean;
  onChange: (isOn: boolean) => void;
}) {
  const colours = useThemeColours();
  return (
    <View style={[styles.settingRow, { borderBottomColor: colours.border }]}>
      <View style={styles.settingText}>
        <Text style={[styles.settingLabel, { color: colours.text }]}>{label}</Text>
        <Text style={{ color: colours.mutedText }}>{isAvailable ? explanation : `${explanation} Not set up yet.`}</Text>
      </View>
      <Switch value={isAvailable && isOn} disabled={!isAvailable} onValueChange={onChange} accessibilityLabel={label} />
    </View>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.medium },
  explanation: { fontSize: 15, lineHeight: 21 },
  buttonRow: { flexDirection: 'row', marginVertical: spacing.medium },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  settingText: { flex: 1, marginRight: spacing.medium },
  settingLabel: { fontSize: 16, fontWeight: '600', marginBottom: 2 },
});
