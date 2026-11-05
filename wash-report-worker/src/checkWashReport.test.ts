import { MAX_REPORT_BYTES, checkWashReport } from './checkWashReport';

const validReport = {
  reportFormat: 1,
  app: { version: 'h.off 1.0.0 · build 50' },
  machine: { modelName: 'HWD100' },
  wash: { programName: 'Cottons', finishedAt: '2026-11-05T09:30:00.000Z' },
};

describe('checkWashReport', () => {
  it('accepts a report of the expected shape', () => {
    expect(checkWashReport(JSON.stringify(validReport))).toMatchObject({
      ok: true,
      report: { appVersion: 'h.off 1.0.0 · build 50', modelName: 'HWD100', programName: 'Cottons' },
    });
  });

  it.each([
    ['not JSON', 'hello'],
    ['a list', '[]'],
    ['an unknown format', JSON.stringify({ ...validReport, reportFormat: 2 })],
    ['missing the model', JSON.stringify({ ...validReport, machine: {} })],
    ['a bad date', JSON.stringify({ ...validReport, wash: { finishedAt: 'yesterday' } })],
    ['too large', JSON.stringify({ ...validReport, padding: 'x'.repeat(MAX_REPORT_BYTES) })],
  ])('refuses a report that is %s', (_description, bodyText) => {
    expect(checkWashReport(bodyText).ok).toBe(false);
  });
});
