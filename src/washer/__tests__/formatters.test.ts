import { formatClockTimeFromNow, formatDuration, formatOptionValue, formatPastDate } from '../formatters';

describe('formatDuration', () => {
  it.each([
    [0, '0 min'],
    [45, '45 min'],
    [60, '1 h'],
    [83, '1 h 23 min'],
    [163, '2 h 43 min'],
  ])('%d minutes → %s', (minutes, expected) => {
    expect(formatDuration(minutes)).toBe(expected);
  });
});

describe('formatOptionValue', () => {
  it('uses friendly words for special values', () => {
    expect(formatOptionValue('temp', '0', '°C')).toBe('Cold');
    expect(formatOptionValue('temp', '40', '°C')).toBe('40 °C');
    expect(formatOptionValue('spinSpeed', '0', 'rpm')).toBe('No spin');
    expect(formatOptionValue('delayTime', 0, 'min')).toBe('Off');
    expect(formatOptionValue('delayTime', 90, 'min')).toBe('1 h 30 min');
    expect(formatOptionValue('extraRinses', '0', null)).toBe('None');
    expect(formatOptionValue('extraRinses', '2', null)).toBe('2');
  });
});

describe('formatPastDate', () => {
  const now = new Date(2026, 9, 25, 15, 0);

  it('says Today and Yesterday for recent dates', () => {
    expect(formatPastDate(new Date(2026, 9, 25, 9, 30), now)).toMatch(/^Today /);
    expect(formatPastDate(new Date(2026, 9, 24, 21, 0), now)).toMatch(/^Yesterday /);
    expect(formatPastDate(new Date(2026, 9, 12, 18, 20), now)).not.toMatch(/^(Today|Yesterday)/);
  });
});

describe('formatClockTimeFromNow', () => {
  const now = new Date(2026, 9, 28, 21, 15);

  it('says "tomorrow" only when the time is on the next day', () => {
    expect(formatClockTimeFromNow(90, now)).not.toMatch(/tomorrow/);
    expect(formatClockTimeFromNow(180, now)).toMatch(/^tomorrow /);
  });
});
