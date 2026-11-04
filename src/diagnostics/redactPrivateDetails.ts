/**
 * Removes anything that could identify you or your machine from data
 * before it leaves the phone in a diagnostic report: sign-in tokens, the
 * machine's MAC address and serial number, and account details.
 *
 * It works on field names (so new h.on fields with familiar names are caught
 * too) and on values that look like MAC addresses or email addresses.
 */

export const REMOVED = '[removed]';

/**
 * Field names, in lower case, whose values are always removed. A field
 * matches if its name contains any of these, so "macAddress" and
 * "applianceMacAddress" are both caught.
 */
const PRIVATE_FIELD_NAME_PARTS = [
  'token',
  'password',
  'secret',
  'cookie',
  'authorization',
  'macaddress',
  'serial',
  'email',
  'phone',
  'firstname',
  'lastname',
  'nickname',
  'address',
  'mobileid',
  'userid',
  'personid',
  'coords',
  'latitude',
  'longitude',
  'ssid',
];

/**
 * Field names removed only on an exact match, because as parts of a name
 * they'd catch useful fields too ("mac" is inside "machine" and "machMode").
 */
const PRIVATE_FIELD_NAMES = new Set(['mac', 'ip']);

/**
 * 12 hex digits, with or without - or : between the pairs. Uses look-arounds
 * rather than \b so a MAC followed by "_" (as in h.on transaction ids) still matches.
 */
const MAC_ADDRESS_PATTERN = /(?<![0-9a-f])[0-9a-f]{2}([-:]?)[0-9a-f]{2}(\1[0-9a-f]{2}){4}(?![0-9a-f])/gi;
const EMAIL_PATTERN = /[^\s@"]+@[^\s@"]+\.[^\s@"]+/g;

function isPrivateFieldName(fieldName: string): boolean {
  const lowerCaseName = fieldName.toLowerCase();
  if (PRIVATE_FIELD_NAMES.has(lowerCaseName)) return true;
  return PRIVATE_FIELD_NAME_PARTS.some((part) => lowerCaseName.includes(part));
}

function redactText(text: string): string {
  return text.replace(MAC_ADDRESS_PATTERN, REMOVED).replace(EMAIL_PATTERN, REMOVED);
}

/** A copy of the value with private details replaced by "[removed]". */
export function redactPrivateDetails<Value>(value: Value): Value {
  return redactValue(value) as Value;
}

function redactValue(value: unknown): unknown {
  if (typeof value === 'string') return redactText(value);
  if (Array.isArray(value)) return value.map(redactValue);
  if (value instanceof Date) return value.toISOString();
  if (value !== null && typeof value === 'object') {
    const redactedObject: Record<string, unknown> = {};
    for (const [fieldName, fieldValue] of Object.entries(value)) {
      redactedObject[fieldName] = isPrivateFieldName(fieldName) ? REMOVED : redactValue(fieldValue);
    }
    return redactedObject;
  }
  return value;
}
