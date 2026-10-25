import type {
  HonAppliance,
  HonCommandCatalogue,
  HonCommandDefinition,
  HonContext,
  HonHistoryEntry,
  HonStatistics,
} from './honApiTypes';
import type { FetchFunction, HonSession } from './auth/honSession';
import { buildCommandPayload, type BuildCommandInput } from './buildCommandPayload';
import { sendHonApiRequest, type HonApiRequest } from './honApiRequest';
import { HON_APP_VERSION, HON_OS_NAME } from './honConstants';
import { buildProgramNameByCode, parsePrograms, programDisplayName } from './parseProgramCatalogue';
import { parseLifetimeTotals, parseWasherStatus } from './parseWasherStatus';
import {
  WasherCommandError,
  type ChosenOptions,
  type MachineInfo,
  type PastCommand,
  type UsageSummary,
  type WasherProgram,
  type WasherService,
  type WasherStatus,
} from '../washer/washerTypes';

/** Appliance types we treat as a washing machine (WD = washer-dryer). */
const WASHER_TYPE_NAMES = ['WM', 'WD'];

/** Readable names for the temperature bands in h.on's statistics. */
const TEMPERATURE_BAND_LABELS: Record<string, string> = {
  temp0to30: '0–30 °C',
  temp40: '40 °C',
  temp60to90: '60–90 °C',
};

export class NoWasherFoundError extends Error {
  constructor() {
    super('No washing machine was found on this h.on account.');
    this.name = 'NoWasherFoundError';
  }
}

/**
 * The real washing machine, reached through Haier's h.on cloud.
 * Follows the same requests as the pyhOn library.
 */
export class HonCloudWasher implements WasherService {
  readonly isDemo = false;
  /** The command catalogue is large (around 1 MB) and rarely changes, so keep it. */
  private cachedCatalogue: HonCommandCatalogue | null = null;

  private constructor(
    private readonly session: HonSession,
    private readonly appliance: HonAppliance,
    private readonly fetchFunction: FetchFunction,
  ) {}

  /** Finds the washing machine on the signed-in account. */
  static async connect(session: HonSession, fetchFunction: FetchFunction = fetch): Promise<HonCloudWasher> {
    const response = await sendHonApiRequest<{ payload?: { appliances?: HonAppliance[] } }>(
      session,
      fetchFunction,
      { method: 'GET', path: '/commands/v1/appliance' },
    );
    const washer = (response.payload?.appliances ?? []).find((appliance) =>
      WASHER_TYPE_NAMES.includes(appliance.applianceTypeName),
    );
    if (!washer) throw new NoWasherFoundError();
    return new HonCloudWasher(session, washer, fetchFunction);
  }

  async getMachineInfo(): Promise<MachineInfo> {
    const modelName = this.appliance.modelName ?? 'Washing machine';
    const nickName = this.appliance.nickName ?? '';
    // h.on fills in placeholder nicknames like "XXX" when none was set.
    const hasRealNickName = nickName !== '' && !/^[xX1\s-]+$/.test(nickName);
    const brand = this.appliance.brand ?? '';
    return {
      displayName: hasRealNickName ? nickName : modelName,
      modelName,
      brand: brand.charAt(0).toUpperCase() + brand.slice(1),
    };
  }

  async getStatus(): Promise<WasherStatus> {
    const [context, catalogue] = await Promise.all([this.loadContext(), this.loadCatalogue()]);
    return parseWasherStatus(context, buildProgramNameByCode(catalogue));
  }

  /**
   * Status without the program name, which saves downloading the large
   * program catalogue. Used by the background check.
   */
  async getStatusWithoutProgramName(): Promise<WasherStatus> {
    return parseWasherStatus(await this.loadContext(), new Map());
  }

  async getPrograms(): Promise<WasherProgram[]> {
    return parsePrograms(await this.loadCatalogue());
  }

  async startProgram(programId: string, chosenOptions: ChosenOptions): Promise<void> {
    const catalogue = await this.loadCatalogue();
    const program = catalogue.startProgram?.[programId];
    if (!program) throw new WasherCommandError(`Your machine doesn't offer the program "${programId}".`);
    await this.sendCommand('startProgram', program, programId, chosenOptions);
  }

  pause(): Promise<void> {
    return this.sendSimpleCommand('pauseProgram');
  }

  resume(): Promise<void> {
    return this.sendSimpleCommand('resumeProgram');
  }

  stop(): Promise<void> {
    return this.sendSimpleCommand('stopProgram');
  }

  async getUsageSummary(): Promise<UsageSummary> {
    const [context, statisticsResponse] = await Promise.all([
      this.loadContext(),
      this.requestJson<{ payload?: HonStatistics }>('GET', '/commands/v1/statistics', {
        macAddress: this.appliance.macAddress,
        applianceType: this.appliance.applianceTypeName,
      }),
    ]);
    const temperatureUsage = statisticsResponse.payload?.temperatureUsage ?? {};
    return {
      ...parseLifetimeTotals(context),
      temperatureBands: Object.entries(temperatureUsage).map(([bandKey, count]) => ({
        label: TEMPERATURE_BAND_LABELS[bandKey] ?? bandKey,
        count,
      })),
    };
  }

  async getRecentCommands(): Promise<PastCommand[]> {
    const response = await this.requestJson<{ payload?: { history?: HonHistoryEntry[] } }>(
      'GET',
      `/commands/v1/appliance/${this.appliance.macAddress}/history`,
    );
    return (response.payload?.history ?? []).map((entry) => {
      const sentAtText = entry.timestampExecuted ?? entry.timestampAccepted;
      const programId = entry.command?.programName;
      return {
        commandName: entry.command?.commandName ?? 'unknown',
        programName: programId ? programDisplayName(programId) : null,
        sentAt: sentAtText ? new Date(sentAtText) : null,
      };
    });
  }

  private async sendSimpleCommand(
    commandName: 'pauseProgram' | 'resumeProgram' | 'stopProgram',
  ): Promise<void> {
    const catalogue = await this.loadCatalogue();
    const command = catalogue[commandName];
    if (!command) throw new WasherCommandError(`Your machine doesn't support "${commandName}".`);
    await this.sendCommand(commandName, command);
  }

  private async sendCommand(
    commandName: BuildCommandInput['commandName'],
    command: HonCommandDefinition,
    programId?: string,
    chosenOptions?: ChosenOptions,
  ): Promise<void> {
    const catalogue = await this.loadCatalogue();
    const body = buildCommandPayload({
      appliance: this.appliance,
      applianceOptions: catalogue.applianceModel?.options ?? {},
      commandName,
      command,
      programId,
      chosenOptions,
      mobileId: this.session.mobileId,
    });
    const response = await this.requestJson<{ payload?: { resultCode?: string } }>(
      'POST',
      '/commands/v1/send',
      undefined,
      body,
    );
    if (response.payload?.resultCode !== '0') {
      throw new WasherCommandError(
        'The machine did not accept the command. Check that remote control is switched on at the machine.',
      );
    }
  }

  private async loadContext(): Promise<HonContext> {
    const response = await this.requestJson<{ payload?: HonContext }>('GET', '/commands/v1/context', {
      macAddress: this.appliance.macAddress,
      applianceType: this.appliance.applianceTypeName,
      category: 'CYCLE',
    });
    return response.payload ?? {};
  }

  private async loadCatalogue(): Promise<HonCommandCatalogue> {
    if (this.cachedCatalogue) return this.cachedCatalogue;

    const query: Record<string, string> = {
      applianceType: this.appliance.applianceTypeName,
      applianceModelId: String(this.appliance.applianceModelId),
      macAddress: this.appliance.macAddress,
      os: HON_OS_NAME,
      appVersion: HON_APP_VERSION,
      code: this.applianceCode(),
    };
    if (this.appliance.eepromId !== undefined) query.firmwareId = String(this.appliance.eepromId);
    if (this.appliance.fwVersion) query.fwVersion = this.appliance.fwVersion;
    if (this.appliance.series) query.series = this.appliance.series;

    const response = await this.requestJson<{ payload?: HonCommandCatalogue & { resultCode?: string } }>(
      'GET',
      '/commands/v1/retrieve',
      query,
    );
    const { resultCode, ...catalogue } = response.payload ?? {};
    if (resultCode !== '0') throw new WasherCommandError("Couldn't load your machine's programs from h.on.");
    this.cachedCatalogue = catalogue;
    return catalogue;
  }

  /** h.on's model code; pyhOn derives it from the serial number when missing. */
  private applianceCode(): string {
    if (this.appliance.code) return this.appliance.code;
    const serialNumber = this.appliance.serialNumber ?? '';
    return serialNumber.length < 18 ? serialNumber.slice(0, 8) : serialNumber.slice(0, 11);
  }

  /** Sends a request for this washer's session. */
  private requestJson<ResponseBody>(
    method: HonApiRequest['method'],
    path: string,
    query?: Record<string, string>,
    body?: unknown,
  ): Promise<ResponseBody> {
    return sendHonApiRequest<ResponseBody>(this.session, this.fetchFunction, { method, path, query, body });
  }
}
