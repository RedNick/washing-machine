/**
 * Checks a wash report sent by the app before it is stored, so the database
 * only ever holds small reports of the expected shape. Kept free of
 * Cloudflare types so the app's Jest run can test it too.
 */

/** Reports bigger than this are refused. A normal one is about 2 KB. */
export const MAX_REPORT_BYTES = 32 * 1024;

export interface StorableWashReport {
  appVersion: string;
  modelName: string;
  programName: string | null;
  finishedAt: string;
  /** The whole report, as sent, for later analysis. */
  reportJson: string;
}

export type CheckResult = { ok: true; report: StorableWashReport } | { ok: false; reason: string };

export function checkWashReport(bodyText: string): CheckResult {
  if (new TextEncoder().encode(bodyText).length > MAX_REPORT_BYTES) return { ok: false, reason: 'Report too large.' };

  let body: unknown;
  try {
    body = JSON.parse(bodyText);
  } catch {
    return { ok: false, reason: 'Report is not JSON.' };
  }
  if (body === null || typeof body !== 'object' || Array.isArray(body))
    return { ok: false, reason: 'Report is not an object.' };

  const report = body as Record<string, unknown>;
  if (report.reportFormat !== 1) return { ok: false, reason: 'Unknown report format.' };
  const app = report.app as Record<string, unknown> | undefined;
  const machine = report.machine as Record<string, unknown> | undefined;
  const wash = report.wash as Record<string, unknown> | undefined;
  if (
    typeof app?.version !== 'string' ||
    typeof machine?.modelName !== 'string' ||
    typeof wash?.finishedAt !== 'string'
  ) {
    return { ok: false, reason: 'Report is missing app.version, machine.modelName or wash.finishedAt.' };
  }
  if (Number.isNaN(Date.parse(wash.finishedAt))) return { ok: false, reason: 'wash.finishedAt is not a date.' };

  return {
    ok: true,
    report: {
      appVersion: app.version.slice(0, 100),
      modelName: machine.modelName.slice(0, 100),
      programName: typeof wash.programName === 'string' ? wash.programName.slice(0, 200) : null,
      finishedAt: wash.finishedAt,
      reportJson: bodyText,
    },
  };
}
