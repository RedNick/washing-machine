import { formatOptionValue } from '../../washer/formatters';
import type { ProgramOption } from '../../washer/washerTypes';
import { expandCombinedOptions } from '../buildCommandPayload';
import { combineDryingOptions } from '../dryingOption';

const temperature: ProgramOption = {
  kind: 'choice',
  key: 'temp',
  label: 'Temperature',
  choices: ['30', '40'],
  defaultValue: '40',
  unit: '°C',
};
const dryLevel: ProgramOption = {
  kind: 'choice',
  key: 'dryLevel',
  label: 'dryLevel',
  choices: ['0', '1', '2', '3'],
  defaultValue: '0',
  unit: null,
};
const dryTime: ProgramOption = {
  kind: 'range',
  key: 'dryTime',
  label: 'dryTime',
  minimum: 0,
  maximum: 4,
  step: 1,
  defaultValue: 0,
  unit: null,
};

describe('combineDryingOptions', () => {
  it('turns the level and time settings into one Drying choice', () => {
    const options = combineDryingOptions([temperature, dryLevel, dryTime]);
    expect(options.map((option) => option.key)).toEqual(['temp', 'drying']);
    expect(options[1]).toMatchObject({
      kind: 'choice',
      label: 'Drying',
      choices: ['none', 'level:1', 'level:2', 'level:3', 'time:1', 'time:2', 'time:3', 'time:4'],
      defaultValue: 'none',
    });
  });

  it('starts on the program’s own default level or time', () => {
    const withLevel = combineDryingOptions([{ ...dryLevel, defaultValue: '2' }, dryTime]);
    expect(withLevel[0].defaultValue).toBe('level:2');
    const withTime = combineDryingOptions([dryLevel, { ...dryTime, defaultValue: 3 }]);
    expect(withTime[0].defaultValue).toBe('time:3');
  });

  it('leaves programs without drying alone', () => {
    expect(combineDryingOptions([temperature])).toEqual([temperature]);
  });
});

describe('drying choices', () => {
  it('have the names shown on the machine, and times in half hours', () => {
    expect(formatOptionValue('drying', 'none', null)).toBe('No drying');
    expect(formatOptionValue('drying', 'level:1', null)).toBe('Iron dry');
    expect(formatOptionValue('drying', 'level:2', null)).toBe('Cupboard dry');
    expect(formatOptionValue('drying', 'level:3', null)).toBe('Extra dry');
    expect(formatOptionValue('drying', 'time:1', null)).toBe('30 min');
    expect(formatOptionValue('drying', 'time:3', null)).toBe('1 h 30 min');
  });

  it('send only a level or only a time, never both', () => {
    expect(expandCombinedOptions({ drying: 'level:2' })).toEqual({ dryLevel: 2, dryTime: 0 });
    expect(expandCombinedOptions({ drying: 'time:4' })).toEqual({ dryLevel: 0, dryTime: 4 });
    expect(expandCombinedOptions({ drying: 'none' })).toEqual({ dryLevel: 0, dryTime: 0 });
  });
});
