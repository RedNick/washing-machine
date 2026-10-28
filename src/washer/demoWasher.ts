import {
  WasherCommandError,
  type ChosenOptions,
  type MachineInfo,
  type PastCommand,
  type UsageSummary,
  type WashPhase,
  type WasherProgram,
  type WasherService,
  type WasherStatus,
} from './washerTypes';

/**
 * In the demo, one real second counts as one machine minute, so a whole
 * 2-hour wash (and its "finished" alert) plays out in about 2 minutes.
 */
const DEMO_MINUTES_PER_REAL_SECOND = 1;

const TEMPERATURE_CHOICES = ['0', '20', '30', '40', '60', '90'];
const SPIN_CHOICES = ['0', '400', '800', '1000', '1200', '1400'];

/** A few typical programs, shaped like what h.on returns for real machines. */
const DEMO_PROGRAMS: WasherProgram[] = [
  {
    id: 'PROGRAMS.WM_WD.COTTONS',
    displayName: 'Cottons',
    description: 'Maximum washing for cotton garments, even when fully loaded.',
    estimatedMinutes: 163,
    isBuiltIn: true,
    options: [
      { kind: 'choice', key: 'temp', label: 'Temperature', choices: TEMPERATURE_CHOICES, defaultValue: '40', unit: '°C' },
      { kind: 'choice', key: 'spinSpeed', label: 'Spin speed', choices: SPIN_CHOICES, defaultValue: '1400', unit: 'rpm' },
      { kind: 'range', key: 'delayTime', label: 'Delay start', minimum: 0, maximum: 1410, step: 30, defaultValue: 0, unit: 'min' },
      { kind: 'toggle', key: 'prewash', label: 'Prewash', defaultValue: false },
      { kind: 'choice', key: 'extraRinses', label: 'Extra rinses', choices: ['0', '1', '2', '3'], defaultValue: '0', unit: null },
    ],
  },
  {
    id: 'PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL',
    displayName: 'Eco 40-60',
    description: 'The most efficient program for normally soiled cotton.',
    estimatedMinutes: 228,
    isBuiltIn: true,
    options: [
      { kind: 'choice', key: 'spinSpeed', label: 'Spin speed', choices: SPIN_CHOICES, defaultValue: '1200', unit: 'rpm' },
      { kind: 'range', key: 'delayTime', label: 'Delay start', minimum: 0, maximum: 1410, step: 30, defaultValue: 0, unit: 'min' },
    ],
  },
  {
    id: 'PROGRAMS.WM_WD.SYNTHETIC_AND_COLOURED',
    displayName: 'Synthetic and coloured',
    description: 'For synthetic and mixed fabrics.',
    estimatedMinutes: 109,
    isBuiltIn: true,
    options: [
      { kind: 'choice', key: 'temp', label: 'Temperature', choices: ['0', '20', '30', '40', '60'], defaultValue: '40', unit: '°C' },
      { kind: 'choice', key: 'spinSpeed', label: 'Spin speed', choices: SPIN_CHOICES.slice(0, 5), defaultValue: '1000', unit: 'rpm' },
      { kind: 'toggle', key: 'anticrease', label: 'Anti-crease', defaultValue: false },
    ],
  },
  {
    id: 'PROGRAMS.WM_WD.CARE_14',
    displayName: 'Care 14',
    description: 'A quick refresh for lightly soiled clothes.',
    estimatedMinutes: 14,
    isBuiltIn: true,
    options: [
      { kind: 'choice', key: 'temp', label: 'Temperature', choices: ['0', '20', '30'], defaultValue: '30', unit: '°C' },
    ],
  },
  {
    id: 'PROGRAMS.WM_WD.RINSE',
    displayName: 'Rinse',
    description: 'Rinse and spin only.',
    estimatedMinutes: 25,
    isBuiltIn: true,
    options: [],
  },
  {
    id: 'PROGRAMS.WM_WD.IOT_WASH_BED_LINEN',
    displayName: 'Bed linen',
    description: 'An app-only program for sheets and duvet covers.',
    estimatedMinutes: 120,
    isBuiltIn: false,
    options: [
      { kind: 'choice', key: 'temp', label: 'Temperature', choices: ['40', '60'], defaultValue: '60', unit: '°C' },
    ],
  },
];

/** Where the demo wash is, in machine minutes. */
interface DemoCycle {
  program: WasherProgram;
  totalMinutes: number;
  /** Machine minutes run before the latest resume. */
  minutesRunBeforeResume: number;
  /** Real time of the latest start/resume, or null while paused. */
  resumedAtMs: number | null;
  temperatureCelsius: number;
  spinSpeedRpm: number;
}

function phaseForProgress(progress: number): WashPhase {
  if (progress < 0.05) return 'weighing';
  if (progress < 0.15) return 'heating';
  if (progress < 0.6) return 'washing';
  if (progress < 0.85) return 'rinse';
  return 'spin';
}

/**
 * A pretend washing machine that behaves like a real one, for trying the app
 * (and developing it) without an h.on account. Nothing leaves the phone.
 */
export class DemoWasher implements WasherService {
  readonly isDemo = true;

  private cycle: DemoCycle | null = null;
  private finishedProgramName: string | null = null;
  private totalEnergyKwh = 33.34;
  private totalWaterLitres = 2571;
  private totalCycles = 45;
  private readonly pastCommands: PastCommand[] = [];

  /** @param now lets tests control the clock. */
  constructor(private readonly now: () => number = Date.now) {}

  async getMachineInfo(): Promise<MachineInfo> {
    return { displayName: 'Demo washer', modelName: 'Demo model', brand: 'Demo' };
  }

  async getStatus(): Promise<WasherStatus> {
    this.finishCycleIfDone();
    const fetchedAt = new Date(this.now());
    const baseStatus = {
      temperatureCelsius: null,
      spinSpeedRpm: null,
      currentCycleEnergyKwh: null,
      currentCycleWaterLitres: null,
      doorLocked: false,
      remoteControlEnabled: true,
      errorCode: null,
      delayMinutes: 0,
      fetchedAt,
    };

    if (!this.cycle) {
      return {
        ...baseStatus,
        state: this.finishedProgramName ? 'finished' : 'idle',
        phase: 'ready',
        programName: this.finishedProgramName,
        remainingMinutes: 0,
      };
    }

    const minutesRun = this.minutesRun(this.cycle);
    const progress = minutesRun / this.cycle.totalMinutes;
    return {
      ...baseStatus,
      state: this.cycle.resumedAtMs === null ? 'paused' : 'running',
      phase: phaseForProgress(progress),
      programName: this.cycle.program.displayName,
      remainingMinutes: Math.max(0, Math.ceil(this.cycle.totalMinutes - minutesRun)),
      temperatureCelsius: this.cycle.temperatureCelsius,
      spinSpeedRpm: progress >= 0.85 ? this.cycle.spinSpeedRpm : 0,
      currentCycleEnergyKwh: Math.round(progress * 0.9 * 100) / 100,
      currentCycleWaterLitres: Math.round(progress * 55),
      doorLocked: true,
    };
  }

  async getPrograms(): Promise<WasherProgram[]> {
    return DEMO_PROGRAMS;
  }

  async startProgram(programId: string, chosenOptions: ChosenOptions): Promise<void> {
    this.finishCycleIfDone();
    if (this.cycle) throw new WasherCommandError('A program is already running. Stop it first.');
    const program = DEMO_PROGRAMS.find((candidate) => candidate.id === programId);
    if (!program) throw new WasherCommandError(`Unknown program "${programId}".`);

    this.cycle = {
      program,
      totalMinutes: program.estimatedMinutes ?? 60,
      minutesRunBeforeResume: 0,
      resumedAtMs: this.now(),
      temperatureCelsius: Number(chosenOptions.temp ?? 40),
      spinSpeedRpm: Number(chosenOptions.spinSpeed ?? 1200),
    };
    this.finishedProgramName = null;
    this.recordCommand('startProgram', program.displayName);
  }

  async pause(): Promise<void> {
    const cycle = this.requireRunningCycle();
    if (cycle.resumedAtMs === null) return;
    cycle.minutesRunBeforeResume = this.minutesRun(cycle);
    cycle.resumedAtMs = null;
    this.recordCommand('pauseProgram', cycle.program.displayName);
  }

  async resume(): Promise<void> {
    const cycle = this.requireRunningCycle();
    if (cycle.resumedAtMs !== null) return;
    cycle.resumedAtMs = this.now();
    this.recordCommand('resumeProgram', cycle.program.displayName);
  }

  async stop(): Promise<void> {
    const cycle = this.requireRunningCycle();
    this.cycle = null;
    this.finishedProgramName = null;
    this.recordCommand('stopProgram', cycle.program.displayName);
  }

  async getUsageSummary(): Promise<UsageSummary> {
    this.finishCycleIfDone();
    return {
      totalEnergyKwh: Math.round(this.totalEnergyKwh * 100) / 100,
      totalWaterLitres: this.totalWaterLitres,
      totalCycles: this.totalCycles,
      temperatureBands: [
        { label: '0–30 °C', count: 16 },
        { label: '40 °C', count: 18 },
        { label: '60–90 °C', count: 11 },
      ],
    };
  }

  async getRecentCommands(): Promise<PastCommand[]> {
    return [...this.pastCommands];
  }

  private minutesRun(cycle: DemoCycle): number {
    if (cycle.resumedAtMs === null) return cycle.minutesRunBeforeResume;
    const realSecondsSinceResume = (this.now() - cycle.resumedAtMs) / 1000;
    return Math.min(
      cycle.totalMinutes,
      cycle.minutesRunBeforeResume + realSecondsSinceResume * DEMO_MINUTES_PER_REAL_SECOND,
    );
  }

  /** Moves a completed demo wash into the totals. */
  private finishCycleIfDone(): void {
    if (!this.cycle || this.minutesRun(this.cycle) < this.cycle.totalMinutes) return;
    this.totalEnergyKwh += 0.9;
    this.totalWaterLitres += 55;
    this.totalCycles += 1;
    this.finishedProgramName = this.cycle.program.displayName;
    this.cycle = null;
  }

  private requireRunningCycle(): DemoCycle {
    this.finishCycleIfDone();
    if (!this.cycle) throw new WasherCommandError('Nothing is running.');
    return this.cycle;
  }

  private recordCommand(commandName: PastCommand['commandName'], programName: string): void {
    this.pastCommands.unshift({ commandName, programName, sentAt: new Date(this.now()) });
  }
}
