import { EMPTY_FINISH_ALERT_MEMORY, planFinishAlert, type FinishAlertMemory } from '../planFinishAlert';
import type { WasherStatus } from '../../washer/washerTypes';

const MINUTE = 60 * 1000;
const NOW = Date.UTC(2026, 9, 25, 10, 0, 0);

function statusWith(overrides: Partial<WasherStatus>): WasherStatus {
  return {
    state: 'idle',
    phase: 'ready',
    programName: 'Cottons',
    remainingMinutes: 0,
    delayMinutes: 0,
    temperatureCelsius: null,
    spinSpeedRpm: null,
    currentCycleEnergyKwh: null,
    currentCycleWaterLitres: null,
    doorLocked: false,
    remoteControlEnabled: true,
    errorCode: null,
    fetchedAt: new Date(NOW),
    ...overrides,
  };
}

/** Memory after an alert was scheduled to go off at `fireAtMs`. */
function scheduledFor(fireAtMs: number): FinishAlertMemory {
  return {
    scheduledNotificationId: 'alert-1',
    scheduledForMs: fireAtMs,
    previousState: 'running',
    lastKnownProgramName: 'Cottons',
  };
}

describe('planFinishAlert', () => {
  it('schedules an alert for when a running wash will end', () => {
    const { steps, nextMemory } = planFinishAlert(
      EMPTY_FINISH_ALERT_MEMORY,
      statusWith({ state: 'running', remainingMinutes: 45 }),
      NOW,
    );
    expect(steps).toEqual([{ kind: 'schedule', fireAtMs: NOW + 45 * MINUTE, programName: 'Cottons' }]);
    expect(nextMemory.scheduledForMs).toBe(NOW + 45 * MINUTE);
    expect(nextMemory.previousState).toBe('running');
  });

  it('leaves the alert alone for small changes in the estimate', () => {
    const { steps } = planFinishAlert(
      scheduledFor(NOW + 45 * MINUTE),
      statusWith({ state: 'running', remainingMinutes: 46 }),
      NOW,
    );
    expect(steps).toEqual([]);
  });

  it('moves the alert when the estimate changes a lot', () => {
    const { steps } = planFinishAlert(
      scheduledFor(NOW + 45 * MINUTE),
      statusWith({ state: 'running', remainingMinutes: 60 }),
      NOW,
    );
    expect(steps).toEqual([
      { kind: 'cancelScheduled', notificationId: 'alert-1' },
      { kind: 'schedule', fireAtMs: NOW + 60 * MINUTE, programName: 'Cottons' },
    ]);
  });

  it('cancels the alert while paused', () => {
    const { steps, nextMemory } = planFinishAlert(scheduledFor(NOW + 45 * MINUTE), statusWith({ state: 'paused' }), NOW);
    expect(steps).toEqual([{ kind: 'cancelScheduled', notificationId: 'alert-1' }]);
    expect(nextMemory.scheduledForMs).toBeNull();
  });

  it('alerts straight away when the wash finished before the scheduled alert', () => {
    const { steps } = planFinishAlert(scheduledFor(NOW + 10 * MINUTE), statusWith({ state: 'finished' }), NOW);
    expect(steps).toEqual([
      { kind: 'cancelScheduled', notificationId: 'alert-1' },
      { kind: 'showNow', reason: 'finished', programName: 'Cottons', errorCode: null },
    ]);
  });

  it('does not alert twice when the scheduled alert already went off', () => {
    const { steps } = planFinishAlert(scheduledFor(NOW - 2 * MINUTE), statusWith({ state: 'finished' }), NOW);
    expect(steps.some((step) => step.kind === 'showNow')).toBe(false);
  });

  it('does not alert for a wash that finished before the app was watching', () => {
    const { steps } = planFinishAlert(EMPTY_FINISH_ALERT_MEMORY, statusWith({ state: 'finished' }), NOW);
    expect(steps).toEqual([]);
  });

  it('keeps the alert and the last state when the machine drops offline', () => {
    const memory = scheduledFor(NOW + 30 * MINUTE);
    const { steps, nextMemory } = planFinishAlert(memory, statusWith({ state: 'offline' }), NOW);
    expect(steps).toEqual([]);
    expect(nextMemory).toEqual(memory);
  });

  it('uses the remembered program name when the status has none', () => {
    const { steps } = planFinishAlert(
      scheduledFor(NOW + 10 * MINUTE),
      statusWith({ state: 'finished', programName: null }),
      NOW,
    );
    expect(steps).toContainEqual({ kind: 'showNow', reason: 'finished', programName: 'Cottons', errorCode: null });
  });

  it('warns once about an error', () => {
    const first = planFinishAlert(
      scheduledFor(NOW + 10 * MINUTE),
      statusWith({ state: 'error', errorCode: 'E21' }),
      NOW,
    );
    expect(first.steps).toContainEqual({ kind: 'showNow', reason: 'error', programName: 'Cottons', errorCode: 'E21' });

    const second = planFinishAlert(first.nextMemory, statusWith({ state: 'error', errorCode: 'E21' }), NOW + MINUTE);
    expect(second.steps).toEqual([]);
  });
});
