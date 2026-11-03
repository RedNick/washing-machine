import { parseLifetimeTotals, parseWasherStatus } from '../parseWasherStatus';
import { contextWith, runningContext } from './honFixtures';

const programNames = new Map([[201, 'Cottons']]);

describe('parseWasherStatus', () => {
  it('reads a running wash', () => {
    const status = parseWasherStatus(runningContext, programNames);
    expect(status).toMatchObject({
      state: 'running',
      phase: 'rinse',
      programName: 'Cottons',
      remainingMinutes: 83,
      temperatureCelsius: 40,
      currentCycleEnergyKwh: 0.42,
      currentCycleWaterLitres: 31,
      doorLocked: true,
      remoteControlEnabled: true,
      errorCode: null,
    });
  });

  it('ignores the leftover time once a program has finished', () => {
    const status = parseWasherStatus(contextWith({ machMode: '7', remainingTimeMM: '83' }), programNames);
    expect(status.state).toBe('finished');
    expect(status.remainingMinutes).toBe(0);
  });

  it('reports offline when the machine is disconnected, whatever its last mode', () => {
    const status = parseWasherStatus(contextWith({ machMode: '2' }, 'DISCONNECTED'), programNames);
    expect(status.state).toBe('offline');
  });

  it('reads delayed starts and error codes', () => {
    const scheduled = parseWasherStatus(contextWith({ machMode: '5', delayTime: '120' }), programNames);
    expect(scheduled.state).toBe('scheduled');
    expect(scheduled.delayMinutes).toBe(120);

    const broken = parseWasherStatus(contextWith({ machMode: '6', errors: 'E21' }), programNames);
    expect(broken.state).toBe('error');
    expect(broken.errorCode).toBe('E21');
  });

  it('lists every raw reading, sorted by name, with when it last changed', () => {
    const context = contextWith({ prPhase: '7', machMode: '2' });
    context.shadow!.parameters!.machMode.lastUpdate = '2026-11-03T15:58:00Z';
    const status = parseWasherStatus(context, programNames);
    expect(status.rawReadings).toEqual([
      { name: 'machMode', value: '2', lastUpdated: new Date('2026-11-03T15:58:00Z') },
      { name: 'prPhase', value: '7', lastUpdated: null },
    ]);
  });

  it('copes with an empty response', () => {
    const status = parseWasherStatus({}, programNames);
    expect(status.state).toBe('idle');
    expect(status.phase).toBe('unknown');
    expect(status.programName).toBeNull();
    expect(status.remoteControlEnabled).toBe(false);
  });
});

describe('parseLifetimeTotals', () => {
  it('reads energy, water and wash count', () => {
    expect(parseLifetimeTotals(runningContext)).toEqual({
      totalEnergyKwh: 33.34,
      totalWaterLitres: 2571,
      totalCycles: 45,
    });
  });
});
