import { REMOVED, redactPrivateDetails } from '../redactPrivateDetails';

describe('redactPrivateDetails', () => {
  it('removes tokens, MAC addresses, serial numbers and account details by field name', () => {
    expect(
      redactPrivateDetails({
        macAddress: 'ab-cd-ef-01-23-45',
        serialNumber: '123456',
        idToken: 'secret',
        nickName: 'Washer Dryer',
        mac: 'x',
        machine: { modelName: 'HWD100' },
        parameters: { machMode: { parNewVal: '2' } },
      }),
    ).toEqual({
      macAddress: REMOVED,
      serialNumber: REMOVED,
      idToken: REMOVED,
      nickName: REMOVED,
      mac: REMOVED,
      machine: { modelName: 'HWD100' },
      parameters: { machMode: { parNewVal: '2' } },
    });
  });

  it('removes MAC and email addresses hidden inside other text', () => {
    expect(
      redactPrivateDetails({ transactionId: 'ab-cd-ef-01-23-45_2026-11-03T21:36:00Z', note: 'from me@example.com' }),
    ).toEqual({ transactionId: `${REMOVED}_2026-11-03T21:36:00Z`, note: `from ${REMOVED}` });
  });

  it('keeps numbers, booleans and dates, and works through lists', () => {
    expect(redactPrivateDetails([1, true, null, new Date('2026-11-04T09:00:00Z')])).toEqual([
      1,
      true,
      null,
      '2026-11-04T09:00:00.000Z',
    ]);
  });
});
