import { DRYING_OPTION_KEY, type ProgramOption } from '../washer/washerTypes';

/**
 * Washer dryers have two drying settings in h.on: a dryness level
 * ("dryLevel") and a drying time ("dryTime"). The machine only accepts one
 * or the other, so the app shows them as one "Drying" choice and splits it
 * back into the two settings when it starts a program.
 *
 * Choice values look like "none", "level:2" or "time:3".
 */

/** h.on's drying setting names. */
export const DRY_LEVEL_KEY = 'dryLevel';
export const DRY_TIME_KEY = 'dryTime';

const NO_DRYING = 'none';
const LEVEL_PREFIX = 'level:';
const TIME_PREFIX = 'time:';

/** What each dryness level is called on the machine (confirmed by Nicolas). */
const DRY_LEVEL_NAMES: Record<number, string> = {
  0: 'No drying',
  1: 'Iron dry',
  2: 'Cupboard dry',
  3: 'Extra dry',
};

/** Each step of h.on's drying time is half an hour (1 = 30 min, 4 = 2 h). */
export const MINUTES_PER_DRY_TIME_STEP = 30;

/** All the values an option allows, as numbers. */
function allowedNumbers(option: ProgramOption): number[] {
  if (option.kind === 'choice') return option.choices.map(Number).filter(Number.isFinite);
  if (option.kind === 'range') {
    const values: number[] = [];
    for (let value = option.minimum; value <= option.maximum; value += option.step) values.push(value);
    return values;
  }
  return [0, 1];
}

function defaultNumber(option: ProgramOption | undefined): number {
  if (!option) return 0;
  return Number(option.defaultValue) || 0;
}

/**
 * Replaces a program's dryLevel and dryTime options with one "Drying"
 * choice. Programs without either are returned unchanged.
 */
export function combineDryingOptions(options: ProgramOption[]): ProgramOption[] {
  const levelOption = options.find((option) => option.key === DRY_LEVEL_KEY);
  const timeOption = options.find((option) => option.key === DRY_TIME_KEY);
  if (!levelOption && !timeOption) return options;

  const levels = levelOption ? allowedNumbers(levelOption) : [];
  const times = timeOption ? allowedNumbers(timeOption) : [];
  const canSkipDrying = levels.includes(0) || times.includes(0);

  const choices = [
    ...(canSkipDrying ? [NO_DRYING] : []),
    ...levels.filter((level) => level > 0).map((level) => `${LEVEL_PREFIX}${level}`),
    ...times.filter((time) => time > 0).map((time) => `${TIME_PREFIX}${time}`),
  ];

  // The program's own default: a level if it has one, otherwise a time.
  const defaultLevel = defaultNumber(levelOption);
  const defaultTime = defaultNumber(timeOption);
  const defaultValue =
    defaultLevel > 0 ? `${LEVEL_PREFIX}${defaultLevel}` : defaultTime > 0 ? `${TIME_PREFIX}${defaultTime}` : choices[0];

  const drying: ProgramOption = {
    kind: 'choice',
    key: DRYING_OPTION_KEY,
    label: 'Drying',
    choices,
    defaultValue: choices.includes(defaultValue) ? defaultValue : choices[0],
    unit: null,
  };
  const otherOptions = options.filter((option) => option.key !== DRY_LEVEL_KEY && option.key !== DRY_TIME_KEY);
  return [...otherOptions, drying];
}

/** The dryLevel and dryTime values to send for a "Drying" choice. */
export function expandDryingChoice(choice: string): { [DRY_LEVEL_KEY]: number; [DRY_TIME_KEY]: number } {
  if (choice.startsWith(LEVEL_PREFIX)) {
    return { [DRY_LEVEL_KEY]: Number(choice.slice(LEVEL_PREFIX.length)), [DRY_TIME_KEY]: 0 };
  }
  if (choice.startsWith(TIME_PREFIX)) {
    return { [DRY_LEVEL_KEY]: 0, [DRY_TIME_KEY]: Number(choice.slice(TIME_PREFIX.length)) };
  }
  return { [DRY_LEVEL_KEY]: 0, [DRY_TIME_KEY]: 0 };
}

/** "level:2" → "Cupboard dry", "time:3" → 90 (minutes), "none" → "No drying". */
export function describeDryingChoice(choice: string): { label: string } | { minutes: number } {
  if (choice.startsWith(LEVEL_PREFIX)) {
    const level = Number(choice.slice(LEVEL_PREFIX.length));
    return { label: DRY_LEVEL_NAMES[level] ?? `Level ${level}` };
  }
  if (choice.startsWith(TIME_PREFIX)) {
    return { minutes: Number(choice.slice(TIME_PREFIX.length)) * MINUTES_PER_DRY_TIME_STEP };
  }
  return { label: DRY_LEVEL_NAMES[0] };
}
