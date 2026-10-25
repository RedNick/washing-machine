import type { HonContext } from './honApiTypes';
import type { MachineState, WashPhase, WasherStatus } from '../washer/washerTypes';

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

/** Reads a number from h.on's string values, or null if it isn't one. */
function toNumberOrNull(rawValue: string | undefined): number | null {
  if (rawValue === undefined || rawValue === '') return null;
  const parsed = Number(rawValue.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : null;
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
  const state: MachineState = isConnected
    ? (MACHINE_STATE_BY_MODE[machineMode] ?? 'idle')
    : 'offline';

  const phaseCode = toNumberOrNull(readValue('prPhase'));
  const phase: WashPhase =
    phaseCode === null ? 'unknown' : (WASH_PHASE_BY_CODE[phaseCode] ?? 'unknown');

  const programCode = toNumberOrNull(readValue('prCode'));
  const programName =
    programCode !== null && programCode > 0 ? (programNameByCode.get(programCode) ?? null) : null;

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
    doorLocked: readValue('doorLockStatus') === '1',
    remoteControlEnabled: readValue('remoteCtrValid') === '1',
    errorCode,
    fetchedAt,
  };
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
