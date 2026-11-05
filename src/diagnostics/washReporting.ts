import { Platform } from 'react-native';

import type { FinishedCycle } from '../storage/cycleLog';
import { loadDiagnosticsSettings } from '../storage/diagnosticsSettings';
import { describeAppVersion } from '../washer/appVersion';
import type { MachineInfo, WasherService, WasherStatus } from '../washer/washerTypes';
import { redactPrivateDetails } from './redactPrivateDetails';
import { isWashReportingSetUp } from './reportingServices';

/**
 * Wash reports: a short summary sent at the end of each wash, only when
 * "Wash reports" is switched on in Settings. They go to the h.off wash
 * report worker (wash-report-worker/ in this repo) and are mainly for
 * learning how different machines signal the end of a program.
 */

/** Give up on sending after this long, so a slow network can't hold up a status check. */
const SEND_TIMEOUT_MS = 15_000;

/** What one wash report contains. Checked by wash-report-worker/src/checkWashReport.ts. */
export interface WashReport {
  reportFormat: 1;
  app: { version: string; platform: string; osVersion: string };
  machine: { modelName: string; brand: string };
  wash: {
    programName: string | null;
    finishedAt: string;
    energyKwh: number | null;
    waterLitres: number | null;
  };
  /** The status that showed the wash had finished, raw readings included. */
  finalStatus: unknown;
}

export function buildWashReport(
  finishedCycle: FinishedCycle,
  finalStatus: WasherStatus,
  machineInfo: MachineInfo,
): WashReport {
  return redactPrivateDetails<WashReport>({
    reportFormat: 1,
    app: { version: describeAppVersion(), platform: Platform.OS, osVersion: String(Platform.Version) },
    // Model and brand only, never the name you gave the machine.
    machine: { modelName: machineInfo.modelName, brand: machineInfo.brand },
    wash: {
      programName: finishedCycle.programName,
      finishedAt: finishedCycle.finishedAt,
      energyKwh: finishedCycle.energyKwh,
      waterLitres: finishedCycle.waterLitres,
    },
    finalStatus,
  });
}

/**
 * Sends a report for a wash that has just finished, if you've switched wash
 * reports on. Never throws: a report that can't be sent is simply dropped.
 */
export async function sendWashReportIfAllowed(
  washer: WasherService,
  finishedCycle: FinishedCycle,
  finalStatus: WasherStatus,
): Promise<void> {
  if (!isWashReportingSetUp || washer.isDemo) return;
  try {
    const settings = await loadDiagnosticsSettings();
    if (!settings.sendWashReports) return;

    const report = buildWashReport(finishedCycle, finalStatus, await washer.getMachineInfo());
    // AbortSignal.timeout isn't available in every React Native version, so time out by hand.
    const abortController = new AbortController();
    const timeout = setTimeout(() => abortController.abort(), SEND_TIMEOUT_MS);
    try {
      const response = await fetch(`${process.env.EXPO_PUBLIC_WASH_REPORT_URL}/reports`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(report),
        signal: abortController.signal,
      });
      if (!response.ok) console.warn(`Wash report refused: ${response.status}`);
    } finally {
      clearTimeout(timeout);
    }
  } catch (error) {
    console.warn('Could not send wash report', error);
  }
}
