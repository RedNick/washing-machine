import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '../../components/ActionButton';
import { Card } from '../../components/Card';
import { DelayStartControl } from '../../components/DelayStartControl';
import { ProgramOptionControl } from '../../components/ProgramOptionControl';
import { spacing, useThemeColours } from '../../components/theme';
import { formatDuration } from '../../washer/formatters';
import { usePrograms } from '../../washer/usePrograms';
import { useConnectedWasher, useWasher } from '../../washer/WasherContext';
import { DELAY_OPTION_KEY, type ChosenOptions, type ProgramOption, type WasherProgram } from '../../washer/washerTypes';

function defaultChoices(program: WasherProgram): ChosenOptions {
  const choices: ChosenOptions = {};
  for (const option of program.options) choices[option.key] = option.defaultValue;
  return choices;
}

/** Set up one program's options, then start it. */
export default function ProgramScreen() {
  const { programId } = useLocalSearchParams<{ programId: string }>();
  const { programs, loadError } = usePrograms();
  const { washer } = useConnectedWasher();
  const { status, refreshStatus } = useWasher();
  const colours = useThemeColours();

  const program = programs?.find((candidate) => candidate.id === programId) ?? null;
  const [chosenOptions, setChosenOptions] = useState<ChosenOptions>({});
  const [isStarting, setIsStarting] = useState(false);

  // Start from the program's defaults once it has loaded.
  useEffect(() => {
    if (program) setChosenOptions(defaultChoices(program));
  }, [program]);

  if (loadError) {
    return <Text style={[styles.message, { color: colours.mutedText }]}>{loadError}</Text>;
  }
  if (!program) {
    return <ActivityIndicator style={styles.loading} size="large" color={colours.accent} />;
  }

  const machineIsBusy = status?.state === 'running' || status?.state === 'paused' || status?.state === 'scheduled';
  const remoteControlOff = status !== null && !status.remoteControlEnabled;

  const startProgram = async () => {
    setIsStarting(true);
    try {
      await washer.startProgram(program.id, chosenOptions);
      await refreshStatus();
      router.navigate('/');
    } catch (error) {
      Alert.alert("Couldn't start the program", error instanceof Error ? error.message : String(error));
    } finally {
      setIsStarting(false);
    }
  };

  const updateOption = (option: ProgramOption, newValue: string | number | boolean) =>
    setChosenOptions((previous) => ({ ...previous, [option.key]: newValue }));

  return (
    <>
      <Stack.Screen options={{ title: program.displayName }} />
      <ScrollView contentContainerStyle={styles.content}>
        {program.description ? (
          <Text style={[styles.description, { color: colours.mutedText }]}>{program.description}</Text>
        ) : null}
        {program.estimatedMinutes ? (
          <Text style={[styles.duration, { color: colours.text }]}>
            Takes about {formatDuration(program.estimatedMinutes)}
          </Text>
        ) : null}

        {program.options.length > 0 ? (
          <Card title="Options">
            {program.options.map((option) =>
              option.key === DELAY_OPTION_KEY && option.kind === 'range' ? (
                <DelayStartControl
                  key={option.key}
                  option={option}
                  estimatedMinutes={program.estimatedMinutes}
                  delayMinutes={Number(chosenOptions[option.key] ?? option.defaultValue)}
                  onChange={(newDelay) => updateOption(option, newDelay)}
                />
              ) : (
                <ProgramOptionControl
                  key={option.key}
                  option={option}
                  value={chosenOptions[option.key] ?? option.defaultValue}
                  onChange={(newValue) => updateOption(option, newValue)}
                />
              ),
            )}
          </Card>
        ) : null}

        {machineIsBusy ? (
          <Text style={[styles.warning, { color: colours.warningText, backgroundColor: colours.warningBackground }]}>
            A program is already running. Stop it before starting another.
          </Text>
        ) : null}
        {remoteControlOff ? (
          <Text style={[styles.warning, { color: colours.warningText, backgroundColor: colours.warningBackground }]}>
            Turn on remote control at the machine first, or it will ignore this.
          </Text>
        ) : null}

        <View style={styles.buttonRow}>
          <ActionButton label="Start" onPress={() => void startProgram()} busy={isStarting} disabled={machineIsBusy} />
        </View>
      </ScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  content: { padding: spacing.medium },
  loading: { marginTop: spacing.large },
  message: { padding: spacing.large, fontSize: 16, textAlign: 'center' },
  description: { fontSize: 16, marginBottom: spacing.small },
  duration: { fontSize: 17, fontWeight: '600', marginBottom: spacing.medium },
  warning: { borderRadius: 12, padding: spacing.medium, marginBottom: spacing.medium, overflow: 'hidden' },
  buttonRow: { flexDirection: 'row', marginTop: spacing.small, marginBottom: spacing.large },
});
