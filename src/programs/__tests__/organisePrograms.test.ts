import type { WasherProgram } from '../../washer/washerTypes';
import {
  NO_PROGRAM_LIST_CHOICES,
  chooseFabric,
  organisePrograms,
  programMatchesSearch,
  toggleFavourite,
  toggleHidden,
} from '../organisePrograms';
import { fabricTypeOf } from '../fabricTypes';

/** A program with just enough filled in for the list. */
function makeProgram(
  lastIdSegment: string,
  displayName: string,
  fabricHint: WasherProgram['fabricHint'] = null,
): WasherProgram {
  return {
    id: `PROGRAMS.WM_WD.${lastIdSegment}`,
    displayName,
    description: '',
    estimatedMinutes: null,
    isBuiltIn: true,
    fabricHint,
    options: [],
  };
}

const cottons = makeProgram('COTTONS', 'Cottons');
const eco = makeProgram('ECO_40_60_NEW_ENERGY_LABEL', 'Eco 40-60');
const care14 = makeProgram('CARE_14', 'Care 14');
const wool = makeProgram('WOOL', 'Wool');
const antiAllergy = makeProgram('ANTI_ALLERGY', 'Anti-allergy');
const rinse = makeProgram('RINSE', 'Rinse');
const mystery = makeProgram('IOT_WASH_MYSTERY', 'Mystery');
const allPrograms = [cottons, eco, care14, wool, antiAllergy, rinse, mystery];

describe('fabricTypeOf', () => {
  it.each([
    [cottons, 'cotton'],
    [eco, 'mixed'],
    [care14, 'mixed'],
    [wool, 'delicates'],
    [antiAllergy, 'mixed'],
    [rinse, 'mixed'],
    [mystery, 'mixed'],
    [makeProgram('IOT_WASH_SILK', 'Silk'), 'delicates'],
    [makeProgram('SYNTHETIC_AND_COLOURED', 'Synthetic and coloured'), 'synthetics'],
    [makeProgram('IOT_WASH_MIX_AND_COLOURED_59', 'Mix and coloured 59'), 'mixed'],
    [makeProgram('IOT_WASH_DELICATE_WHITES', 'Delicate whites'), 'delicates'],
    [makeProgram('IOT_WASH_COLOURED_SHIRTS', 'Coloured shirts'), 'cotton'],
    [makeProgram('IOT_WASH_RAPID_59', 'Rapid 59'), 'mixed'],
    [makeProgram('RAPID_WASH_AND_DRY_59_MIN', 'Rapid wash and dry 59 min'), 'mixed'],
    [makeProgram('HIGH_DRY', 'High dry'), 'drying'],
    [makeProgram('WOOL_DRY', 'Wool dry'), 'drying'],
    [makeProgram('TUMBLING', 'Tumbling'), 'drying'],
    [makeProgram('IOT_DRY_WARM_EMBRACE', 'Dry warm embrace'), 'drying'],
    [makeProgram('IOT_DRY_SHIRTS', 'Dry shirts'), 'drying'],
  ])('%#: puts %s under %s', (program, expectedFabric) => {
    expect(fabricTypeOf(program)).toBe(expectedFabric);
  });

  it("sorts programs named after a colour by h.on's dryType hint", () => {
    expect(fabricTypeOf(makeProgram('IOT_WASH_COLOURED', 'Coloured', 'synthetics'))).toBe('synthetics');
    expect(fabricTypeOf(makeProgram('IOT_WASH_WHITES', 'Whites', 'cotton'))).toBe('cotton');
    expect(fabricTypeOf(makeProgram('IOT_WASH_DARK', 'Dark', null))).toBe('mixed');
  });
});

describe('organisePrograms', () => {
  const sectionSummary = (choices = NO_PROGRAM_LIST_CHOICES, includeHidden = false) =>
    organisePrograms(allPrograms, choices, { includeHidden }).sections.map((section) => [
      section.title,
      section.data.map((program) => program.displayName),
    ]);

  it('sorts everything by fabric when nothing is starred or hidden', () => {
    expect(sectionSummary()).toEqual([
      ['Cotton', ['Cottons']],
      ['Mixed', ['Eco 40-60', 'Care 14', 'Anti-allergy', 'Rinse', 'Mystery']],
      ['Delicates', ['Wool']],
    ]);
  });

  it('offers chips only for fabrics this machine has', () => {
    expect(organisePrograms(allPrograms, NO_PROGRAM_LIST_CHOICES, { includeHidden: false }).availableFabrics).toEqual([
      'cotton',
      'mixed',
      'delicates',
    ]);
  });

  it('lists only the chosen fabric, favourites included', () => {
    const choices = chooseFabric(toggleFavourite(NO_PROGRAM_LIST_CHOICES, wool.id), 'mixed');
    expect(sectionSummary(choices)).toEqual([['Mixed', ['Eco 40-60', 'Care 14', 'Anti-allergy', 'Rinse', 'Mystery']]]);
    const delicatesOnly = chooseFabric(choices, 'delicates');
    expect(sectionSummary(delicatesOnly)).toEqual([['Favourites', ['Wool']]]);
  });

  it('goes back to All when the chosen chip is tapped again', () => {
    const choices = chooseFabric(chooseFabric(NO_PROGRAM_LIST_CHOICES, 'cotton'), 'cotton');
    expect(choices.fabricFilter).toBeNull();
  });

  it('treats a saved fabric this machine has no programs for as All', () => {
    const choices = chooseFabric(NO_PROGRAM_LIST_CHOICES, 'drying');
    expect(sectionSummary(choices).map(([title]) => title)).toEqual(['Cotton', 'Mixed', 'Delicates']);
  });

  it('searches within the chosen fabric', () => {
    const choices = chooseFabric(NO_PROGRAM_LIST_CHOICES, 'delicates');
    expect(organisePrograms(allPrograms, choices, { includeHidden: false, searchText: 'co' }).sections).toEqual([]);
  });

  it('puts favourites first, in the order they were starred, and takes them out of their group', () => {
    let choices = toggleFavourite(NO_PROGRAM_LIST_CHOICES, wool.id);
    choices = toggleFavourite(choices, cottons.id);
    expect(sectionSummary(choices).slice(0, 2)).toEqual([
      ['Favourites', ['Wool', 'Cottons']],
      ['Mixed', ['Eco 40-60', 'Care 14', 'Anti-allergy', 'Rinse', 'Mystery']],
    ]);
  });

  it('leaves hidden programs out, and counts them', () => {
    const choices = toggleHidden(toggleHidden(NO_PROGRAM_LIST_CHOICES, rinse.id), mystery.id);
    const organised = organisePrograms(allPrograms, choices, { includeHidden: false });
    expect(organised.hiddenCount).toBe(2);
    expect(organised.sections.flatMap((section) => section.data)).not.toContain(rinse);
  });

  it('shows hidden programs in their own section while editing', () => {
    const choices = toggleHidden(NO_PROGRAM_LIST_CHOICES, rinse.id);
    expect(sectionSummary(choices, true).at(-1)).toEqual(['Hidden', ['Rinse']]);
  });

  it('shows a hidden favourite as hidden only', () => {
    const choices = toggleHidden(toggleFavourite(NO_PROGRAM_LIST_CHOICES, wool.id), wool.id);
    expect(sectionSummary(choices).map(([title]) => title)).not.toContain('Favourites');
  });

  it('ignores saved programs this machine no longer offers', () => {
    const choices = toggleFavourite(NO_PROGRAM_LIST_CHOICES, 'PROGRAMS.WM_WD.GONE');
    expect(sectionSummary(choices)[0][0]).toBe('Cotton');
  });

  it('un-stars and un-hides on a second tap', () => {
    const starredTwice = toggleFavourite(toggleFavourite(NO_PROGRAM_LIST_CHOICES, wool.id), wool.id);
    const hiddenTwice = toggleHidden(toggleHidden(NO_PROGRAM_LIST_CHOICES, wool.id), wool.id);
    expect(starredTwice.favouriteIds).toEqual([]);
    expect(hiddenTwice.hiddenIds).toEqual([]);
  });
});

describe('program search', () => {
  it('matches the start of words in the name, in any order', () => {
    expect(programMatchesSearch(eco, 'eco')).toBe(true);
    expect(programMatchesSearch(eco, '40 ec')).toBe(true);
    expect(programMatchesSearch(cottons, 'tons')).toBe(false);
    expect(programMatchesSearch(cottons, '')).toBe(true);
  });

  it('lists only the matches, in one section, leaving out hidden ones', () => {
    const choices = { favouriteIds: [cottons.id], hiddenIds: [wool.id], fabricFilter: null };
    const { sections } = organisePrograms(allPrograms, choices, { includeHidden: false, searchText: 'co' });
    expect(sections.map((section) => [section.title, section.data.map((program) => program.displayName)])).toEqual([
      ['Matching programs', ['Cottons']],
    ]);
    expect(organisePrograms(allPrograms, choices, { includeHidden: false, searchText: 'wool' }).sections).toEqual([]);
  });
});
