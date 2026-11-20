import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, SectionList, StyleSheet, Text, TextInput, View } from 'react-native';

import { ActionButton } from '../../components/ActionButton';
import { spacing, useThemeColours } from '../../components/theme';
import { fabricTitle, type FabricTypeId } from '../../programs/fabricTypes';
import { organisePrograms } from '../../programs/organisePrograms';
import { useProgramListChoices } from '../../programs/useProgramListChoices';
import { formatDuration } from '../../washer/formatters';
import { usePrograms } from '../../washer/usePrograms';
import type { WasherProgram } from '../../washer/washerTypes';

/**
 * Every program the machine offers, with your favourites first and the rest
 * sorted by fabric. Tap one to set it up and start it. Tap ☆ to make it a
 * favourite, or "Edit list" to hide programs you never use. The search box
 * and the fabric chips above the list stay put while the list scrolls; a
 * chip shows only that fabric's programs, one fabric at a time.
 */
export default function ProgramsScreen() {
  const { programs, loadError, reload } = usePrograms();
  const { choices, toggleFavourite, toggleHidden, chooseFabric } = useProgramListChoices();
  const [isEditing, setIsEditing] = useState(false);
  const [searchText, setSearchText] = useState('');
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

  const { sections, hiddenCount, availableFabrics } = organisePrograms(programs, choices, {
    includeHidden: isEditing,
    searchText,
  });
  const favouriteIds = new Set(choices.favouriteIds);
  const hiddenIds = new Set(choices.hiddenIds);
  const isSearching = searchText.trim() !== '';
  // A saved chip this machine has no programs for shows as "All".
  const chosenFabric =
    choices.fabricFilter && availableFabrics.includes(choices.fabricFilter) ? choices.fabricFilter : null;
  const fabricChips: { fabric: FabricTypeId | null; label: string }[] = [
    { fabric: null, label: 'All' },
    ...availableFabrics.map((fabric) => ({ fabric, label: fabricTitle(fabric) })),
  ];

  /** Why the list is empty, and what to tap to see more. */
  const emptyListMessage = isSearching
    ? chosenFabric
      ? `No ${fabricTitle(chosenFabric).toLowerCase()} programs match "${searchText.trim()}".`
      : `No programs match "${searchText.trim()}".`
    : chosenFabric
      ? `Every ${fabricTitle(chosenFabric).toLowerCase()} program is hidden.`
      : null;

  return (
    <View style={styles.screen}>
      <View style={[styles.toolbar, { borderColor: colours.border }]}>
        <TextInput
          value={searchText}
          onChangeText={setSearchText}
          placeholder="Search programs"
          placeholderTextColor={colours.mutedText}
          clearButtonMode="while-editing"
          autoCorrect={false}
          returnKeyType="search"
          accessibilityLabel="Search programs"
          style={[
            styles.searchBox,
            {
              color: colours.text,
              backgroundColor: colours.card,
              borderColor: colours.border,
            },
          ]}
        />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.fabricChips}>
          {fabricChips.map(({ fabric, label }) => {
            const isChosen = fabric === chosenFabric;
            return (
              <Pressable
                key={fabric ?? 'all'}
                accessibilityRole="button"
                accessibilityState={{ selected: isChosen }}
                accessibilityLabel={fabric ? `Show only ${label} programs` : 'Show all programs'}
                // Tapping the chosen chip again goes back to All.
                onPress={() => chooseFabric(fabric)}
                style={[
                  styles.fabricChip,
                  {
                    borderColor: isChosen ? colours.accent : colours.border,
                    backgroundColor: isChosen ? colours.accent : colours.card,
                  },
                ]}
              >
                <Text style={[styles.fabricChipLabel, { color: isChosen ? colours.accentText : colours.text }]}>
                  {label}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </View>
      <SectionList
        sections={sections}
        keyboardShouldPersistTaps="handled"
        ListEmptyComponent={
          emptyListMessage ? (
            <View style={styles.noMatches}>
              <Text style={[styles.message, { color: colours.mutedText }]}>{emptyListMessage}</Text>
              {chosenFabric ? <TextButton label="Show all fabrics" onPress={() => chooseFabric(null)} /> : null}
            </View>
          ) : undefined
        }
        keyExtractor={(program) => program.id}
        contentContainerStyle={styles.list}
        stickySectionHeadersEnabled={false}
        ListHeaderComponent={
          <View style={styles.listHeader}>
            <Text style={[styles.hint, { color: colours.mutedText }]}>
              {isEditing ? 'Hide the programs you never use.' : 'Tap ☆ to add a favourite.'}
            </Text>
            <TextButton label={isEditing ? 'Done' : 'Edit list'} onPress={() => setIsEditing(!isEditing)} />
          </View>
        }
        ListFooterComponent={
          !isEditing && hiddenCount > 0 ? (
            <View style={styles.footer}>
              <TextButton
                label={hiddenCount === 1 ? '1 hidden program · Show' : `${hiddenCount} hidden programs · Show`}
                onPress={() => setIsEditing(true)}
              />
            </View>
          ) : undefined
        }
        renderSectionHeader={({ section }) => (
          <Text style={[styles.sectionTitle, { color: colours.mutedText }]}>{section.title}</Text>
        )}
        renderItem={({ item }) => (
          <ProgramRow
            program={item}
            isEditing={isEditing}
            isFavourite={favouriteIds.has(item.id)}
            isHidden={hiddenIds.has(item.id)}
            onToggleFavourite={() => toggleFavourite(item.id)}
            onToggleHidden={() => toggleHidden(item.id)}
          />
        )}
      />
    </View>
  );
}

/** A small text-only button, for actions that shouldn't shout. */
function TextButton({ label, onPress }: { label: string; onPress: () => void }) {
  const colours = useThemeColours();
  return (
    <Pressable accessibilityRole="button" onPress={onPress} hitSlop={8}>
      <Text style={[styles.textButton, { color: colours.accent }]}>{label}</Text>
    </Pressable>
  );
}

interface ProgramRowProps {
  program: WasherProgram;
  /** While editing, the row shows Hide / Show instead of opening the program. */
  isEditing: boolean;
  isFavourite: boolean;
  isHidden: boolean;
  onToggleFavourite: () => void;
  onToggleHidden: () => void;
}

function ProgramRow({ program, isEditing, isFavourite, isHidden, onToggleFavourite, onToggleHidden }: ProgramRowProps) {
  const colours = useThemeColours();
  return (
    <Pressable
      accessibilityRole="button"
      disabled={isEditing}
      onPress={() =>
        router.push({
          pathname: '/program/[programId]',
          params: { programId: program.id },
        })
      }
      style={({ pressed }) => [
        styles.row,
        {
          backgroundColor: colours.card,
          borderColor: colours.border,
          opacity: pressed ? 0.7 : 1,
        },
      ]}
    >
      {isHidden ? null : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={
            isFavourite ? `Remove ${program.displayName} from favourites` : `Add ${program.displayName} to favourites`
          }
          onPress={onToggleFavourite}
          hitSlop={8}
          style={styles.star}
        >
          <Text style={[styles.starText, { color: isFavourite ? colours.accent : colours.mutedText }]}>
            {isFavourite ? '★' : '☆'}
          </Text>
        </Pressable>
      )}
      <View style={styles.rowText}>
        <Text style={[styles.programName, { color: colours.text }]}>{program.displayName}</Text>
        {program.description ? (
          <Text style={[styles.description, { color: colours.mutedText }]} numberOfLines={2}>
            {program.description}
          </Text>
        ) : null}
      </View>
      {isEditing ? (
        <TextButton label={isHidden ? 'Show' : 'Hide'} onPress={onToggleHidden} />
      ) : program.estimatedMinutes ? (
        <Text style={[styles.duration, { color: colours.mutedText }]}>{formatDuration(program.estimatedMinutes)}</Text>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  centred: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.large,
  },
  message: { fontSize: 16, textAlign: 'center' },
  buttonRow: {
    flexDirection: 'row',
    marginTop: spacing.medium,
    alignSelf: 'stretch',
  },
  screen: { flex: 1 },
  toolbar: {
    paddingHorizontal: spacing.medium,
    paddingTop: spacing.medium,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  searchBox: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 12,
    paddingHorizontal: spacing.medium,
    paddingVertical: 10,
    fontSize: 16,
  },
  fabricChips: { gap: spacing.small, paddingVertical: spacing.small },
  fabricChip: {
    borderWidth: 1,
    borderRadius: 18,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  fabricChipLabel: { fontSize: 14, fontWeight: '600' },
  noMatches: { marginTop: spacing.large, alignItems: 'center', gap: spacing.small },
  list: { padding: spacing.medium },
  listHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  hint: { fontSize: 14, flex: 1, marginRight: spacing.small },
  textButton: { fontSize: 16, fontWeight: '600' },
  footer: { alignItems: 'center', marginTop: spacing.medium },
  star: { marginRight: spacing.medium },
  starText: { fontSize: 24 },
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
