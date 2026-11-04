import { loadFinishedCycles } from '../storage/cycleLog';
import type { MachineInfo, WasherService } from '../washer/washerTypes';
import { redactPrivateDetails } from './redactPrivateDetails';

/**
 * Everything in a "Send a report" file. It's built on the phone, shown to
 * you in the share sheet, and only goes where you choose to send it.
 *
 * Each part is fetched separately, so if h.on is failing the report still
 * goes out with the error in place of that part (that's often when a report
 * is most useful).
 */
export interface DiagnosticReport {
  reportFormat: 1;
  createdAt: string;
  app: { version: string; platform: string; osVersion: string };
  /** Model and brand only. The name you gave the machine is left out. */
  machine: { modelName: string; brand: string };
  status: unknown;
  rawDiagnostics: unknown;
  programs: unknown;
  recentFinishedWashes: unknown;
}

export interface ReportContext {
  washer: WasherService;
  machineInfo: MachineInfo;
  /** e.g. "h.off 1.0.0 · build 44". */
  appVersion: string;
  platform: string;
  osVersion: string;
  now?: Date;
}

const FINISHED_WASHES_TO_INCLUDE = 10;

export async function buildDiagnosticReport(context: ReportContext): Promise<DiagnosticReport> {
  const { washer } = context;
  const [status, rawDiagnostics, programs, recentFinishedWashes] = await Promise.all([
    describeFailures(() => washer.getStatus()),
    describeFailures(async () => {
      if (!washer.getDiagnostics) return 'Not available for this machine.';
      const blocks = await washer.getDiagnostics();
      return blocks.map((block) => ({ title: block.title, data: block.data }));
    }),
    describeFailures(async () => summarisePrograms(await washer.getPrograms())),
    describeFailures(async () => (await loadFinishedCycles()).slice(0, FINISHED_WASHES_TO_INCLUDE)),
  ]);

  const report: DiagnosticReport = {
    reportFormat: 1,
    createdAt: (context.now ?? new Date()).toISOString(),
    app: { version: context.appVersion, platform: context.platform, osVersion: context.osVersion },
    machine: { modelName: context.machineInfo.modelName, brand: context.machineInfo.brand },
    status,
    rawDiagnostics,
    programs,
    recentFinishedWashes,
  };
  return redactPrivateDetails(report);
}

/**
 * Program names, lengths and the options each one offers. Enough to see
 * how a model's catalogue was understood, without the whole h.on download.
 */
function summarisePrograms(programs: Awaited<ReturnType<WasherService['getPrograms']>>) {
  return programs.map((program) => ({
    id: program.id,
    name: program.displayName,
    estimatedMinutes: program.estimatedMinutes,
    isBuiltIn: program.isBuiltIn,
    options: program.options.map((option) =>
      option.kind === 'choice'
        ? { key: option.key, kind: option.kind, choices: option.choices, defaultValue: option.defaultValue }
        : option.kind === 'range'
          ? { key: option.key, kind: option.kind, minimum: option.minimum, maximum: option.maximum, step: option.step }
          : { key: option.key, kind: option.kind, defaultValue: option.defaultValue },
    ),
  }));
}

/** Runs one part of the report, turning a failure into a readable note. */
async function describeFailures<Result>(loadPart: () => Promise<Result>): Promise<Result | { failed: string }> {
  try {
    return await loadPart();
  } catch (error) {
    return { failed: error instanceof Error ? error.message : String(error) };
  }
}
