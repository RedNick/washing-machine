/**
 * Shapes of the JSON the h.on API sends back. Only the fields this app uses
 * are listed; the real responses contain much more.
 */

/** One appliance from GET /commands/v1/appliance. */
export interface HonAppliance {
  macAddress: string;
  /** "WM" for a washing machine, "WD" for a washer-dryer, "TD" for a dryer… */
  applianceTypeName: string;
  applianceModelId: number | string;
  modelName?: string;
  brand?: string;
  nickName?: string;
  code?: string;
  serialNumber?: string;
  eepromId?: number | string;
  fwVersion?: string;
  series?: string;
}

/**
 * A single parameter definition inside a command. h.on describes every
 * setting this way: either fixed, a range of numbers, or a list of choices.
 */
export interface HonParameterDefinition {
  category?: string;
  typology?: 'fixed' | 'range' | 'enum' | string;
  mandatory?: number;
  fixedValue?: string | number | null;
  defaultValue?: string | number | null;
  minimumValue?: string | number;
  maximumValue?: string | number;
  incrementValue?: string | number;
  enumValues?: (string | number)[];
}

/** A command such as stopProgram, or one program of startProgram. */
export interface HonCommandDefinition {
  description?: string;
  protocolType?: string;
  parameters?: Record<string, HonParameterDefinition>;
  ancillaryParameters?: Record<string, HonParameterDefinition>;
  remainingTimes?: Record<string, HonParameterDefinition>;
}

/** Payload of GET /commands/v1/retrieve. */
export interface HonCommandCatalogue {
  applianceModel?: { options?: Record<string, string> };
  startProgram?: Record<string, HonCommandDefinition>;
  stopProgram?: HonCommandDefinition;
  pauseProgram?: HonCommandDefinition;
  resumeProgram?: HonCommandDefinition;
  [otherCommand: string]: unknown;
}

/** Payload of GET /commands/v1/context: the machine's live state. */
export interface HonContext {
  shadow?: {
    parameters?: Record<string, { parNewVal?: string; lastUpdate?: string }>;
  };
  activity?: Record<string, unknown>;
  lastConnEvent?: { category?: 'CONNECTED' | 'DISCONNECTED' | string };
}

/** Payload of GET /commands/v1/statistics. */
export interface HonStatistics {
  programsCounter?: number;
  temperatureUsage?: Record<string, number>;
}

/** One entry from GET /commands/v1/appliance/{mac}/history. */
export interface HonHistoryEntry {
  timestampAccepted?: string;
  timestampExecuted?: string;
  command?: { commandName?: string; programName?: string };
}
