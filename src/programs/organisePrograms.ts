import type { WasherProgram } from '../washer/washerTypes';
import { GROUP_DISPLAY_ORDER, groupForProgram, groupTitle } from './programGroups';

/** What you've chosen for the program list. Saved on the phone. */
export interface ProgramListChoices {
  /** In the order you starred them. */
  favouriteIds: string[];
  hiddenIds: string[];
}

export const NO_PROGRAM_LIST_CHOICES: ProgramListChoices = { favouriteIds: [], hiddenIds: [] };

export interface ProgramSection {
  key: string;
  title: string;
  data: WasherProgram[];
}

export interface OrganisedPrograms {
  sections: ProgramSection[];
  hiddenCount: number;
}

/**
 * Builds the Programs screen: favourites first, then each group. Hidden
 * programs are left out, unless you're editing the list, when they get a
 * "Hidden" section of their own at the end so you can bring them back.
 */
export function organisePrograms(
  programs: WasherProgram[],
  choices: ProgramListChoices,
  { includeHidden }: { includeHidden: boolean },
): OrganisedPrograms {
  const hiddenIds = new Set(choices.hiddenIds);
  const favouriteIds = new Set(choices.favouriteIds);
  const programById = new Map(programs.map((program) => [program.id, program]));

  const hidden = programs.filter((program) => hiddenIds.has(program.id));
  // A program that is both hidden and a favourite shows as hidden only.
  const favourites = choices.favouriteIds
    .map((programId) => programById.get(programId))
    .filter((program): program is WasherProgram => program !== undefined && !hiddenIds.has(program.id));
  const everythingElse = programs.filter((program) => !hiddenIds.has(program.id) && !favouriteIds.has(program.id));

  const sections: ProgramSection[] = [{ key: 'favourites', title: 'Favourites', data: favourites }];
  for (const groupId of GROUP_DISPLAY_ORDER) {
    sections.push({
      key: groupId,
      title: groupTitle(groupId),
      data: everythingElse.filter((program) => groupForProgram(program) === groupId),
    });
  }
  if (includeHidden) sections.push({ key: 'hidden', title: 'Hidden', data: hidden });

  return { sections: sections.filter((section) => section.data.length > 0), hiddenCount: hidden.length };
}

/** Stars or un-stars a program. A new favourite goes to the end. */
export function toggleFavourite(choices: ProgramListChoices, programId: string): ProgramListChoices {
  const isFavourite = choices.favouriteIds.includes(programId);
  return {
    ...choices,
    favouriteIds: isFavourite
      ? choices.favouriteIds.filter((favouriteId) => favouriteId !== programId)
      : [...choices.favouriteIds, programId],
  };
}

/** Hides a program, or shows it again. */
export function toggleHidden(choices: ProgramListChoices, programId: string): ProgramListChoices {
  const isHidden = choices.hiddenIds.includes(programId);
  return {
    ...choices,
    hiddenIds: isHidden ? choices.hiddenIds.filter((hiddenId) => hiddenId !== programId) : [...choices.hiddenIds, programId],
  };
}
