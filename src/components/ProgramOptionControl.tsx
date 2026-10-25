import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

import { formatOptionValue } from '../washer/formatters';
import type { ProgramOption } from '../washer/washerTypes';
import { spacing, useThemeColours } from './theme';

interface ProgramOptionControlProps {
  option: ProgramOption;
  value: string | number | boolean;
  onChange: (newValue: string | number | boolean) => void;
}

/** The right control for one program option: chips, a switch or a stepper. */
export function ProgramOptionControl({ option, value, onChange }: ProgramOptionControlProps) {
  const colours = useThemeColours();

  if (option.kind === 'toggle') {
    return (
      <View style={styles.row}>
        <Text style={[styles.label, { color: colours.text }]}>{option.label}</Text>
        <Switch value={Boolean(value)} onValueChange={onChange} accessibilityLabel={option.label} />
      </View>
    );
  }

  if (option.kind === 'choice') {
    return (
      <View style={styles.block}>
        <Text style={[styles.label, { color: colours.text }]}>{option.label}</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {option.choices.map((choice) => {
            const isSelected = String(value) === choice;
            return (
              <Pressable
                key={choice}
                accessibilityRole="radio"
                accessibilityState={{ selected: isSelected }}
                onPress={() => onChange(choice)}
                style={[
                  styles.chip,
                  {
                    backgroundColor: isSelected ? colours.accent : colours.card,
                    borderColor: isSelected ? colours.accent : colours.border,
                  },
                ]}
              >
                <Text style={{ color: isSelected ? colours.accentText : colours.text, fontWeight: '600' }}>
                  {formatOptionValue(option.key, choice, option.unit)}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </View>
    );
  }

  // A number range, e.g. delay start: show − value + buttons.
  const numericValue = Number(value);
  const stepBy = (direction: 1 | -1) => {
    const nextValue = numericValue + direction * option.step;
    onChange(Math.min(option.maximum, Math.max(option.minimum, nextValue)));
  };
  return (
    <View style={styles.row}>
      <Text style={[styles.label, { color: colours.text }]}>{option.label}</Text>
      <View style={styles.stepper}>
        <StepperButton label="−" onPress={() => stepBy(-1)} disabled={numericValue <= option.minimum} />
        <Text style={[styles.stepperValue, { color: colours.text }]}>
          {formatOptionValue(option.key, numericValue, option.unit)}
        </Text>
        <StepperButton label="+" onPress={() => stepBy(1)} disabled={numericValue >= option.maximum} />
      </View>
    </View>
  );
}

function StepperButton({ label, onPress, disabled }: { label: string; onPress: () => void; disabled: boolean }) {
  const colours = useThemeColours();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label === '+' ? 'Increase' : 'Decrease'}
      onPress={onPress}
      disabled={disabled}
      style={[styles.stepperButton, { borderColor: colours.border, opacity: disabled ? 0.4 : 1 }]}
    >
      <Text style={[styles.stepperButtonLabel, { color: colours.accent }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.small,
  },
  block: { paddingVertical: spacing.small },
  label: { fontSize: 16, fontWeight: '500', marginBottom: 6 },
  chips: { gap: spacing.small, paddingVertical: 2 },
  chip: { borderWidth: 1, borderRadius: 20, paddingHorizontal: 14, paddingVertical: 8 },
  stepper: { flexDirection: 'row', alignItems: 'center', gap: spacing.small },
  stepperValue: { minWidth: 84, textAlign: 'center', fontSize: 16, fontWeight: '600' },
  stepperButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepperButtonLabel: { fontSize: 22, fontWeight: '700' },
});
