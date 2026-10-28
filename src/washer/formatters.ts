import type { MachineState, WashPhase } from './washerTypes';

/** 83 → "1 h 23 min", 45 → "45 min", 0 → "0 min". */
export function formatDuration(totalMinutes: number): string {
  const roundedMinutes = Math.max(0, Math.round(totalMinutes));
  const hours = Math.floor(roundedMinutes / 60);
  const minutes = roundedMinutes % 60;
  if (hours === 0) return `${minutes} min`;
  if (minutes === 0) return `${hours} h`;
  return `${hours} h ${minutes} min`;
}

/** The clock time a wash will end, e.g. "14:05". */
export function formatFinishTime(remainingMinutes: number, now: Date = new Date()): string {
  const finish = new Date(now.getTime() + remainingMinutes * 60 * 1000);
  return finish.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

/**
 * The clock time a number of minutes from now: "14:30", or "tomorrow 07:00"
 * when it falls on the next day.
 */
export function formatClockTimeFromNow(minutesFromNow: number, now: Date = new Date()): string {
  const moment = new Date(now.getTime() + minutesFromNow * 60 * 1000);
  const time = moment.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const isSameDay = moment.toDateString() === now.toDateString();
  return isSameDay ? time : `tomorrow ${time}`;
}

/** "Today 14:05", "Yesterday 09:30" or "12 Oct 18:20". */
export function formatPastDate(date: Date, now: Date = new Date()): string {
  const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const dayInMs = 24 * 60 * 60 * 1000;
  if (date.getTime() >= startOfToday) return `Today ${time}`;
  if (date.getTime() >= startOfToday - dayInMs) return `Yesterday ${time}`;
  return `${date.toLocaleDateString([], { day: 'numeric', month: 'short' })} ${time}`;
}

const STATE_HEADLINES: Record<MachineState, string> = {
  offline: 'Machine offline',
  idle: 'Ready',
  scheduled: 'Delayed start',
  running: 'Washing',
  paused: 'Paused',
  finished: 'Finished',
  error: 'Needs attention',
};

/** The big headline on the status screen. */
export function describeState(state: MachineState): string {
  return STATE_HEADLINES[state];
}

const PHASE_LABELS: Record<WashPhase, string> = {
  ready: 'Ready',
  weighing: 'Weighing the load',
  heating: 'Heating water',
  washing: 'Washing',
  rinse: 'Rinsing',
  spin: 'Spinning',
  drying: 'Drying',
  steam: 'Steaming',
  tumbling: 'Tumbling',
  refresh: 'Refreshing',
  scheduled: 'Waiting to start',
  unknown: '',
};

export function describePhase(phase: WashPhase): string {
  return PHASE_LABELS[phase];
}

/** "40" + "°C" → "40 °C"; "0" for temperature means a cold wash. */
export function formatOptionValue(key: string, value: string | number, unit: string | null): string {
  if (key === 'temp' && Number(value) === 0) return 'Cold';
  if (key === 'spinSpeed' && Number(value) === 0) return 'No spin';
  if (key === 'delayTime') return Number(value) === 0 ? 'Off' : formatDuration(Number(value));
  if (key === 'extraRinses') return Number(value) === 0 ? 'None' : String(value);
  return unit ? `${value} ${unit}` : String(value);
}
