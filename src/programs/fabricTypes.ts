import type { WasherProgram } from '../washer/washerTypes';

/**
 * Sorts programs by fabric, for the chips at the top of the Programs screen.
 *
 * h.on doesn't say which fabric a program is for, so this goes by words in
 * the program's id and name. Nicolas's choices (20 November 2026):
 * - Wool and silk count as Delicates.
 * - Programs for any fabric (Rapid 59, stain programs, Rinse, Spin...) go
 *   under Mixed.
 * - Drying-only programs get their own Drying chip.
 */

export type FabricTypeId = 'cotton' | 'synthetics' | 'mixed' | 'delicates' | 'drying';

interface FabricType {
  id: FabricTypeId;
  /**
   * Lower-case words or phrases that put a program under this fabric. Each
   * must start a word, so "mix" matches "Mixed" but "silk" doesn't match
   * "milk".
   */
  keywords: string[];
}

/**
 * Checked in this order and the first match wins, so the more specific
 * fabrics come first: "Delicate whites" is Delicates, "Synthetic and
 * coloured" is Synthetics, "Mix and coloured 59" is Mixed.
 */
const FABRICS_IN_MATCH_ORDER: FabricType[] = [
  {
    id: 'delicates',
    keywords: [
      'delicate',
      'wool',
      'silk',
      'cashmere',
      'lingerie',
      'underwear',
      'handwash',
      'hand wash',
      'soft care',
      'curtain',
      'masks',
    ],
  },
  {
    id: 'synthetics',
    keywords: [
      'synthetic',
      'technical',
      'sport',
      'gym',
      'ski',
      'swimsuit',
      'diving',
      'down jacket',
      'trainers',
      'backpack',
      'outdoor',
    ],
  },
  { id: 'mixed', keywords: ['mix', 'all in one', 'night and day', 'eco 40', '20 degrees'] },
  {
    id: 'cotton',
    keywords: [
      'cotton',
      'bed linen',
      'towel',
      'bathrobe',
      'tablecloth',
      'shirt',
      'baby',
      'playsuit',
      'pets',
      'cuddly',
      'mats',
      'men s trousers',
      'duvet',
    ],
  },
];

/** Words that only name a colour. These programs take their fabric from h.on's hint. */
const COLOUR_ONLY_KEYWORDS = ['white', 'colour', 'color', 'dark', 'denim', 'bleach'];

/** The chips' order on screen, and the section order when "All" is chosen. */
export const FABRIC_DISPLAY_ORDER: FabricTypeId[] = ['cotton', 'synthetics', 'mixed', 'delicates', 'drying'];

const FABRIC_TITLES: Record<FabricTypeId, string> = {
  cotton: 'Cotton',
  synthetics: 'Synthetics',
  mixed: 'Mixed',
  delicates: 'Delicates',
  drying: 'Drying',
};

export function fabricTitle(fabricTypeId: FabricTypeId): string {
  return FABRIC_TITLES[fabricTypeId];
}

/** True for a known fabric id, so an old or garbled saved choice is ignored. */
export function isFabricTypeId(value: unknown): value is FabricTypeId {
  return typeof value === 'string' && (FABRIC_DISPLAY_ORDER as string[]).includes(value);
}

/** "PROGRAMS.WM_WD.IOT_DRY_SHIRTS" + "Dry shirts" → "iot dry shirts dry shirts". */
function searchTextFor(program: Pick<WasherProgram, 'id' | 'displayName'>): string {
  const idWords = (program.id.split('.').pop() ?? program.id).replace(/_/g, ' ');
  return `${idWords} ${program.displayName}`.toLowerCase();
}

/**
 * Drying only: h.on's "IOT_DRY_..." programs, and anything else that dries
 * or tumbles without washing ("Low dry", "Wool dry", "Tumbling"). A "Rapid
 * wash and dry" washes too, so it follows its fabric instead.
 */
function isDryingOnly(searchText: string): boolean {
  if (/(^|[^a-z])iot dry /.test(searchText)) return true;
  const mentionsWashing = /(^|[^a-z])wash/.test(searchText);
  const mentionsDrying = /(^|[^a-z])(dry|drying|tumbl)/.test(searchText);
  return mentionsDrying && !mentionsWashing;
}

/** Which fabric chip a program belongs under. */
export function fabricTypeOf(program: Pick<WasherProgram, 'id' | 'displayName' | 'fabricHint'>): FabricTypeId {
  const searchText = searchTextFor(program);
  if (isDryingOnly(searchText)) return 'drying';

  const startsAWord = (keyword: string) => new RegExp(`(^|[^a-z])${keyword}`).test(searchText);
  const match = FABRICS_IN_MATCH_ORDER.find((fabric) => fabric.keywords.some(startsAWord));
  if (match) return match.id;

  // "Whites", "Coloured", "Darks"...: h.on's dryType says which fabric.
  if (COLOUR_ONLY_KEYWORDS.some(startsAWord) && program.fabricHint) return program.fabricHint;

  // Anything else suits any fabric (Rapid 59, Stain remover, Rinse, Spin...).
  return 'mixed';
}
