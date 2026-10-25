import type { HonCommandCatalogue, HonCommandDefinition, HonParameterDefinition } from './honApiTypes';
import { defaultValueOf, parseHonNumber, stripBrackets } from './honParameterValues';
import type { ProgramOption, WasherProgram } from '../washer/washerTypes';

/** Friendly names and units for the options we know about. */
const KNOWN_OPTIONS: Record<string, { label: string; unit?: string }> = {
  temp: { label: 'Temperature', unit: '°C' },
  spinSpeed: { label: 'Spin speed', unit: 'rpm' },
  delayTime: { label: 'Delay start', unit: 'min' },
  dirtyLevel: { label: 'Soil level' },
  prewash: { label: 'Prewash' },
  extraRinse1: { label: 'Extra rinse' },
  extraRinse2: { label: 'Second extra rinse' },
  extraRinse3: { label: 'Third extra rinse' },
  anticrease: { label: 'Anti-crease' },
  nightWashStatus: { label: 'Night wash' },
  goodNight: { label: 'Good night (quiet)' },
  silentMode: { label: 'Silent mode' },
  steamWashStatus: { label: 'Steam' },
  hygiene: { label: 'Hygiene' },
  acquaplus: { label: 'Aqua plus' },
  creaseResistSoakStatus: { label: 'Crease-resist soak' },
  permanentPressStatus: { label: 'Keep fresh' },
  autoDetergentStatus: { label: 'Auto-dose detergent' },
  autoSoftenerStatus: { label: 'Auto-dose softener' },
  antiAllergyStatus: { label: 'Anti-allergy' },
  highWaterLevelStatus: { label: 'Extra water' },
  dryLevel: { label: 'Drying level' },
  dryTime: { label: 'Drying time', unit: 'min' },
  weight: { label: 'Load', unit: 'kg' },
};

/** Program variants pyhOn hides because they can't be started normally. */
const HIDDEN_PROGRAM_MARKERS = ['iot_recipe', 'iot_guided'];

/** "extraRinse1" → "Extra rinse 1", for options we have no name for. */
function labelFromKey(key: string): string {
  const words = key
    .replace(/Status$/, '')
    .replace(/([a-z])([A-Z0-9])/g, '$1 $2')
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * "PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL" → "Eco 40-60".
 * "PROGRAMS.WM_WD.IOT_WASH_MEN_S_TROUSERS" → "Men's trousers".
 */
export function programDisplayName(programId: string): string {
  const lastSegment = programId.split('.').pop() ?? programId;
  const words = lastSegment
    .replace(/^IOT_(WASH_)?/, '')
    .replace(/_NEW_ENERGY_LABEL$/, '')
    .replace(/(\d+)_(\d+)/g, '$1-$2')
    .replace(/_S_/g, "'S_")
    .replace(/_/g, ' ')
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** True for a parameter the user can actually change. */
function isAdjustable(definition: HonParameterDefinition): boolean {
  if (definition.typology === 'enum') return (definition.enumValues?.length ?? 0) > 1;
  if (definition.typology === 'range') {
    return parseHonNumber(definition.maximumValue) > parseHonNumber(definition.minimumValue);
  }
  return false;
}

/** Turns one h.on parameter into an option the UI can show. */
function toProgramOption(key: string, definition: HonParameterDefinition): ProgramOption {
  const known = KNOWN_OPTIONS[key];
  const label = known?.label ?? labelFromKey(key);
  const unit = known?.unit ?? null;
  const defaultValue = defaultValueOf(definition);

  if (definition.typology === 'enum') {
    const choices = (definition.enumValues ?? []).map((value) => stripBrackets(String(value)));
    const isOnOff = choices.length === 2 && choices.includes('0') && choices.includes('1');
    if (isOnOff) return { kind: 'toggle', key, label, defaultValue: defaultValue === '1' };
    return { kind: 'choice', key, label, choices, defaultValue, unit };
  }

  const minimum = parseHonNumber(definition.minimumValue);
  const maximum = parseHonNumber(definition.maximumValue);
  const step = parseHonNumber(definition.incrementValue) || 1;
  if (minimum === 0 && maximum === 1) {
    return { kind: 'toggle', key, label, defaultValue: defaultValue === '1' };
  }
  return { kind: 'range', key, label, minimum, maximum, step, defaultValue: Number(defaultValue), unit };
}

/** Toggles go last so the important choices (temperature, spin) come first. */
function optionSortOrder(option: ProgramOption): number {
  if (option.kind === 'toggle') return 2;
  return option.key === 'delayTime' ? 1 : 0;
}

function toWasherProgram(programId: string, definition: HonCommandDefinition): WasherProgram {
  const options = Object.entries(definition.parameters ?? {})
    .filter(([, parameter]) => isAdjustable(parameter))
    .map(([key, parameter]) => toProgramOption(key, parameter))
    .sort((first, second) => optionSortOrder(first) - optionSortOrder(second));

  const estimatedTime = definition.remainingTimes?.remainingTime;
  const estimatedMinutes = estimatedTime ? parseHonNumber(defaultValueOf(estimatedTime)) || null : null;

  const lastSegment = programId.split('.').pop() ?? '';
  return {
    id: programId,
    displayName: programDisplayName(programId),
    description: definition.description ?? '',
    estimatedMinutes,
    isBuiltIn: !lastSegment.toUpperCase().startsWith('IOT_'),
    options,
  };
}

/** True for programs the app should offer to start. */
function isStartableProgram(programId: string, definition: HonCommandDefinition): boolean {
  const lowerCaseId = programId.toLowerCase();
  if (HIDDEN_PROGRAM_MARKERS.some((marker) => lowerCaseId.includes(marker))) return false;
  // h.on marks some programs as not to be shown in the app.
  const remoteVisible = definition.ancillaryParameters?.remoteVisible;
  return !remoteVisible || defaultValueOf(remoteVisible) !== '0';
}

/**
 * Lists the programs a machine supports. Built-in programs come first in the
 * machine's own order, then the app-only extras alphabetically.
 */
export function parsePrograms(catalogue: HonCommandCatalogue): WasherProgram[] {
  const programs = Object.entries(catalogue.startProgram ?? {})
    .filter(([programId, definition]) => isStartableProgram(programId, definition))
    .map(([programId, definition]) => toWasherProgram(programId, definition));

  const builtIn = programs.filter((program) => program.isBuiltIn);
  const extras = programs
    .filter((program) => !program.isBuiltIn)
    .sort((first, second) => first.displayName.localeCompare(second.displayName));
  return [...builtIn, ...extras];
}

/**
 * The machine reports the running program as a number ("prCode"). This maps
 * those numbers back to readable program names.
 */
export function buildProgramNameByCode(catalogue: HonCommandCatalogue): Map<number, string> {
  const programNameByCode = new Map<number, string>();
  for (const [programId, definition] of Object.entries(catalogue.startProgram ?? {})) {
    const programCode = definition.parameters?.prCode;
    if (!programCode) continue;
    const code = parseHonNumber(defaultValueOf(programCode));
    // Several entries can share a code; keep the first (the built-in one).
    if (code > 0 && !programNameByCode.has(code)) {
      programNameByCode.set(code, programDisplayName(programId));
    }
  }
  return programNameByCode;
}
