import type { HonParameterDefinition } from './honApiTypes';

/** h.on sometimes wraps defaults in brackets, e.g. "[dashboard]". */
export function stripBrackets(rawValue: string): string {
  return rawValue.replace(/^\[|\]$/g, '');
}

/** Parses h.on's numeric strings, which can use a comma as decimal point. */
export function parseHonNumber(rawValue: string | number | null | undefined): number {
  if (rawValue === null || rawValue === undefined || rawValue === '') return 0;
  if (typeof rawValue === 'number') return rawValue;
  const parsed = Number(rawValue.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : 0;
}

/**
 * The value h.on would use for a parameter if the user does not change it,
 * as the string the API expects.
 */
export function defaultValueOf(definition: HonParameterDefinition): string {
  switch (definition.typology) {
    case 'fixed':
      // A missing fixed value is sent as "0", matching pyhOn.
      return definition.fixedValue === null || definition.fixedValue === undefined
        ? '0'
        : String(definition.fixedValue);
    case 'range': {
      const fallback = definition.minimumValue ?? 0;
      // String() writes whole numbers without a decimal point ("40", not
      // "40.0"), which is what the machine expects.
      return String(parseHonNumber(definition.defaultValue ?? fallback));
    }
    case 'enum': {
      if (definition.defaultValue !== null && definition.defaultValue !== undefined) {
        return stripBrackets(String(definition.defaultValue));
      }
      const firstChoice = definition.enumValues?.[0];
      return firstChoice === undefined ? '0' : String(firstChoice);
    }
    default:
      return '';
  }
}
