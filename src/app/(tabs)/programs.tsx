import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, SectionList, StyleSheet, Text, TextInput, View } from 'react-native';

import { ActionButton } from '../../components/ActionButton';
import { spacing, useThemeColours } from '../../components/theme';
import { organisePrograms } from '../../programs/organisePrograms';
import { useProgramListChoices } from '../../programs/useProgramListChoices';
import { formatDuration } from '../../washer/formatters';
import { usePrograms } from '../../washer/usePrograms';
import type { WasherProgram } from '../../washer/washerTypes';

/**
 * Every program the machine offers, with your favourites first and the rest
 * in groups. Tap one to set it up and start it. Tap ☆ to make it a
 * favourite, or "Edit list" to hide programs you never use. The search box
 * and the group buttons above the list stay put while the list scrolls.
 */
export default function ProgramsScreen() {
  const { programs, loadError, reload } = usePrograms();
  const { choices, toggleFavourite, toggleHidden } = useProgramListChoices();
  const [isEditing, setIsEditing] = useState(false);
  const [searchText, setSearchText] = useState('');
  const programListRef = useRef<SectionList<WasherProgram>>(null);
  /** The group last jumped to, so a jump that misses can be tried again. */
  const lastJumpSectionIndex = useRef(0);
  /** A jump that misses is tried again a few times, then given up. */
  const jumpRetriesLeft = useRef(0);
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

  const { sections, hiddenCount } = organisePrograms(programs, choices, {
    includeHidden: isEditing,
    searchText,
  });
  const favouriteIds = new Set(choices.favouriteIds);
  const hiddenIds = new Set(choices.hiddenIds);
  const isSearching = searchText.trim() !== '';

  /** Scrolls the list so a group's heading is at the top. */
  const jumpToSection = (sectionIndex: number) => {
    lastJumpSectionIndex.current = sectionIndex;
    jumpRetriesLeft.current = 3;
    programListRef.current?.scrollToLocation(jumpTarget(sectionIndex));
  };

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
        {isSearching ? null : (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.jumpButtons}>
            {sections.map((section, sectionIndex) => (
              <Pressable
                key={section.key}
                accessibilityRole="button"
                accessibilityLabel={`Jump to ${section.title}`}
                onPress={() => jumpToSection(sectionIndex)}
                style={[
                  styles.jumpButton,
                  {
                    borderColor: colours.border,
                    backgroundColor: colours.card,
                  },
                ]}
              >
                <Text style={[styles.jumpButtonLabel, { color: colours.text }]}>{section.title}</Text>
              </Pressable>
            ))}
          </ScrollView>
        )}
      </View>
      <SectionList
        ref={programListRef}
        sections={sections}
        keyboardShouldPersistTaps="handled"
        // Rows have different heights, so a far jump can miss at first; try
        // again once the list has drawn more of itself.
        onScrollToIndexFailed={() => {
          if (jumpRetriesLeft.current <= 0) return;
          jumpRetriesLeft.current -= 1;
          setTimeout(() => programListRef.current?.scrollToLocation(jumpTarget(lastJumpSectionIndex.current)), 250);
        }}
        ListEmptyComponent={
          isSearching ? (
            <Text style={[styles.message, styles.noMatches, { color: colours.mutedText }]}>
              No programs match "{searchText.trim()}".
            </Text>
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

/** Where to scroll to show a group: its heading, just below the top. */
function jumpTarget(sectionIndex: number) {
  return { sectionIndex, itemIndex: 0, viewOffset: 40, animated: true };
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
  jumpButtons: { gap: spacing.small, paddingVertical: spacing.small },
  jumpButton: {
    borderWidth: 1,
    borderRadius: 18,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  jumpButtonLabel: { fontSize: 14, fontWeight: '600' },
  noMatches: { marginTop: spacing.large },
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
