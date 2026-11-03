import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '../../components/ActionButton';
import { Card, DetailRow } from '../../components/Card';
import { spacing, useThemeColours } from '../../components/theme';
import { describePhase, describeState, formatDuration, formatFinishTime, formatPastDate } from '../../washer/formatters';
import { describeAppVersion } from '../../washer/appVersion';
import { useConnectedWasher, useWasher } from '../../washer/WasherContext';
import type { WasherStatus } from '../../washer/washerTypes';

/** The home screen: what the machine is doing, and buttons to control it. */
export default function NowScreen() {
  const { status, statusError, isRefreshing, refreshStatus } = useWasher();
  const { washer, machineInfo } = useConnectedWasher();
  const colours = useThemeColours();
  const [commandInProgress, setCommandInProgress] = useState<'pause' | 'resume' | 'stop' | null>(null);

  /** Sends a command, then refreshes so the screen shows the result. */
  const runCommand = async (commandName: 'pause' | 'resume' | 'stop') => {
    setCommandInProgress(commandName);
    try {
      await washer[commandName]();
      await refreshStatus();
    } catch (error) {
      Alert.alert("That didn't work", error instanceof Error ? error.message : String(error));
    } finally {
      setCommandInProgress(null);
    }
  };

  const confirmStop = () =>
    Alert.alert('Stop the wash?', 'The program will be cancelled. The door may stay locked while water drains.', [
      { text: 'Keep washing', style: 'cancel' },
      { text: 'Stop', style: 'destructive', onPress: () => void runCommand('stop') },
    ]);

  return (
    <ScrollView
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={() => void refreshStatus()} />}
    >
      <Text style={[styles.machineName, { color: colours.mutedText }]}>
        {machineInfo.displayName}
        {washer.isDemo ? ' · demo' : ''}
      </Text>

      {statusError ? (
        <View style={[styles.banner, { backgroundColor: colours.warningBackground }]}>
          <Text style={{ color: colours.warningText }}>{statusError}</Text>
        </View>
      ) : null}

      {status ? (
        <>
          <StatusSummary status={status} />
          {!status.remoteControlEnabled && status.state !== 'offline' ? (
            <View style={[styles.banner, { backgroundColor: colours.warningBackground }]}>
              <Text style={{ color: colours.warningText }}>
                Remote control is off on the machine, so it will ignore commands from your phone. Turn it on at the
                machine to start, pause or stop from here.
              </Text>
            </View>
          ) : null}
          <Controls
            status={status}
            commandInProgress={commandInProgress}
            onPause={() => void runCommand('pause')}
            onResume={() => void runCommand('resume')}
            onStop={confirmStop}
          />
          <CycleDetails status={status} />
          {washer.isDemo ? null : <MachineReadings status={status} />}
        </>
      ) : (
        <Text style={[styles.loading, { color: colours.mutedText }]}>Checking the machine…</Text>
      )}

      <Text style={[styles.appVersion, { color: colours.mutedText }]}>{describeAppVersion()}</Text>
    </ScrollView>
  );
}

/** The big headline: state, program, and time left. */
function StatusSummary({ status }: { status: WasherStatus }) {
  const colours = useThemeColours();
  const isCounting = status.state === 'running' || status.state === 'paused';
  // Machines (washer dryers especially) can sit on "1 min" for a while as
  // they cool down and unlock, so don't show a countdown that isn't moving.
  const isFinishingUp = status.state === 'running' && status.remainingMinutes <= 1;
  const phaseLabel = describePhase(status.phase);

  return (
    <Card>
      <Text style={[styles.stateHeadline, { color: status.state === 'error' ? colours.danger : colours.text }]}>
        {describeState(status.state)}
      </Text>
      {status.programName ? (
        <Text style={[styles.programName, { color: colours.text }]}>{status.programName}</Text>
      ) : null}

      {isFinishingUp ? (
        <View style={styles.timeBlock}>
          <Text style={[styles.timeLeft, { color: colours.accent }]}>Almost done</Text>
          <Text style={[styles.timeCaption, { color: colours.mutedText }]}>
            The machine is finishing the program. It can take a few minutes before it says it's done.
          </Text>
        </View>
      ) : isCounting ? (
        <View style={styles.timeBlock}>
          <Text style={[styles.timeLeft, { color: colours.accent }]}>{formatDuration(status.remainingMinutes)}</Text>
          <Text style={[styles.timeCaption, { color: colours.mutedText }]}>
            {status.state === 'paused' ? 'left when resumed' : `left · done around ${formatFinishTime(status.remainingMinutes)}`}
          </Text>
          {status.state === 'running' && phaseLabel ? (
            <Text style={[styles.phase, { color: colours.mutedText }]}>{phaseLabel}</Text>
          ) : null}
        </View>
      ) : null}

      {status.state === 'scheduled' ? (
        <Text style={[styles.timeCaption, { color: colours.mutedText }]}>
          Starts in {formatDuration(status.delayMinutes)}
        </Text>
      ) : null}
      {status.state === 'finished' ? (
        <Text style={[styles.timeCaption, { color: colours.success }]}>Ready to unload.</Text>
      ) : null}
      {status.state === 'offline' ? (
        <Text style={[styles.timeCaption, { color: colours.mutedText }]}>
          The machine isn't connected to h.on. Check it's switched on and in Wi-Fi range.
        </Text>
      ) : null}
      {status.errorCode ? (
        <Text style={[styles.timeCaption, { color: colours.danger }]}>Error code {status.errorCode}</Text>
      ) : null}
    </Card>
  );
}

interface ControlsProps {
  status: WasherStatus;
  commandInProgress: 'pause' | 'resume' | 'stop' | null;
  onPause: () => void;
  onResume: () => void;
  onStop: () => void;
}

/** Only the buttons that make sense right now. */
function Controls({ status, commandInProgress, onPause, onResume, onStop }: ControlsProps) {
  const isBusy = commandInProgress !== null;

  if (status.state === 'running' || status.state === 'paused' || status.state === 'scheduled') {
    return (
      <View style={styles.buttonRow}>
        {status.state === 'running' ? (
          <ActionButton label="Pause" look="secondary" onPress={onPause} busy={commandInProgress === 'pause'} disabled={isBusy} />
        ) : null}
        {status.state === 'paused' ? (
          <ActionButton label="Resume" onPress={onResume} busy={commandInProgress === 'resume'} disabled={isBusy} />
        ) : null}
        <ActionButton label="Stop" look="danger" onPress={onStop} busy={commandInProgress === 'stop'} disabled={isBusy} />
      </View>
    );
  }

  if (status.state === 'idle' || status.state === 'finished') {
    return (
      <View style={styles.buttonRow}>
        <ActionButton label="Choose a program" onPress={() => router.navigate('/programs')} />
      </View>
    );
  }
  return null;
}

/** Temperature, spin and what this wash has used so far. */
function CycleDetails({ status }: { status: WasherStatus }) {
  const isMidCycle = status.state === 'running' || status.state === 'paused';
  if (!isMidCycle) return null;

  return (
    <Card title="This wash">
      {status.temperatureCelsius !== null ? (
        <DetailRow label="Temperature" value={status.temperatureCelsius === 0 ? 'Cold' : `${status.temperatureCelsius} °C`} />
      ) : null}
      {status.spinSpeedRpm ? <DetailRow label="Spin" value={`${status.spinSpeedRpm} rpm`} /> : null}
      {status.currentCycleEnergyKwh !== null ? (
        <DetailRow label="Energy so far" value={`${status.currentCycleEnergyKwh.toFixed(2)} kWh`} />
      ) : null}
      {status.currentCycleWaterLitres !== null ? (
        <DetailRow label="Water so far" value={`${status.currentCycleWaterLitres} L`} />
      ) : null}
      <DetailRow label="Door" value={status.doorLocked ? 'Locked' : 'Unlocked'} />
    </Card>
  );
}

/**
 * Everything the machine reported, unprocessed, behind a "Show" button.
 * Lets a screenshot show what this particular model actually sends.
 */
function MachineReadings({ status }: { status: WasherStatus }) {
  const colours = useThemeColours();
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <Card title="Machine readings">
      <DetailRow label="Checked" value={formatPastDate(status.fetchedAt)} />
      <Pressable accessibilityRole="button" onPress={() => setIsExpanded((wasExpanded) => !wasExpanded)}>
        <Text style={[styles.readingsToggle, { color: colours.accent }]}>
          {isExpanded ? 'Hide raw readings' : `Show raw readings (${status.rawReadings.length})`}
        </Text>
      </Pressable>
      {isExpanded
        ? status.rawReadings.map((reading) => (
            <DetailRow
              key={reading.name}
              label={reading.name}
              value={reading.lastUpdated ? `${reading.value}  ·  ${formatPastDate(reading.lastUpdated)}` : reading.value}
            />
          ))
        : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.medium },
  machineName: { fontSize: 15, marginBottom: spacing.small },
  banner: { borderRadius: 12, padding: spacing.medium, marginBottom: spacing.medium },
  loading: { textAlign: 'center', marginTop: spacing.large, fontSize: 16 },
  stateHeadline: { fontSize: 30, fontWeight: '800' },
  programName: { fontSize: 19, fontWeight: '600', marginTop: 4 },
  timeBlock: { marginTop: spacing.medium },
  timeLeft: { fontSize: 44, fontWeight: '800' },
  timeCaption: { fontSize: 16, marginTop: 4 },
  phase: { fontSize: 15, marginTop: spacing.small, fontStyle: 'italic' },
  buttonRow: { flexDirection: 'row', gap: spacing.medium, marginBottom: spacing.medium },
  appVersion: { fontSize: 13, textAlign: 'center', marginTop: spacing.medium },
  readingsToggle: { fontSize: 15, fontWeight: '600', marginTop: spacing.small },
});
