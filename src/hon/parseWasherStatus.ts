import type { HonContext } from './honApiTypes';
import type { MachineState, RawReading, WashPhase, WasherStatus } from '../washer/washerTypes';
import { programDisplayName } from './parseProgramCatalogue';

/**
 * h.on reports the machine's mode as a number ("machMode"). Meanings come
 * from the Home Assistant hon integration.
 */
const MACHINE_STATE_BY_MODE: Record<number, MachineState> = {
  0: 'idle', // no state
  1: 'idle', // choosing a program on the machine
  2: 'running',
  3: 'paused',
  4: 'scheduled', // delayed start being set up
  5: 'scheduled', // delayed start counting down
  6: 'error',
  7: 'finished',
  8: 'idle', // factory test mode
  9: 'finished', // stopping
};

/** h.on's program phase numbers ("prPhase") for washing machines. */
const WASH_PHASE_BY_CODE: Record<number, WashPhase> = {
  0: 'ready',
  1: 'washing',
  2: 'washing',
  3: 'spin',
  4: 'rinse',
  5: 'rinse',
  6: 'rinse',
  7: 'drying',
  8: 'drying',
  9: 'steam',
  10: 'ready',
  11: 'spin',
  12: 'weighing',
  13: 'weighing',
  14: 'washing',
  15: 'washing',
  16: 'washing',
  17: 'rinse',
  18: 'rinse',
  19: 'scheduled',
  20: 'tumbling',
  24: 'refresh',
  25: 'washing',
  26: 'heating',
  27: 'washing',
};

/**
 * Some machines (Nicolas's washer dryer) never report the end of a program:
 * h.on keeps saying "running, 1 minute left" long after the wash is done. If
 * the time left has sat at 1 minute or less for this long, treat the program
 * as finished.
 */
export const STUCK_AT_END_MINUTES = 10;

/** Reads a number from h.on's string values, or null if it isn't one. */
function toNumberOrNull(rawValue: string | undefined): number | null {
  if (rawValue === undefined || rawValue === '') return null;
  const parsed = Number(rawValue.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * The name of the program that was started, from h.on's "activity" record.
 *
 * Several programs share one machine program code (prCode 4 is both
 * "Synthetic and coloured" and "Colored"), so the code alone can give the
 * wrong name. The activity names the exact program, but it lingers after a
 * cycle, so it's only trusted when its code matches the one running now.
 */
function programNameFromActivity(context: HonContext, runningProgramCode: number | null): string | null {
  const attributes = context.activity?.attributes;
  if (!attributes || typeof attributes !== 'object') return null;
  const { programName, prCode } = attributes as Record<string, unknown>;
  if (typeof programName !== 'string' || programName.trim() === '') return null;
  if (runningProgramCode === null || toNumberOrNull(String(prCode ?? '')) !== runningProgramCode) return null;
  return programDisplayName(programName);
}

/**
 * Turns the raw h.on "context" response into the app's WasherStatus.
 *
 * @param programNameByCode maps the machine's numeric program code to a
 *   readable name, built from the program catalogue.
 */
export function parseWasherStatus(
  context: HonContext,
  programNameByCode: Map<number, string>,
  fetchedAt: Date = new Date(),
): WasherStatus {
  const rawParameters = context.shadow?.parameters ?? {};
  const readValue = (name: string): string | undefined => rawParameters[name]?.parNewVal;

  const isConnected = context.lastConnEvent?.category !== 'DISCONNECTED';
  const machineMode = toNumberOrNull(readValue('machMode')) ?? 0;
  const reportedState: MachineState = isConnected ? (MACHINE_STATE_BY_MODE[machineMode] ?? 'idle') : 'offline';
  const state: MachineState = hasStalledAtTheEnd(reportedState, rawParameters.remainingTimeMM, fetchedAt)
    ? 'finished'
    : reportedState;

  const phaseCode = toNumberOrNull(readValue('prPhase'));
  const phase: WashPhase = phaseCode === null ? 'unknown' : (WASH_PHASE_BY_CODE[phaseCode] ?? 'unknown');

  const programCode = toNumberOrNull(readValue('prCode'));
  const programName =
    programNameFromActivity(context, programCode) ??
    (programCode !== null && programCode > 0 ? (programNameByCode.get(programCode) ?? null) : null);

  // The machine keeps the last remaining time after a program ends, so only
  // trust it while something is actually running or paused.
  const isMidCycle = state === 'running' || state === 'paused' || state === 'scheduled';
  const remainingMinutes = isMidCycle ? (toNumberOrNull(readValue('remainingTimeMM')) ?? 0) : 0;
  const delayMinutes = state === 'scheduled' ? (toNumberOrNull(readValue('delayTime')) ?? 0) : 0;

  // "00" (or "0") means no error.
  const rawErrorCode = readValue('errors') ?? '';
  const errorCode = /^0*$/.test(rawErrorCode) ? null : rawErrorCode;

  return {
    state,
    phase,
    programName,
    remainingMinutes,
    delayMinutes,
    temperatureCelsius: toNumberOrNull(readValue('temp')),
    spinSpeedRpm: toNumberOrNull(readValue('spinSpeed')),
    currentCycleEnergyKwh: toNumberOrNull(readValue('currentElectricityUsed')),
    currentCycleWaterLitres: toNumberOrNull(readValue('currentWaterUsed')),
    doorLocked: readDoorLocked(readValue),
    remoteControlEnabled: readValue('remoteCtrValid') === '1',
    errorCode,
    fetchedAt,
    rawReadings: listRawReadings(rawParameters),
  };
}

/** True when a "running" program's last minute has lasted far too long. */
function hasStalledAtTheEnd(
  reportedState: MachineState,
  remainingTime: { parNewVal?: string; lastUpdate?: string } | undefined,
  fetchedAt: Date,
): boolean {
  if (reportedState !== 'running' || !remainingTime?.lastUpdate) return false;
  const minutesLeft = toNumberOrNull(remainingTime.parNewVal);
  const lastChangedMs = new Date(remainingTime.lastUpdate).getTime();
  if (minutesLeft === null || minutesLeft > 1 || Number.isNaN(lastChangedMs)) return false;
  return fetchedAt.getTime() - lastChangedMs >= STUCK_AT_END_MINUTES * 60 * 1000;
}

/**
 * "doorLockStatus" 1 means locked (as in the Home Assistant hon
 * integration). Machines that don't send it get null rather than a guess.
 */
function readDoorLocked(readValue: (name: string) => string | undefined): boolean | null {
  const doorLockStatus = readValue('doorLockStatus');
  return doorLockStatus === undefined ? null : doorLockStatus === '1';
}

/** All of h.on's parameters as name/value pairs, sorted by name. */
function listRawReadings(rawParameters: Record<string, { parNewVal?: string; lastUpdate?: string }>): RawReading[] {
  return Object.entries(rawParameters)
    .map(([name, parameter]) => {
      const lastUpdated = parameter.lastUpdate ? new Date(parameter.lastUpdate) : null;
      return {
        name,
        value: parameter.parNewVal ?? '',
        lastUpdated: lastUpdated && !Number.isNaN(lastUpdated.getTime()) ? lastUpdated : null,
      };
    })
    .sort((first, second) => first.name.localeCompare(second.name));
}

/** Lifetime totals the machine keeps in its live state. */
export function parseLifetimeTotals(context: HonContext): {
  totalEnergyKwh: number | null;
  totalWaterLitres: number | null;
  totalCycles: number | null;
} {
  const rawParameters = context.shadow?.parameters ?? {};
  return {
    totalEnergyKwh: toNumberOrNull(rawParameters.totalElectricityUsed?.parNewVal),
    totalWaterLitres: toNumberOrNull(rawParameters.totalWaterUsed?.parNewVal),
    totalCycles: toNumberOrNull(rawParameters.totalWashCycle?.parNewVal),
  };
}
