import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatClockTimeFromNow, formatDuration } from '../washer/formatters';
import type { ProgramOption } from '../washer/washerTypes';
import { spacing, useThemeColours } from './theme';

/**
 * How you'd like to set the delay: start straight away, pick when the wash
 * starts, or pick when it should be done.
 */
type DelayMode = 'now' | 'startAt' | 'finishBy';

interface DelayStartControlProps {
  option: Extract<ProgramOption, { kind: 'range' }>;
  /** The program's length, needed to work out a start time from a finish time. */
  estimatedMinutes: number | null;
  /** The delay in minutes, as the machine counts it: from now until the wash starts. */
  delayMinutes: number;
  onChange: (delayMinutes: number) => void;
}

/**
 * Delay start, shown as clock times. The machine only takes delays in fixed
 * steps (usually 30 minutes), so − and + move the time by one step and the
 * times shown are the exact ones the machine will use.
 */
export function DelayStartControl({ option, estimatedMinutes, delayMinutes, onChange }: DelayStartControlProps) {
  const colours = useThemeColours();
  const [mode, setMode] = useState<DelayMode>(delayMinutes > 0 ? 'startAt' : 'now');
  const canPickFinishTime = estimatedMinutes !== null && estimatedMinutes > 0;

  const smallestDelay = Math.max(option.minimum, option.step);
  const chooseMode = (newMode: DelayMode) => {
    setMode(newMode);
    if (newMode === 'now') onChange(0);
    else if (delayMinutes === 0) onChange(smallestDelay);
  };

  const stepBy = (direction: 1 | -1) => {
    const nextDelay = delayMinutes + direction * option.step;
    onChange(Math.min(option.maximum, Math.max(smallestDelay, nextDelay)));
  };

  const startTime = formatClockTimeFromNow(delayMinutes);
  const finishTime = canPickFinishTime ? formatClockTimeFromNow(delayMinutes + estimatedMinutes) : null;
  const pickedTime = mode === 'finishBy' && finishTime ? finishTime : startTime;

  // What happens, in a sentence under the controls.
  let summary: string;
  if (mode === 'now') {
    summary = finishTime ? `Starts straight away and finishes about ${finishTime}.` : 'Starts straight away.';
  } else if (mode === 'startAt') {
    summary = `Starts in ${formatDuration(delayMinutes)}${finishTime ? `, finishes about ${finishTime}` : ''}.`;
  } else {
    summary = `Starts at ${startTime} (in ${formatDuration(delayMinutes)}) so it's done by ${finishTime}.`;
  }

  const modeChoices: { mode: DelayMode; label: string }[] = [
    { mode: 'now', label: 'Start now' },
    { mode: 'startAt', label: 'Start at' },
    ...(canPickFinishTime ? [{ mode: 'finishBy' as const, label: 'Finish by' }] : []),
  ];

  return (
    <View style={styles.block}>
      <Text style={[styles.label, { color: colours.text }]}>{option.label}</Text>
      <View style={styles.chips}>
        {modeChoices.map((choice) => {
          const isSelected = mode === choice.mode;
          return (
            <Pressable
              key={choice.mode}
              accessibilityRole="radio"
              accessibilityState={{ selected: isSelected }}
              onPress={() => chooseMode(choice.mode)}
              style={[
                styles.chip,
                {
                  backgroundColor: isSelected ? colours.accent : colours.card,
                  borderColor: isSelected ? colours.accent : colours.border,
                },
              ]}
            >
              <Text style={{ color: isSelected ? colours.accentText : colours.text, fontWeight: '600' }}>
                {choice.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {mode !== 'now' ? (
        <View style={styles.timeRow}>
          <StepperButton label="−" onPress={() => stepBy(-1)} disabled={delayMinutes <= smallestDelay} />
          <Text style={[styles.time, { color: colours.text }]}>{pickedTime}</Text>
          <StepperButton label="+" onPress={() => stepBy(1)} disabled={delayMinutes >= option.maximum} />
        </View>
      ) : null}
      <Text style={[styles.summary, { color: colours.mutedText }]}>{summary}</Text>
    </View>
  );
}

function StepperButton({ label, onPress, disabled }: { label: string; onPress: () => void; disabled: boolean }) {
  const colours = useThemeColours();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label === '+' ? 'Later' : 'Earlier'}
      onPress={onPress}
      disabled={disabled}
      style={[styles.stepperButton, { borderColor: colours.border, opacity: disabled ? 0.4 : 1 }]}
    >
      <Text style={[styles.stepperButtonLabel, { color: colours.accent }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  block: { paddingVertical: spacing.small },
  label: { fontSize: 16, fontWeight: '500', marginBottom: 6 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.small },
  chip: { borderWidth: 1, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8 },
  timeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.medium,
    marginTop: spacing.medium,
  },
  time: { minWidth: 140, textAlign: 'center', fontSize: 24, fontWeight: '700' },
  stepperButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperButtonLabel: { fontSize: 22, fontWeight: '700' },
  summary: { fontSize: 14, marginTop: spacing.small },
});
