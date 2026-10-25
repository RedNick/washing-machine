import type { HonAppliance, HonCommandCatalogue, HonContext } from '../honApiTypes';

/**
 * Small, made-up h.on responses shaped like real ones (based on the public
 * hon-test-data captures). Real catalogues have 100+ programs.
 */

export const washerAppliance: HonAppliance = {
  macAddress: 'aa-bb-cc-dd-ee-ff',
  applianceTypeName: 'WM',
  applianceModelId: 1301,
  modelName: 'H7W 412MBCR-80',
  brand: 'hoover',
  nickName: 'XXXXX',
  serialNumber: 'AB1CDEF2345GHIJK6789',
  eepromId: 41,
  fwVersion: '5.15.0',
  series: 'h-wash700Pro',
};

export const sampleCatalogue: HonCommandCatalogue = {
  applianceModel: { options: { opt1: 'prewash', opt5: 'extraRinse1' } },
  startProgram: {
    'PROGRAMS.WM_WD.COTTONS': {
      description: 'For cotton garments.',
      protocolType: 'MQTT',
      parameters: {
        prCode: { category: 'command', typology: 'fixed', mandatory: 1, fixedValue: '201' },
        temp: { category: 'command', typology: 'enum', mandatory: 1, defaultValue: '40', enumValues: [0, 20, 40, 60, 90] },
        spinSpeed: { category: 'command', typology: 'enum', mandatory: 1, defaultValue: '1400', enumValues: [0, 800, 1400] },
        delayTime: {
          category: 'command',
          typology: 'range',
          mandatory: 1,
          defaultValue: '0',
          minimumValue: '0',
          maximumValue: '1410',
          incrementValue: '30',
        },
        prewash: {
          category: 'command',
          typology: 'range',
          mandatory: 1,
          defaultValue: '0',
          minimumValue: '0',
          maximumValue: '1',
          incrementValue: '1',
        },
        weight: { category: 'command', typology: 'fixed', mandatory: 1, fixedValue: '40' },
        prStr: { category: 'command', typology: 'fixed', mandatory: 1, fixedValue: '' },
      },
      ancillaryParameters: {
        remoteVisible: { category: 'general', typology: 'fixed', mandatory: 0, fixedValue: '1' },
        suggestedLoadW: { category: 'general', typology: 'fixed', mandatory: 1, fixedValue: 12 },
        suggestedLoadWD: { category: 'general', typology: 'fixed', mandatory: 1, fixedValue: null },
        programFamily: { category: 'cluster', typology: 'enum', mandatory: 1, enumValues: ['dashboard'], defaultValue: '[dashboard]' },
        programRules: { category: 'rule', typology: 'fixed', fixedValue: '{}' },
      },
      remainingTimes: { remainingTime: { category: 'time', typology: 'fixed', mandatory: 1, fixedValue: '163' } },
    },
    'PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL': {
      description: 'Efficient wash.',
      parameters: {
        prCode: { category: 'command', typology: 'fixed', fixedValue: '202' },
      },
    },
    'PROGRAMS.WM_WD.IOT_WASH_MEN_S_TROUSERS': {
      description: 'App-only program.',
      parameters: { prCode: { category: 'command', typology: 'fixed', fixedValue: '300' } },
    },
    'PROGRAMS.WM_WD.IOT_WASH_BED_LINEN': {
      parameters: { prCode: { category: 'command', typology: 'fixed', fixedValue: '301' } },
    },
    'PROGRAMS.WM_WD.IOT_RECIPE_SECRET': {
      parameters: { prCode: { category: 'command', typology: 'fixed', fixedValue: '400' } },
    },
    'PROGRAMS.WM_WD.HIDDEN_ONE': {
      parameters: { prCode: { category: 'command', typology: 'fixed', fixedValue: '401' } },
      ancillaryParameters: { remoteVisible: { category: 'general', typology: 'fixed', fixedValue: '0' } },
    },
  },
  stopProgram: {
    description: 'The message allows to stop the program',
    parameters: { onOffStatus: { category: 'command', typology: 'fixed', mandatory: 1, fixedValue: '0' } },
  },
  pauseProgram: {
    parameters: { pause: { category: 'command', typology: 'fixed', mandatory: 1, fixedValue: '1' } },
  },
  resumeProgram: {
    parameters: { pause: { category: 'command', typology: 'fixed', mandatory: 1, fixedValue: '0' } },
  },
};

/** Builds a context response from simple name → value pairs. */
export function contextWith(
  values: Record<string, string>,
  connection: 'CONNECTED' | 'DISCONNECTED' = 'CONNECTED',
): HonContext {
  const parameters: Record<string, { parNewVal: string }> = {};
  for (const [name, value] of Object.entries(values)) parameters[name] = { parNewVal: value };
  return { shadow: { parameters }, activity: {}, lastConnEvent: { category: connection } };
}

export const runningContext = contextWith({
  machMode: '2',
  prPhase: '4',
  prCode: '201',
  remainingTimeMM: '83',
  temp: '40',
  spinSpeed: '0',
  currentElectricityUsed: '0.42',
  currentWaterUsed: '31',
  doorLockStatus: '1',
  remoteCtrValid: '1',
  errors: '00',
  totalElectricityUsed: '33.34',
  totalWaterUsed: '2571',
  totalWashCycle: '45',
});
