/**
 * The app's own view of a washing machine.
 *
 * Screens only ever use these types. The real h.on client and the demo
 * machine both translate their data into them, so the UI does not need to
 * know which one it is talking to.
 */

/** What the machine is doing, simplified from h.on's "machMode" numbers. */
export type MachineState =
  | 'offline' // the machine is not connected to the cloud
  | 'idle' // switched on, waiting for a program
  | 'scheduled' // delayed start is counting down
  | 'running'
  | 'paused'
  | 'finished' // the last program has ended
  | 'error';

/** The part of the wash the machine is in, e.g. "rinse" or "spin". */
export type WashPhase =
  | 'ready'
  | 'weighing'
  | 'heating'
  | 'washing'
  | 'rinse'
  | 'spin'
  | 'drying'
  | 'steam'
  | 'tumbling'
  | 'refresh'
  | 'scheduled'
  | 'unknown';

export interface MachineInfo {
  /** The name you gave the machine in h.on, or its model name. */
  displayName: string;
  modelName: string;
  brand: string;
}

export interface WasherStatus {
  state: MachineState;
  phase: WashPhase;
  /** Name of the running (or last) program, if known. */
  programName: string | null;
  /** Minutes until the program ends. Zero when nothing is running. */
  remainingMinutes: number;
  /** Minutes until a delayed start begins. Zero when not scheduled. */
  delayMinutes: number;
  temperatureCelsius: number | null;
  spinSpeedRpm: number | null;
  /** Energy and water used by the current program so far. */
  currentCycleEnergyKwh: number | null;
  currentCycleWaterLitres: number | null;
  doorLocked: boolean;
  /**
   * The machine only accepts commands from the app when "remote control"
   * is switched on at the machine itself.
   */
  remoteControlEnabled: boolean;
  /** Error code reported by the machine, or null when everything is fine. */
  errorCode: string | null;
  /** When this status was fetched. */
  fetchedAt: Date;
}

/** One adjustable setting of a program, e.g. temperature or extra rinse. */
export type ProgramOption =
  | {
      kind: 'choice';
      key: string;
      label: string;
      /** Allowed values, already in the format the machine expects. */
      choices: string[];
      defaultValue: string;
      unit: string | null;
    }
  | {
      kind: 'toggle';
      key: string;
      label: string;
      defaultValue: boolean;
    }
  | {
      kind: 'range';
      key: string;
      label: string;
      minimum: number;
      maximum: number;
      step: number;
      defaultValue: number;
      unit: string | null;
    };

export interface WasherProgram {
  /** h.on's own program id, e.g. "PROGRAMS.WM_WD.COTTONS". */
  id: string;
  displayName: string;
  description: string;
  /** The machine's estimate of how long the program takes, in minutes. */
  estimatedMinutes: number | null;
  /**
   * Built-in programs are the ones on the machine's dial. The others are
   * extra "app only" programs that h.on downloads to the machine.
   */
  isBuiltIn: boolean;
  options: ProgramOption[];
}

/** Values the user picked for a program's options, keyed by option key. */
export type ChosenOptions = Record<string, string | number | boolean>;

export interface UsageSummary {
  totalEnergyKwh: number | null;
  totalWaterLitres: number | null;
  totalCycles: number | null;
  /** How often each temperature band has been used, e.g. "0–30 °C": 16. */
  temperatureBands: { label: string; count: number }[];
}

/** One past wash, as reported by the h.on command history. */
export interface PastCommand {
  commandName: 'startProgram' | 'stopProgram' | 'pauseProgram' | 'resumeProgram' | string;
  programName: string | null;
  sentAt: Date | null;
}

/**
 * Everything the screens can ask of a washing machine. Implemented by
 * HonCloudWasher (the real one) and DemoWasher (a fake one for trying the
 * app without an account).
 */
export interface WasherService {
  readonly isDemo: boolean;
  getMachineInfo(): Promise<MachineInfo>;
  getStatus(): Promise<WasherStatus>;
  getPrograms(): Promise<WasherProgram[]>;
  startProgram(programId: string, chosenOptions: ChosenOptions): Promise<void>;
  pause(): Promise<void>;
  resume(): Promise<void>;
  stop(): Promise<void>;
  getUsageSummary(): Promise<UsageSummary>;
  getRecentCommands(): Promise<PastCommand[]>;
}

/** Thrown when the machine refuses or cannot receive a command. */
export class WasherCommandError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WasherCommandError';
  }
}
