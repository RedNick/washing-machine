import type { WasherProgram } from '../../washer/washerTypes';
import { NO_PROGRAM_LIST_CHOICES, organisePrograms, toggleFavourite, toggleHidden } from '../organisePrograms';
import { groupForProgram } from '../programGroups';

/** A program with just enough filled in for the list. */
function makeProgram(lastIdSegment: string, displayName: string): WasherProgram {
  return {
    id: `PROGRAMS.WM_WD.${lastIdSegment}`,
    displayName,
    description: '',
    estimatedMinutes: null,
    isBuiltIn: true,
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

describe('groupForProgram', () => {
  it.each([
    [cottons, 'everyday'],
    [eco, 'everyday'],
    [care14, 'quick'],
    [wool, 'delicates'],
    [antiAllergy, 'care'],
    [rinse, 'rinseAndSpin'],
    [mystery, 'other'],
    [makeProgram('IOT_WASH_RAPID_COTTON', 'Rapid cotton'), 'quick'],
    [makeProgram('SECOND_HAND', 'Second hand'), 'other'],
  ])('%#: puts %s in %s', (program, expectedGroup) => {
    expect(groupForProgram(program)).toBe(expectedGroup);
  });
});

describe('organisePrograms', () => {
  const sectionSummary = (choices = NO_PROGRAM_LIST_CHOICES, includeHidden = false) =>
    organisePrograms(allPrograms, choices, { includeHidden }).sections.map((section) => [
      section.title,
      section.data.map((program) => program.displayName),
    ]);

  it('groups everything when nothing is starred or hidden', () => {
    expect(sectionSummary()).toEqual([
      ['Everyday', ['Cottons', 'Eco 40-60']],
      ['Quick', ['Care 14']],
      ['Delicates', ['Wool']],
      ['Care and hygiene', ['Anti-allergy']],
      ['Rinse and spin', ['Rinse']],
      ['Other programs', ['Mystery']],
    ]);
  });

  it('puts favourites first, in the order they were starred, and takes them out of their group', () => {
    let choices = toggleFavourite(NO_PROGRAM_LIST_CHOICES, wool.id);
    choices = toggleFavourite(choices, cottons.id);
    expect(sectionSummary(choices).slice(0, 2)).toEqual([
      ['Favourites', ['Wool', 'Cottons']],
      ['Everyday', ['Eco 40-60']],
    ]);
  });

  it('leaves hidden programs out, and counts them', () => {
    const choices = toggleHidden(toggleHidden(NO_PROGRAM_LIST_CHOICES, rinse.id), mystery.id);
    const organised = organisePrograms(allPrograms, choices, { includeHidden: false });
    expect(organised.hiddenCount).toBe(2);
    expect(organised.sections.map((section) => section.title)).not.toContain('Rinse and spin');
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
    expect(sectionSummary(choices)[0][0]).toBe('Everyday');
  });

  it('un-stars and un-hides on a second tap', () => {
    const starredTwice = toggleFavourite(toggleFavourite(NO_PROGRAM_LIST_CHOICES, wool.id), wool.id);
    const hiddenTwice = toggleHidden(toggleHidden(NO_PROGRAM_LIST_CHOICES, wool.id), wool.id);
    expect(starredTwice.favouriteIds).toEqual([]);
    expect(hiddenTwice.hiddenIds).toEqual([]);
  });
});
