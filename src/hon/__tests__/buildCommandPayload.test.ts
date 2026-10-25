import { buildCommandPayload } from '../buildCommandPayload';
import { sampleCatalogue, washerAppliance } from './honFixtures';

const fixedTime = new Date('2026-10-25T09:40:12.123Z');

describe('buildCommandPayload', () => {
  it('builds a start command with the user’s choices and every default', () => {
    const body = buildCommandPayload({
      appliance: washerAppliance,
      applianceOptions: sampleCatalogue.applianceModel!.options!,
      commandName: 'startProgram',
      command: sampleCatalogue.startProgram!['PROGRAMS.WM_WD.COTTONS'],
      programId: 'PROGRAMS.WM_WD.COTTONS',
      chosenOptions: { temp: '60', prewash: true, delayTime: 90 },
      mobileId: 'test-phone',
      now: fixedTime,
    });

    expect(body.commandName).toBe('startProgram');
    expect(body.programName).toBe('PROGRAMS.WM_WD.COTTONS');
    expect(body.timestamp).toBe('2026-10-25T09:40:12.123Z');
    expect(body.transactionId).toBe('aa-bb-cc-dd-ee-ff_2026-10-25T09:40:12.123Z');
    expect(body.applianceType).toBe('WM');
    expect(body.device).toEqual({
      appVersion: '2.6.5',
      mobileId: 'test-phone',
      mobileOs: 'android',
      osVersion: 999,
      deviceModel: 'WasherApp',
    });
    expect(body.parameters).toEqual({
      prCode: '201',
      temp: '60',
      spinSpeed: '1400',
      delayTime: '90',
      prewash: '1',
      weight: '40',
      prStr: 'PROGRAMS.WM_WD.COTTONS',
    });
  });

  it('sends ancillary values but leaves out the program rules', () => {
    const body = buildCommandPayload({
      appliance: washerAppliance,
      applianceOptions: {},
      commandName: 'startProgram',
      command: sampleCatalogue.startProgram!['PROGRAMS.WM_WD.COTTONS'],
      programId: 'PROGRAMS.WM_WD.COTTONS',
      mobileId: 'test-phone',
      now: fixedTime,
    });
    expect(body.ancillaryParameters).toEqual({
      remoteVisible: '1',
      suggestedLoadW: '12',
      suggestedLoadWD: '0',
      programFamily: 'dashboard',
    });
  });

  it('builds a stop command with no program name', () => {
    const body = buildCommandPayload({
      appliance: washerAppliance,
      applianceOptions: {},
      commandName: 'stopProgram',
      command: sampleCatalogue.stopProgram!,
      mobileId: 'test-phone',
      now: fixedTime,
    });
    expect(body.parameters).toEqual({ onOffStatus: '0' });
    expect(body.programName).toBeUndefined();
  });
});
