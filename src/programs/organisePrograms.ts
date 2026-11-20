import type { WasherProgram } from '../washer/washerTypes';
import { FABRIC_DISPLAY_ORDER, fabricTitle, fabricTypeOf, type FabricTypeId } from './fabricTypes';

/** What you've chosen for the program list. Saved on the phone. */
export interface ProgramListChoices {
  /** In the order you starred them. */
  favouriteIds: string[];
  hiddenIds: string[];
  /** The fabric chip picked at the top of the list, or null for "All". */
  fabricFilter: FabricTypeId | null;
}

export const NO_PROGRAM_LIST_CHOICES: ProgramListChoices = { favouriteIds: [], hiddenIds: [], fabricFilter: null };

export interface ProgramSection {
  key: string;
  title: string;
  data: WasherProgram[];
}

export interface OrganisedPrograms {
  sections: ProgramSection[];
  hiddenCount: number;
  /** The fabric chips worth showing: only fabrics this machine has programs for. */
  availableFabrics: FabricTypeId[];
}

/**
 * True when every word typed appears at the start of a word in the
 * program's name or description, so "cot 40" finds "Cottons 40 °C".
 */
export function programMatchesSearch(program: WasherProgram, searchText: string): boolean {
  const typedWords = searchText.toLowerCase().split(/\s+/).filter(Boolean);
  if (typedWords.length === 0) return true;
  const programWords = `${program.displayName} ${program.description}`.toLowerCase().split(/[^a-z0-9]+/);
  return typedWords.every((typedWord) => programWords.some((programWord) => programWord.startsWith(typedWord)));
}

/**
 * Builds the Programs screen: favourites first, then a section per fabric.
 * With a fabric chip picked, only that fabric's programs are listed.
 * Hidden programs are left out, unless you're editing the list, when they
 * get a "Hidden" section of their own at the end so you can bring them back.
 * With search text, only the matching programs are listed, in one section.
 */
export function organisePrograms(
  allPrograms: WasherProgram[],
  choices: ProgramListChoices,
  { includeHidden, searchText = '' }: { includeHidden: boolean; searchText?: string },
): OrganisedPrograms {
  const hiddenIds = new Set(choices.hiddenIds);
  const fabricById = new Map(allPrograms.map((program) => [program.id, fabricTypeOf(program)]));
  const availableFabrics = FABRIC_DISPLAY_ORDER.filter((fabric) =>
    allPrograms.some((program) => fabricById.get(program.id) === fabric),
  );
  // A saved fabric this machine has no programs for counts as "All".
  const fabricFilter =
    choices.fabricFilter && availableFabrics.includes(choices.fabricFilter) ? choices.fabricFilter : null;
  const programs = fabricFilter
    ? allPrograms.filter((program) => fabricById.get(program.id) === fabricFilter)
    : allPrograms;
  const hiddenCount = programs.filter((program) => hiddenIds.has(program.id)).length;

  if (searchText.trim()) {
    const matches = programs.filter(
      (program) => (includeHidden || !hiddenIds.has(program.id)) && programMatchesSearch(program, searchText),
    );
    const sections = matches.length > 0 ? [{ key: 'results', title: 'Matching programs', data: matches }] : [];
    return { sections, hiddenCount, availableFabrics };
  }
  const favouriteIds = new Set(choices.favouriteIds);
  const programById = new Map(programs.map((program) => [program.id, program]));

  const hidden = programs.filter((program) => hiddenIds.has(program.id));
  // A program that is both hidden and a favourite shows as hidden only.
  const favourites = choices.favouriteIds
    .map((programId) => programById.get(programId))
    .filter((program): program is WasherProgram => program !== undefined && !hiddenIds.has(program.id));
  const everythingElse = programs.filter((program) => !hiddenIds.has(program.id) && !favouriteIds.has(program.id));

  const sections: ProgramSection[] = [{ key: 'favourites', title: 'Favourites', data: favourites }];
  for (const fabric of FABRIC_DISPLAY_ORDER) {
    sections.push({
      key: fabric,
      title: fabricTitle(fabric),
      data: everythingElse.filter((program) => fabricById.get(program.id) === fabric),
    });
  }
  if (includeHidden) sections.push({ key: 'hidden', title: 'Hidden', data: hidden });

  return { sections: sections.filter((section) => section.data.length > 0), hiddenCount, availableFabrics };
}

/** Picks a fabric chip, or goes back to "All" when the picked one is tapped again. */
export function chooseFabric(choices: ProgramListChoices, fabric: FabricTypeId | null): ProgramListChoices {
  return { ...choices, fabricFilter: choices.fabricFilter === fabric ? null : fabric };
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
    hiddenIds: isHidden
      ? choices.hiddenIds.filter((hiddenId) => hiddenId !== programId)
      : [...choices.hiddenIds, programId],
  };
}
