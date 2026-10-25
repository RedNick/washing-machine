import { buildLoginPageUrl, isLoginRedirect, parseLoginRedirect } from '../auth/honLogin';

describe('buildLoginPageUrl', () => {
  it('points at the h.on sign-in page with the app’s settings', () => {
    const url = buildLoginPageUrl('11111111-2222-3333-4444-555555555555');
    expect(url.startsWith('https://account2.hon-smarthome.com/services/oauth2/authorize/expid_Login?')).toBe(true);
    expect(url).toContain('response_type=token+id_token');
    expect(url).toContain('redirect_uri=hon%3A%2F%2Fmobilesdk%2Fdetect%2Foauth%2Fdone');
    expect(url).toContain('scope=api%20openid%20refresh_token%20web');
    expect(url).toContain('nonce=11111111-2222-3333-4444-555555555555');
  });

  it('uses a fresh UUID-shaped nonce by default', () => {
    expect(buildLoginPageUrl()).toMatch(/nonce=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  });
});

describe('parseLoginRedirect', () => {
  const redirect =
    'hon://mobilesdk/detect/oauth/done#access_token=00Dabc%21AQ&refresh_token=5Aep861%3D%3D&instance_url=x&id_token=eyJ.payload.sig&issued_at=1';

  it('recognises the redirect', () => {
    expect(isLoginRedirect(redirect)).toBe(true);
    expect(isLoginRedirect('https://account2.hon-smarthome.com/s/login')).toBe(false);
  });

  it('pulls out the three tokens', () => {
    expect(parseLoginRedirect(redirect)).toEqual({
      accessToken: '00Dabc!AQ',
      refreshToken: '5Aep861==',
      idToken: 'eyJ.payload.sig',
    });
  });

  it('returns null when a token is missing', () => {
    expect(parseLoginRedirect('hon://mobilesdk/detect/oauth/done#access_token=a&id_token=b')).toBeNull();
  });
});
