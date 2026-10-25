import { router } from 'expo-router';
import { ActivityIndicator, Pressable, SectionList, StyleSheet, Text, View } from 'react-native';

import { ActionButton } from '../../components/ActionButton';
import { spacing, useThemeColours } from '../../components/theme';
import { formatDuration } from '../../washer/formatters';
import { usePrograms } from '../../washer/usePrograms';
import type { WasherProgram } from '../../washer/washerTypes';

/** Every program the machine offers. Tap one to set it up and start it. */
export default function ProgramsScreen() {
  const { programs, loadError, reload } = usePrograms();
  const colours = useThemeColours();

  if (loadError) {
    return (
      <View style={styles.centred}>
        <Text style={[styles.message, { color: colours.mutedText }]}>{loadError}</Text>
        <View style={styles.buttonRow}>
          <ActionButton label="Try again" onPress={reload} />
        </View>
      </View>
    );
  }
  if (!programs) {
    return (
      <View style={styles.centred}>
        <ActivityIndicator size="large" color={colours.accent} />
      </View>
    );
  }

  const sections = [
    { title: 'On the machine', data: programs.filter((program) => program.isBuiltIn) },
    { title: 'Extra programs from h.on', data: programs.filter((program) => !program.isBuiltIn) },
  ].filter((section) => section.data.length > 0);

  return (
    <SectionList
      sections={sections}
      keyExtractor={(program) => program.id}
      contentContainerStyle={styles.list}
      stickySectionHeadersEnabled={false}
      renderSectionHeader={({ section }) => (
        <Text style={[styles.sectionTitle, { color: colours.mutedText }]}>{section.title}</Text>
      )}
      renderItem={({ item }) => <ProgramRow program={item} />}
    />
  );
}

function ProgramRow({ program }: { program: WasherProgram }) {
  const colours = useThemeColours();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={() => router.push({ pathname: '/program/[programId]', params: { programId: program.id } })}
      style={({ pressed }) => [
        styles.row,
        { backgroundColor: colours.card, borderColor: colours.border, opacity: pressed ? 0.7 : 1 },
      ]}
    >
      <View style={styles.rowText}>
        <Text style={[styles.programName, { color: colours.text }]}>{program.displayName}</Text>
        {program.description ? (
          <Text style={[styles.description, { color: colours.mutedText }]} numberOfLines={2}>
            {program.description}
          </Text>
        ) : null}
      </View>
      {program.estimatedMinutes ? (
        <Text style={[styles.duration, { color: colours.mutedText }]}>{formatDuration(program.estimatedMinutes)}</Text>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  centred: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.large },
  message: { fontSize: 16, textAlign: 'center' },
  buttonRow: { flexDirection: 'row', marginTop: spacing.medium, alignSelf: 'stretch' },
  list: { padding: spacing.medium },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginTop: spacing.small,
    marginBottom: spacing.small,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 14,
    borderWidth: StyleSheet.hairlineWidth,
    padding: spacing.medium,
    marginBottom: spacing.small,
  },
  rowText: { flex: 1, marginRight: spacing.small },
  programName: { fontSize: 17, fontWeight: '600' },
  description: { fontSize: 14, marginTop: 2 },
  duration: { fontSize: 15, fontWeight: '500' },
});
