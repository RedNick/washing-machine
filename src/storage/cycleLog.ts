import AsyncStorage from '@react-native-async-storage/async-storage';

import type { MachineState, WasherStatus } from '../washer/washerTypes';

/**
 * h.on only keeps lifetime totals, so we keep our own list of finished
 * washes with what each one used. It lives only on this phone.
 */

export interface FinishedCycle {
  programName: string | null;
  finishedAt: string; // ISO date
  energyKwh: number | null;
  waterLitres: number | null;
}

const CYCLE_LOG_KEY = 'cycleLog.entries';
const LAST_SEEN_KEY = 'cycleLog.lastSeen';
const MAX_ENTRIES = 100;

/** What we saw at the previous check, so we can spot a wash ending. */
interface LastSeenCycle {
  state: MachineState;
  programName: string | null;
  energyKwh: number | null;
  waterLitres: number | null;
}

export async function loadFinishedCycles(): Promise<FinishedCycle[]> {
  try {
    const saved = await AsyncStorage.getItem(CYCLE_LOG_KEY);
    return saved ? (JSON.parse(saved) as FinishedCycle[]) : [];
  } catch {
    return [];
  }
}

/**
 * Call with every new status. When a wash has just ended, it is added to
 * the log using the last usage figures seen while it was running (some
 * machines reset them to zero at the end), and returned. Otherwise returns null.
 */
export async function recordCycleIfJustFinished(
  status: WasherStatus,
  now: Date = new Date(),
): Promise<FinishedCycle | null> {
  if (status.state === 'offline') return null;

  const savedLastSeen = await AsyncStorage.getItem(LAST_SEEN_KEY);
  const lastSeen: LastSeenCycle | null = savedLastSeen ? JSON.parse(savedLastSeen) : null;

  const wasRunning = lastSeen?.state === 'running' || lastSeen?.state === 'paused';
  let justFinishedCycle: FinishedCycle | null = null;
  if (wasRunning && status.state === 'finished') {
    const entry: FinishedCycle = {
      programName: status.programName ?? lastSeen.programName,
      finishedAt: now.toISOString(),
      energyKwh: status.currentCycleEnergyKwh || lastSeen.energyKwh,
      waterLitres: status.currentCycleWaterLitres || lastSeen.waterLitres,
    };
    const entries = await loadFinishedCycles();
    await AsyncStorage.setItem(CYCLE_LOG_KEY, JSON.stringify([entry, ...entries].slice(0, MAX_ENTRIES)));
    justFinishedCycle = entry;
  }

  const nextLastSeen: LastSeenCycle = {
    state: status.state,
    programName: status.programName,
    energyKwh: status.currentCycleEnergyKwh,
    waterLitres: status.currentCycleWaterLitres,
  };
  await AsyncStorage.setItem(LAST_SEEN_KEY, JSON.stringify(nextLastSeen));
  return justFinishedCycle;
}

/** Forgets every logged wash, e.g. when leaving the demo machine. */
export async function clearCycleLog(): Promise<void> {
  await AsyncStorage.multiRemove([CYCLE_LOG_KEY, LAST_SEEN_KEY]);
}
