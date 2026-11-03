import type { WasherProgram } from '../washer/washerTypes';

/**
 * Sorts programs into a few groups so a long list is easy to scan.
 *
 * h.on doesn't say what kind of program each one is, so this goes by words
 * in the program's id and name. Anything that doesn't match ends up in
 * "Other programs".
 */

export type ProgramGroupId =
  | 'everyday'
  | 'quick'
  | 'washAndDry'
  | 'dryOnly'
  | 'delicates'
  | 'care'
  | 'rinseAndSpin'
  | 'other';

interface ProgramGroup {
  id: ProgramGroupId;
  title: string;
  /**
   * Lower-case words or phrases that put a program in this group. Each must
   * start a word, so "allerg" matches "Anti-allergy" but "eco" doesn't match
   * "second".
   */
  keywords: string[];
}

/**
 * Checked in this order and the first match wins, so the more specific
 * groups come first: "Quick wash cotton" is quick, "Wool rinse" is delicate,
 * and anything that dries (washer dryers) goes under the drying groups.
 */
const GROUPS_IN_MATCH_ORDER: ProgramGroup[] = [
  {
    id: 'washAndDry',
    title: 'Wash and dry',
    keywords: ['wash dry', 'wash and dry', 'wash & dry', 'wash&dry', 'wash+dry', 'washdry', 'wash n dry', 'non stop', 'nonstop', 'one cycle'],
  },
  { id: 'dryOnly', title: 'Drying only', keywords: ['dry', 'drying', 'tumble'] },
  { id: 'rinseAndSpin', title: 'Rinse and spin', keywords: ['rinse', 'spin', 'drain'] },
  {
    id: 'quick',
    title: 'Quick',
    keywords: ['quick', 'rapid', 'fast', 'express', 'refresh', 'daily 20', 'care 14', '14 min', '15 min', '20 min', '30 min'],
  },
  {
    id: 'delicates',
    title: 'Delicates',
    keywords: ['wool', 'delicate', 'silk', 'hand wash', 'lingerie', 'underwear', 'cashmere', 'curtain', 'down'],
  },
  {
    id: 'care',
    title: 'Care and hygiene',
    keywords: ['allerg', 'hygien', 'steam', 'baby', 'sanit', 'bed linen', 'duvet', 'towel', 'shirt', 'sport', 'outdoor', 'jeans', 'dark'],
  },
  {
    id: 'everyday',
    title: 'Everyday',
    keywords: ['cotton', 'eco', 'mix', 'synthetic', 'colour', 'color', 'daily', 'auto', 'whites', 'smart'],
  },
];

const OTHER_GROUP: ProgramGroup = { id: 'other', title: 'Other programs', keywords: [] };

/** The order the groups appear in on screen. */
export const GROUP_DISPLAY_ORDER: ProgramGroupId[] = [
  'everyday',
  'quick',
  'washAndDry',
  'dryOnly',
  'delicates',
  'care',
  'rinseAndSpin',
  'other',
];

export function groupTitle(groupId: ProgramGroupId): string {
  return [...GROUPS_IN_MATCH_ORDER, OTHER_GROUP].find((group) => group.id === groupId)?.title ?? OTHER_GROUP.title;
}

/** Which group a program belongs in. */
export function groupForProgram(program: Pick<WasherProgram, 'id' | 'displayName'>): ProgramGroupId {
  // "PROGRAMS.WM_WD.CARE_14" → "care 14", plus the display name.
  const idWords = (program.id.split('.').pop() ?? program.id).replace(/_/g, ' ');
  const searchText = `${idWords} ${program.displayName}`.toLowerCase();
  const startsAWord = (keyword: string) => new RegExp(`(^|[^a-z])${keyword}`).test(searchText);
  const match = GROUPS_IN_MATCH_ORDER.find((group) => group.keywords.some(startsAWord));
  return match?.id ?? 'other';
}
