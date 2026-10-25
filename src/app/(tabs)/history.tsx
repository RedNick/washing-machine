import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '../../components/ActionButton';
import { Card, DetailRow } from '../../components/Card';
import { spacing, useThemeColours } from '../../components/theme';
import { loadFinishedCycles, type FinishedCycle } from '../../storage/cycleLog';
import { formatPastDate } from '../../washer/formatters';
import { useConnectedWasher, useWasher } from '../../washer/WasherContext';
import type { PastCommand, UsageSummary } from '../../washer/washerTypes';

const COMMAND_LABELS: Record<string, string> = {
  startProgram: 'Started',
  stopProgram: 'Stopped',
  pauseProgram: 'Paused',
  resumeProgram: 'Resumed',
};

/** Energy and water use, past washes, and the sign-out button. */
export default function HistoryScreen() {
  const { washer, machineInfo } = useConnectedWasher();
  const { signOut } = useWasher();
  const colours = useThemeColours();

  const [usage, setUsage] = useState<UsageSummary | null>(null);
  const [finishedCycles, setFinishedCycles] = useState<FinishedCycle[]>([]);
  const [recentCommands, setRecentCommands] = useState<PastCommand[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const loadHistory = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const [loadedUsage, loadedCycles, loadedCommands] = await Promise.all([
        washer.getUsageSummary(),
        loadFinishedCycles(),
        washer.getRecentCommands(),
      ]);
      setUsage(loadedUsage);
      setFinishedCycles(loadedCycles);
      setRecentCommands(loadedCommands.slice(0, 15));
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : String(error));
    } finally {
      setIsLoading(false);
    }
  }, [washer]);

  // Reload whenever the tab is opened.
  useFocusEffect(
    useCallback(() => {
      void loadHistory();
    }, [loadHistory]),
  );

  return (
    <ScrollView
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={isLoading} onRefresh={() => void loadHistory()} />}
    >
      {loadError ? (
        <Text style={[styles.banner, { color: colours.warningText, backgroundColor: colours.warningBackground }]}>
          {loadError}
        </Text>
      ) : null}

      {usage ? (
        <Card title="Since the machine was new">
          <DetailRow label="Washes" value={usage.totalCycles === null ? '–' : String(usage.totalCycles)} />
          <DetailRow
            label="Energy"
            value={usage.totalEnergyKwh === null ? '–' : `${usage.totalEnergyKwh.toFixed(1)} kWh`}
          />
          <DetailRow
            label="Water"
            value={usage.totalWaterLitres === null ? '–' : `${usage.totalWaterLitres.toLocaleString()} L`}
          />
          {usage.totalCycles && usage.totalEnergyKwh !== null && usage.totalWaterLitres !== null ? (
            <Text style={[styles.average, { color: colours.mutedText }]}>
              About {(usage.totalEnergyKwh / usage.totalCycles).toFixed(2)} kWh and{' '}
              {Math.round(usage.totalWaterLitres / usage.totalCycles)} L per wash.
            </Text>
          ) : null}
        </Card>
      ) : null}

      {usage && usage.temperatureBands.length > 0 ? (
        <Card title="Temperatures used">
          <TemperatureBars bands={usage.temperatureBands} />
        </Card>
      ) : null}

      <Card title="Your washes">
        {finishedCycles.length === 0 ? (
          <Text style={{ color: colours.mutedText }}>
            Washes appear here once they finish while this app is installed.
          </Text>
        ) : (
          finishedCycles.slice(0, 20).map((cycle) => (
            <View key={cycle.finishedAt} style={[styles.historyRow, { borderBottomColor: colours.border }]}>
              <Text style={[styles.historyTitle, { color: colours.text }]}>{cycle.programName ?? 'Wash'}</Text>
              <Text style={{ color: colours.mutedText }}>
                {formatPastDate(new Date(cycle.finishedAt))}
                {cycle.energyKwh !== null ? ` · ${cycle.energyKwh.toFixed(2)} kWh` : ''}
                {cycle.waterLitres !== null ? ` · ${cycle.waterLitres} L` : ''}
              </Text>
            </View>
          ))
        )}
      </Card>

      {recentCommands.length > 0 ? (
        <Card title="Recent commands">
          {recentCommands.map((command, index) => (
            <View key={index} style={[styles.historyRow, { borderBottomColor: colours.border }]}>
              <Text style={[styles.historyTitle, { color: colours.text }]}>
                {COMMAND_LABELS[command.commandName] ?? command.commandName}
                {command.programName ? ` · ${command.programName}` : ''}
              </Text>
              {command.sentAt ? (
                <Text style={{ color: colours.mutedText }}>{formatPastDate(command.sentAt)}</Text>
              ) : null}
            </View>
          ))}
        </Card>
      ) : null}

      <Card title="Machine">
        <DetailRow label="Name" value={machineInfo.displayName} />
        <DetailRow label="Model" value={machineInfo.modelName} />
        {machineInfo.brand ? <DetailRow label="Brand" value={machineInfo.brand} /> : null}
      </Card>

      <View style={styles.buttonRow}>
        <ActionButton
          label={washer.isDemo ? 'Leave the demo' : 'Sign out'}
          look="secondary"
          onPress={() => void signOut()}
        />
      </View>
    </ScrollView>
  );
}

/** A tiny bar chart of how often each temperature band is used. */
function TemperatureBars({ bands }: { bands: UsageSummary['temperatureBands'] }) {
  const colours = useThemeColours();
  const highestCount = Math.max(1, ...bands.map((band) => band.count));
  return (
    <View>
      {bands.map((band) => (
        <View key={band.label} style={styles.barRow}>
          <Text style={[styles.barLabel, { color: colours.text }]}>{band.label}</Text>
          <View style={[styles.barTrack, { backgroundColor: colours.border }]}>
            <View
              style={[styles.barFill, { backgroundColor: colours.accent, width: `${(band.count / highestCount) * 100}%` }]}
            />
          </View>
          <Text style={[styles.barCount, { color: colours.mutedText }]}>{band.count}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.medium },
  banner: { borderRadius: 12, padding: spacing.medium, marginBottom: spacing.medium, overflow: 'hidden' },
  average: { marginTop: spacing.small, fontSize: 14 },
  historyRow: { paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth },
  historyTitle: { fontSize: 16, fontWeight: '600' },
  barRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6 },
  barLabel: { width: 80, fontSize: 15 },
  barTrack: { flex: 1, height: 10, borderRadius: 5, overflow: 'hidden', marginHorizontal: spacing.small },
  barFill: { height: 10, borderRadius: 5 },
  barCount: { width: 32, textAlign: 'right' },
  buttonRow: { flexDirection: 'row', marginBottom: spacing.large },
});
