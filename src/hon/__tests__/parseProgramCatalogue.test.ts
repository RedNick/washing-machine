import {
  buildProgramNameByCode,
  listLeftOutPrograms,
  parsePrograms,
  programDisplayName,
} from '../parseProgramCatalogue';
import { sampleCatalogue } from './honFixtures';

describe('programDisplayName', () => {
  it.each([
    ['PROGRAMS.WM_WD.COTTONS', 'Cottons'],
    ['PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL', 'Eco 40-60'],
    ['PROGRAMS.WM_WD.IOT_WASH_MEN_S_TROUSERS', "Men's trousers"],
    ['PROGRAMS.WM_WD.RAPID_WASH_AND_DRY_59_MIN', 'Rapid wash and dry 59 min'],
  ])('%s → %s', (programId, expectedName) => {
    expect(programDisplayName(programId)).toBe(expectedName);
  });
});

describe('parsePrograms', () => {
  const programs = parsePrograms(sampleCatalogue);

  it('lists built-in programs first, then app extras alphabetically', () => {
    expect(programs.map((program) => program.displayName)).toEqual([
      'Cottons',
      'Eco 40-60',
      'Bed linen',
      "Men's trousers",
    ]);
  });

  it('hides recipe programs and ones marked not visible', () => {
    const ids = programs.map((program) => program.id);
    expect(ids).not.toContain('PROGRAMS.WM_WD.IOT_RECIPE_SECRET');
    expect(ids).not.toContain('PROGRAMS.WM_WD.HIDDEN_ONE');
  });

  it('keeps programs whose remoteVisible value is missing or empty', () => {
    const catalogueWithBlankVisibility = {
      startProgram: {
        'PROGRAMS.WM_WD.COTTON': {
          ...sampleCatalogue.startProgram!['PROGRAMS.WM_WD.HIDDEN_ONE'],
          ancillaryParameters: { remoteVisible: { category: 'general', typology: 'fixed', fixedValue: null } },
        },
        'PROGRAMS.WM_WD.SYNTHETIC': {
          ...sampleCatalogue.startProgram!['PROGRAMS.WM_WD.HIDDEN_ONE'],
          ancillaryParameters: {},
        },
      },
    };
    expect(parsePrograms(catalogueWithBlankVisibility).map((program) => program.id)).toEqual([
      'PROGRAMS.WM_WD.COTTON',
      'PROGRAMS.WM_WD.SYNTHETIC',
    ]);
  });

  it('lists the programs it leaves out, with the reason, for reports', () => {
    expect(listLeftOutPrograms(sampleCatalogue)).toEqual([
      { id: 'PROGRAMS.WM_WD.IOT_RECIPE_SECRET', reason: expect.stringContaining('recipe') },
      { id: 'PROGRAMS.WM_WD.HIDDEN_ONE', reason: expect.stringContaining('remoteVisible') },
    ]);
  });

  it('turns parameters into options the user can change', () => {
    const cottons = programs[0];
    expect(cottons.estimatedMinutes).toBe(163);
    expect(cottons.options).toEqual([
      {
        kind: 'choice',
        key: 'temp',
        label: 'Temperature',
        choices: ['0', '20', '40', '60', '90'],
        defaultValue: '40',
        unit: '°C',
      },
      {
        kind: 'choice',
        key: 'spinSpeed',
        label: 'Spin speed',
        choices: ['0', '800', '1400'],
        defaultValue: '1400',
        unit: 'rpm',
      },
      {
        kind: 'choice',
        key: 'extraRinses',
        label: 'Extra rinses',
        choices: ['0', '1', '2'],
        defaultValue: '1',
        unit: null,
      },
      {
        kind: 'range',
        key: 'delayTime',
        label: 'Delay start',
        minimum: 0,
        maximum: 1410,
        step: 30,
        defaultValue: 0,
        unit: 'min',
      },
      { kind: 'toggle', key: 'prewash', label: 'Prewash', defaultValue: false },
    ]);
  });

  it('leaves out settings that are not wash options, like the display language', () => {
    const optionKeys = programs[0].options.map((option) => option.key);
    expect(optionKeys).not.toContain('lang');
    expect(optionKeys).not.toContain('delayStatus');
  });
});

describe('buildProgramNameByCode', () => {
  it('maps the machine’s program numbers to names', () => {
    const programNameByCode = buildProgramNameByCode(sampleCatalogue);
    expect(programNameByCode.get(201)).toBe('Cottons');
    expect(programNameByCode.get(300)).toBe("Men's trousers");
  });
});
