import type { MachineState, WasherStatus } from '../washer/washerTypes';

/**
 * Decides what to do about the "wash finished" alert each time we get a new
 * status. Kept free of any phone APIs so it can be tested on its own.
 *
 * How the alert works: while a wash runs, we schedule a phone notification
 * for the moment the machine says it will finish, and move it whenever the
 * time left changes. If we then see the wash has ended before that alert went
 * off (for example it finished early), we show the alert straight away.
 */

/** What we remember between checks. Saved on the phone. */
export interface FinishAlertMemory {
  scheduledNotificationId: string | null;
  /** When the scheduled alert will go off. */
  scheduledForMs: number | null;
  /** Machine state at the previous check. */
  previousState: MachineState | null;
  /**
   * Last program name we saw. The background check skips the program list
   * to save data, so it relies on this to name the program in the alert.
   */
  lastKnownProgramName: string | null;
}

export const EMPTY_FINISH_ALERT_MEMORY: FinishAlertMemory = {
  scheduledNotificationId: null,
  scheduledForMs: null,
  previousState: null,
  lastKnownProgramName: null,
};

export type FinishAlertStep =
  | { kind: 'cancelScheduled'; notificationId: string }
  | { kind: 'schedule'; fireAtMs: number; programName: string | null }
  | { kind: 'showNow'; reason: 'finished' | 'error'; programName: string | null; errorCode: string | null };

/** Don't move the alert for small wobbles in the machine's estimate. */
const RESCHEDULE_THRESHOLD_MS = 2 * 60 * 1000;

const MID_CYCLE_STATES: MachineState[] = ['running', 'paused', 'scheduled'];

export function planFinishAlert(
  memory: FinishAlertMemory,
  status: WasherStatus,
  nowMs: number,
): { steps: FinishAlertStep[]; nextMemory: FinishAlertMemory } {
  const steps: FinishAlertStep[] = [];
  let scheduledNotificationId = memory.scheduledNotificationId;
  let scheduledForMs = memory.scheduledForMs;

  const cancelScheduled = () => {
    if (scheduledNotificationId) {
      steps.push({ kind: 'cancelScheduled', notificationId: scheduledNotificationId });
    }
    scheduledNotificationId = null;
    scheduledForMs = null;
  };

  const programName = status.programName ?? memory.lastKnownProgramName;
  const wasMidCycle = memory.previousState !== null && MID_CYCLE_STATES.includes(memory.previousState);
  const scheduledAlertAlreadyFired = scheduledForMs !== null && scheduledForMs <= nowMs;

  switch (status.state) {
    case 'running': {
      const expectedFinishMs = nowMs + status.remainingMinutes * 60 * 1000;
      const needsNewAlert =
        scheduledForMs === null || Math.abs(expectedFinishMs - scheduledForMs) > RESCHEDULE_THRESHOLD_MS;
      if (needsNewAlert && status.remainingMinutes > 0) {
        cancelScheduled();
        steps.push({ kind: 'schedule', fireAtMs: expectedFinishMs, programName });
        // The real id is filled in by whoever carries out the step.
        scheduledForMs = expectedFinishMs;
      }
      break;
    }

    case 'paused':
    case 'scheduled':
      // The finish time is unknown until it runs again.
      cancelScheduled();
      break;

    case 'finished': {
      const alertStillPending = scheduledForMs !== null && !scheduledAlertAlreadyFired;
      const missedTheEnd = wasMidCycle && (scheduledForMs === null || alertStillPending);
      cancelScheduled();
      if (missedTheEnd) {
        steps.push({ kind: 'showNow', reason: 'finished', programName, errorCode: null });
      }
      break;
    }

    case 'error':
      cancelScheduled();
      if (memory.previousState !== 'error') {
        steps.push({ kind: 'showNow', reason: 'error', programName, errorCode: status.errorCode });
      }
      break;

    case 'idle':
      // Someone stopped the wash, or it was never started.
      cancelScheduled();
      break;

    case 'offline':
      // A lost connection doesn't mean the wash stopped, so keep the alert.
      break;
  }

  return {
    steps,
    nextMemory: {
      scheduledNotificationId,
      scheduledForMs,
      // Remember the last real state, so going offline briefly doesn't hide a finish.
      previousState: status.state === 'offline' ? memory.previousState : status.state,
      lastKnownProgramName: programName,
    },
  };
}
