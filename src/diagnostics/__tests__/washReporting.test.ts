import AsyncStorage from '@react-native-async-storage/async-storage';

import { checkWashReport } from '../../../wash-report-worker/src/checkWashReport';
import { saveDiagnosticsSettings } from '../../storage/diagnosticsSettings';
import type { MachineInfo, WasherService, WasherStatus } from '../../washer/washerTypes';
import { REMOVED } from '../redactPrivateDetails';

const machineInfo: MachineInfo = { displayName: "Nicolas's washer", modelName: 'HWD100', brand: 'Haier' };
const finishedCycle = {
  programName: 'Cottons',
  finishedAt: '2026-11-05T09:30:00.000Z',
  energyKwh: 0.9,
  waterLitres: 48,
};
const finalStatus = {
  state: 'finished',
  remainingMinutes: 1,
  fetchedAt: new Date('2026-11-05T09:30:00Z'),
  rawReadings: [{ name: 'macAddress', value: 'ab-cd-ef-01-23-45', lastUpdated: null }],
} as unknown as WasherStatus;

function fakeWasher(isDemo = false): WasherService {
  return { isDemo, getMachineInfo: async () => machineInfo } as unknown as WasherService;
}

/** Loads washReporting.ts fresh, with or without a report address built in. */
function loadWashReporting(reportUrl: string | undefined) {
  if (reportUrl) process.env.EXPO_PUBLIC_WASH_REPORT_URL = reportUrl;
  else delete process.env.EXPO_PUBLIC_WASH_REPORT_URL;
  let washReporting!: typeof import('../washReporting');
  jest.isolateModules(() => {
    washReporting = require('../washReporting');
  });
  return washReporting;
}

describe('wash reports', () => {
  const fetchMock = jest.fn(async () => ({ ok: true, status: 204 }));
  beforeEach(async () => {
    fetchMock.mockClear();
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    await AsyncStorage.clear();
  });
  afterAll(() => delete process.env.EXPO_PUBLIC_WASH_REPORT_URL);

  it('leave out the machine name and private details, and pass the worker’s checks', () => {
    const report = loadWashReporting(undefined).buildWashReport(finishedCycle, finalStatus, machineInfo);

    const reportText = JSON.stringify(report);
    expect(reportText).not.toContain("Nicolas's washer");
    expect(reportText).not.toContain('ab-cd-ef-01-23-45');
    expect(reportText).toContain(REMOVED);
    expect(checkWashReport(reportText)).toMatchObject({
      ok: true,
      report: { modelName: 'HWD100', programName: 'Cottons' },
    });
  });

  it("aren't sent until you switch them on", async () => {
    await loadWashReporting('https://reports.example').sendWashReportIfAllowed(
      fakeWasher(),
      finishedCycle,
      finalStatus,
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('are sent to the worker once switched on', async () => {
    await saveDiagnosticsSettings({ sendCrashReports: false, sendWashReports: true });
    await loadWashReporting('https://reports.example').sendWashReportIfAllowed(
      fakeWasher(),
      finishedCycle,
      finalStatus,
    );
    expect(fetchMock).toHaveBeenCalledWith(
      'https://reports.example/reports',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it("aren't sent for the demo machine, or by a build without a report address", async () => {
    await saveDiagnosticsSettings({ sendCrashReports: false, sendWashReports: true });
    await loadWashReporting('https://reports.example').sendWashReportIfAllowed(
      fakeWasher(true),
      finishedCycle,
      finalStatus,
    );
    await loadWashReporting(undefined).sendWashReportIfAllowed(fakeWasher(), finishedCycle, finalStatus);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("don't throw when the network fails", async () => {
    await saveDiagnosticsSettings({ sendCrashReports: false, sendWashReports: true });
    fetchMock.mockRejectedValueOnce(new Error('offline'));
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    await expect(
      loadWashReporting('https://reports.example').sendWashReportIfAllowed(fakeWasher(), finishedCycle, finalStatus),
    ).resolves.toBeUndefined();
    warn.mockRestore();
  });
});
