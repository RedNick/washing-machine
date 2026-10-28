import { redirectSystemPath } from '../app/+native-intent';

describe('redirectSystemPath', () => {
  it('leaves the "signed in" link to the sign-in screen', () => {
    const signedInLink = 'hon://mobilesdk/detect/oauth/done#access_token=a&refresh_token=b&id_token=c';
    expect(redirectSystemPath({ path: signedInLink, initial: false })).toBeNull();
    expect(redirectSystemPath({ path: signedInLink, initial: true })).toBeNull();
  });

  it('passes other links through unchanged', () => {
    expect(redirectSystemPath({ path: 'washer://programs', initial: true })).toBe('washer://programs');
  });
});
