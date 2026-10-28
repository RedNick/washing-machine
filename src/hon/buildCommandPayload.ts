import type { HonAppliance, HonCommandDefinition, HonParameterDefinition } from './honApiTypes';
import { HON_APP_VERSION, HON_DEVICE_MODEL, HON_OS_NAME, HON_OS_VERSION } from './honConstants';
import { defaultValueOf } from './honParameterValues';
import { DELAY_OPTION_KEY, EXTRA_RINSES_OPTION_KEY, type ChosenOptions } from '../washer/washerTypes';
import { EXTRA_RINSE_SWITCH_KEYS } from './parseProgramCatalogue';

/** The body of POST /commands/v1/send, as the h.on app sends it. */
export interface HonSendCommandBody {
  macAddress: string;
  timestamp: string;
  commandName: string;
  transactionId: string;
  applianceOptions: Record<string, string>;
  device: {
    appVersion: string;
    mobileId: string;
    mobileOs: string;
    osVersion: number;
    deviceModel: string;
  };
  attributes: { channel: string; origin: string; energyLabel: string };
  ancillaryParameters: Record<string, string>;
  parameters: Record<string, string>;
  applianceType: string;
  programName?: string;
}

/** Converts a user's choice into the string the machine expects. */
function chosenValueToString(value: string | number | boolean): string {
  if (typeof value === 'boolean') return value ? '1' : '0';
  return String(value);
}

/**
 * Turns the app's combined options back into h.on's own parameters:
 * "Extra rinses: 2" switches on extraRinse1 and extraRinse2, and a delay
 * start also switches on delayStatus (ignored by machines without it).
 */
export function expandCombinedOptions(chosenOptions: ChosenOptions): ChosenOptions {
  const { [EXTRA_RINSES_OPTION_KEY]: extraRinseCount, ...expanded } = chosenOptions;
  if (extraRinseCount !== undefined) {
    EXTRA_RINSE_SWITCH_KEYS.forEach((switchKey, index) => {
      expanded[switchKey] = index < Number(extraRinseCount);
    });
  }
  const delayMinutes = Number(chosenOptions[DELAY_OPTION_KEY] ?? 0);
  if (delayMinutes > 0) expanded.delayStatus = true;
  return expanded;
}

/** Fills in every parameter, using the user's choice where there is one. */
function resolveParameterValues(
  definitions: Record<string, HonParameterDefinition>,
  chosenOptions: ChosenOptions,
): Record<string, string> {
  const resolvedValues: Record<string, string> = {};
  for (const [key, definition] of Object.entries(definitions)) {
    const chosenValue = chosenOptions[key];
    resolvedValues[key] =
      chosenValue === undefined ? defaultValueOf(definition) : chosenValueToString(chosenValue);
  }
  return resolvedValues;
}

export interface BuildCommandInput {
  appliance: HonAppliance;
  /** The "options" map from the catalogue's applianceModel. */
  applianceOptions: Record<string, string>;
  commandName: 'startProgram' | 'stopProgram' | 'pauseProgram' | 'resumeProgram';
  command: HonCommandDefinition;
  /** Only for startProgram: the program id, e.g. "PROGRAMS.WM_WD.COTTONS". */
  programId?: string;
  chosenOptions?: ChosenOptions;
  /** Stable random id for this phone, so h.on can tell our requests apart. */
  mobileId: string;
  now?: Date;
}

/**
 * Builds the JSON for a command, matching what pyhOn (and the official app)
 * send. Every parameter of the command is included; the user's choices
 * replace the defaults.
 */
export function buildCommandPayload(input: BuildCommandInput): HonSendCommandBody {
  const timestamp = (input.now ?? new Date()).toISOString();
  const chosenOptions = expandCombinedOptions(input.chosenOptions ?? {});

  const parameters = resolveParameterValues(input.command.parameters ?? {}, chosenOptions);
  // "programRules" describes how options affect each other; it isn't sent.
  const { programRules: _programRules, ...ancillaryDefinitions } =
    input.command.ancillaryParameters ?? {};
  const ancillaryParameters = resolveParameterValues(ancillaryDefinitions, {});

  const body: HonSendCommandBody = {
    macAddress: input.appliance.macAddress,
    timestamp,
    commandName: input.commandName,
    transactionId: `${input.appliance.macAddress}_${timestamp}`,
    applianceOptions: input.applianceOptions,
    device: {
      appVersion: HON_APP_VERSION,
      mobileId: input.mobileId,
      mobileOs: HON_OS_NAME,
      osVersion: HON_OS_VERSION,
      deviceModel: HON_DEVICE_MODEL,
    },
    attributes: { channel: 'mobileApp', origin: 'standardProgram', energyLabel: '0' },
    ancillaryParameters,
    parameters,
    applianceType: input.appliance.applianceTypeName,
  };

  if (input.commandName === 'startProgram' && input.programId) {
    const programName = input.programId.toUpperCase();
    body.programName = programName;
    // Some machines also want the program name inside the parameters.
    if ('prStr' in parameters) parameters.prStr = programName;
  }
  return body;
}
