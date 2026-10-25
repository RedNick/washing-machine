import type { ReactNode } from 'react';
import { StyleSheet, Text, View, type ViewStyle } from 'react-native';

import { spacing, useThemeColours } from './theme';

/** A rounded white (or dark) panel with an optional heading. */
export function Card({ title, children, style }: { title?: string; children: ReactNode; style?: ViewStyle }) {
  const colours = useThemeColours();
  return (
    <View style={[styles.card, { backgroundColor: colours.card, borderColor: colours.border }, style]}>
      {title ? <Text style={[styles.title, { color: colours.mutedText }]}>{title}</Text> : null}
      {children}
    </View>
  );
}

/** A label on the left and a value on the right, e.g. "Water   2 571 L". */
export function DetailRow({ label, value }: { label: string; value: string }) {
  const colours = useThemeColours();
  return (
    <View style={styles.detailRow}>
      <Text style={[styles.detailLabel, { color: colours.mutedText }]}>{label}</Text>
      <Text style={[styles.detailValue, { color: colours.text }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    borderWidth: StyleSheet.hairlineWidth,
    padding: spacing.medium,
    marginBottom: spacing.medium,
  },
  title: {
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.small,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 6,
  },
  detailLabel: { fontSize: 16 },
  detailValue: { fontSize: 16, fontWeight: '600' },
});
