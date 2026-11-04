import { DemoWasher } from '../../washer/demoWasher';
import type { MachineInfo, WasherService } from '../../washer/washerTypes';
import { buildDiagnosticReport } from '../buildDiagnosticReport';
import { REMOVED } from '../redactPrivateDetails';
import { reportFileName } from '../shareDiagnosticReport';

const machineInfo: MachineInfo = { displayName: "Nicolas's washer", modelName: 'HWD100', brand: 'Haier' };
const reportContext = { machineInfo, appVersion: 'h.off 1.0.0 · build 44', platform: 'android', osVersion: '36' };

describe('buildDiagnosticReport', () => {
  it('includes the model but not the name you gave the machine', async () => {
    const report = await buildDiagnosticReport({ ...reportContext, washer: new DemoWasher() });

    expect(report.machine).toEqual({ modelName: 'HWD100', brand: 'Haier' });
    expect(JSON.stringify(report)).not.toContain("Nicolas's washer");
    expect(report.app.version).toBe('h.off 1.0.0 · build 44');
    expect(Array.isArray(report.programs)).toBe(true);
    expect(report.rawDiagnostics).toBe('Not available for this machine.');
  });

  it('removes private details from the raw h.on data', async () => {
    const demoWasher = new DemoWasher();
    const washer = {
      isDemo: false,
      getStatus: () => demoWasher.getStatus(),
      getPrograms: async () => [],
      getDiagnostics: async () => [
        {
          title: 'Activity',
          json: '',
          data: { macAddress: 'ab-cd-ef-01-23-45', serialNumber: 'SN123', lastConnEvent: { category: 'CONNECTED' } },
        },
      ],
    } as unknown as WasherService;

    const report = await buildDiagnosticReport({ ...reportContext, washer });

    expect(report.rawDiagnostics).toEqual([
      {
        title: 'Activity',
        data: { macAddress: REMOVED, serialNumber: REMOVED, lastConnEvent: { category: 'CONNECTED' } },
      },
    ]);
  });

  it('still makes a report when h.on fails', async () => {
    const washer = {
      isDemo: false,
      getStatus: async () => {
        throw new Error('h.on is down');
      },
      getPrograms: async () => [],
    } as unknown as WasherService;

    const report = await buildDiagnosticReport({ ...reportContext, washer });

    expect(report.status).toEqual({ failed: 'h.on is down' });
  });
});

describe('reportFileName', () => {
  it('names the file after when it was made', () => {
    expect(reportFileName(new Date(2026, 10, 4, 9, 5))).toBe('h-off-report-2026-11-04-0905.json');
  });
});
