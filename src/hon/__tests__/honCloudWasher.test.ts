import { HonAuthError, HonSession, type SavedHonSession, type SessionStore } from '../auth/honSession';
import { HonCloudWasher, NoWasherFoundError } from '../honCloudWasher';
import { WasherCommandError } from '../../washer/washerTypes';
import { runningContext, sampleCatalogue, washerAppliance } from './honFixtures';

/** A pretend network: each test says how the h.on servers should answer. */
type FakeRoute = (url: string, init: RequestInit) => { status?: number; body: unknown } | undefined;

function createFakeFetch(route: FakeRoute) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fakeFetch = jest.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input);
    calls.push({ url, init });
    const answer = route(url, init);
    if (!answer) throw new Error(`Unexpected request to ${url}`);
    const status = answer.status ?? 200;
    return {
      ok: status >= 200 && status < 300,
      status,
      json: async () => answer.body,
    } as Response;
  });
  return { fakeFetch: fakeFetch as unknown as typeof fetch, calls };
}

function createMemoryStore(initial: SavedHonSession | null = null): SessionStore & { saved: SavedHonSession | null } {
  return {
    saved: initial,
    async load() {
      return this.saved;
    },
    async save(session) {
      this.saved = session;
    },
    async clear() {
      this.saved = null;
    },
  };
}

/** Answers for a healthy account with one washing machine. */
function healthyAccount(extra?: FakeRoute): FakeRoute {
  let cognitoCounter = 0;
  return (url, init) => {
    const custom = extra?.(url, init);
    if (custom) return custom;
    if (url.includes('/services/oauth2/token')) return { body: { id_token: 'new-id-token', access_token: 'a' } };
    if (url.endsWith('/auth/v1/login')) return { body: { cognitoUser: { Token: `cognito-${++cognitoCounter}` } } };
    if (url.includes('/commands/v1/appliance?') || url.endsWith('/commands/v1/appliance')) {
      return { body: { payload: { appliances: [{ applianceTypeName: 'REF', macAddress: 'fridge' }, washerAppliance] } } };
    }
    if (url.includes('/commands/v1/retrieve')) return { body: { payload: { resultCode: '0', ...sampleCatalogue } } };
    if (url.includes('/commands/v1/context')) return { body: { payload: runningContext } };
    if (url.includes('/commands/v1/send')) return { body: { payload: { resultCode: '0' } } };
    return undefined;
  };
}

describe('HonSession', () => {
  it('signs in, swaps the id token for a cognito token, and saves only the refresh token', async () => {
    const store = createMemoryStore();
    const { fakeFetch, calls } = createFakeFetch(healthyAccount());
    const session = await HonSession.fromLogin(
      { accessToken: 'a', refreshToken: 'refresh-1', idToken: 'id-1' },
      store,
      fakeFetch,
    );

    const loginCall = calls.find((call) => call.url.endsWith('/auth/v1/login'))!;
    expect((loginCall.init.headers as Record<string, string>)['id-token']).toBe('id-1');
    expect(store.saved?.refreshToken).toBe('refresh-1');
    expect(store.saved?.mobileId).toMatch(/^[0-9a-f]{16}$/);

    const headers = await session.authorizedHeaders();
    expect(headers['cognito-token']).toBe('cognito-1');
    expect(headers['id-token']).toBe('id-1');
  });

  it('returns null when nothing is saved', async () => {
    const { fakeFetch } = createFakeFetch(healthyAccount());
    await expect(HonSession.restore(createMemoryStore(), fakeFetch)).resolves.toBeNull();
  });

  it('reports an expired sign-in when the refresh is refused', async () => {
    const store = createMemoryStore({ refreshToken: 'old', mobileId: 'phone' });
    const { fakeFetch } = createFakeFetch(
      healthyAccount((url) => (url.includes('/oauth2/token') ? { status: 400, body: {} } : undefined)),
    );
    await expect(HonSession.restore(store, fakeFetch)).rejects.toBeInstanceOf(HonAuthError);
  });
});

describe('HonCloudWasher', () => {
  async function connectedWasher(extra?: FakeRoute) {
    const store = createMemoryStore({ refreshToken: 'refresh-1', mobileId: 'phone' });
    const network = createFakeFetch(healthyAccount(extra));
    const session = (await HonSession.restore(store, network.fakeFetch))!;
    const washer = await HonCloudWasher.connect(session, network.fakeFetch);
    return { washer, ...network };
  }

  it('finds the washing machine among other appliances', async () => {
    const { washer } = await connectedWasher();
    await expect(washer.getMachineInfo()).resolves.toEqual({
      // The nickname is a placeholder, so the model name is used.
      displayName: 'H7W 412MBCR-80',
      modelName: 'H7W 412MBCR-80',
      brand: 'Hoover',
    });
  });

  it('fails clearly when there is no washing machine', async () => {
    const store = createMemoryStore({ refreshToken: 'r', mobileId: 'phone' });
    const { fakeFetch } = createFakeFetch(
      healthyAccount((url) =>
        url.endsWith('/commands/v1/appliance') ? { body: { payload: { appliances: [] } } } : undefined,
      ),
    );
    const session = (await HonSession.restore(store, fakeFetch))!;
    await expect(HonCloudWasher.connect(session, fakeFetch)).rejects.toBeInstanceOf(NoWasherFoundError);
  });

  it('reads the status with the program name from the catalogue', async () => {
    const { washer, calls } = await connectedWasher();
    const status = await washer.getStatus();
    expect(status.state).toBe('running');
    expect(status.programName).toBe('Cottons');

    const retrieveCall = calls.find((call) => call.url.includes('/commands/v1/retrieve'))!;
    // The model code comes from the serial number when h.on doesn't send one.
    expect(retrieveCall.url).toContain('code=AB1CDEF2345');
    expect(retrieveCall.url).toContain('firmwareId=41');
  });

  it('downloads the large program catalogue only once', async () => {
    const { washer, calls } = await connectedWasher();
    await washer.getStatus();
    await washer.getPrograms();
    await washer.getStatus();
    expect(calls.filter((call) => call.url.includes('/commands/v1/retrieve'))).toHaveLength(1);
  });

  it('refreshes the tokens and retries once when h.on says they are stale', async () => {
    let contextRequests = 0;
    const { washer, calls } = await connectedWasher((url) => {
      if (!url.includes('/commands/v1/context')) return undefined;
      contextRequests += 1;
      return contextRequests === 1 ? { status: 401, body: {} } : undefined;
    });
    await expect(washer.getStatusWithoutProgramName()).resolves.toMatchObject({ state: 'running' });
    expect(contextRequests).toBe(2);
    // One refresh when restoring, one more after the 401.
    expect(calls.filter((call) => call.url.includes('/oauth2/token'))).toHaveLength(2);
  });

  it('sends a start command and checks that the machine accepted it', async () => {
    const { washer, calls } = await connectedWasher();
    await washer.startProgram('PROGRAMS.WM_WD.COTTONS', { temp: '60' });

    const sendCall = calls.find((call) => call.url.includes('/commands/v1/send'))!;
    const sentBody = JSON.parse(String(sendCall.init.body));
    expect(sentBody.programName).toBe('PROGRAMS.WM_WD.COTTONS');
    expect(sentBody.parameters.temp).toBe('60');
    expect(sentBody.device.mobileId).toBe('phone');
  });

  it('explains a refused command', async () => {
    const { washer } = await connectedWasher((url) =>
      url.includes('/commands/v1/send') ? { body: { payload: { resultCode: '1' } } } : undefined,
    );
    await expect(washer.stop()).rejects.toThrow(/remote control/);
    await expect(washer.startProgram('PROGRAMS.NOPE', {})).rejects.toBeInstanceOf(WasherCommandError);
  });
});
