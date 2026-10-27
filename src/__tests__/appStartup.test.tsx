import { renderRouter, screen, waitFor } from 'expo-router/testing-library';

/**
 * Starts the whole app (every screen in src/app) the way the phone does, to
 * catch errors that would crash it on launch.
 *
 * This file lives outside src/app because every file in there becomes a screen.
 */
describe('app startup', () => {
  it('opens on the sign-in screen when nobody is signed in', async () => {
    await renderRouter('./src/app', { initialUrl: '/' });
    await waitFor(() => expect(screen.getByText('Try the demo machine')).toBeTruthy());
  });
});
