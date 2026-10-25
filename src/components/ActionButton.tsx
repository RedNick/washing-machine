import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native';

import { useThemeColours } from './theme';

type ButtonLook = 'primary' | 'secondary' | 'danger';

interface ActionButtonProps {
  label: string;
  onPress: () => void;
  look?: ButtonLook;
  disabled?: boolean;
  /** Shows a spinner instead of the label, e.g. while a command is sent. */
  busy?: boolean;
}

/** A large, easy-to-hit button. */
export function ActionButton({ label, onPress, look = 'primary', disabled = false, busy = false }: ActionButtonProps) {
  const colours = useThemeColours();
  const isInactive = disabled || busy;

  const backgroundColor = look === 'primary' ? colours.accent : 'transparent';
  const textColour = look === 'primary' ? colours.accentText : look === 'danger' ? colours.danger : colours.accent;
  const borderColor = look === 'primary' ? colours.accent : look === 'danger' ? colours.danger : colours.border;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: isInactive, busy }}
      onPress={onPress}
      disabled={isInactive}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor, borderColor, opacity: isInactive ? 0.5 : pressed ? 0.8 : 1 },
      ]}
    >
      {busy ? <ActivityIndicator color={textColour} /> : <Text style={[styles.label, { color: textColour }]}>{label}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    flex: 1,
    minHeight: 52,
    borderRadius: 14,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  label: { fontSize: 17, fontWeight: '700' },
});
