import { Slot } from 'expo-router';
import { fireEvent, renderRouter, screen, waitFor } from 'expo-router/testing-library';
import * as Sharing from 'expo-sharing';
import { useEffect } from 'react';

import SettingsScreen from '../app/settings';
import { WasherProvider, useWasher } from '../washer/WasherContext';

jest.mock('expo-sharing', () => ({
  isAvailableAsync: jest.fn(async () => true),
  shareAsync: jest.fn(async () => undefined),
}));

// Stands in for the phone's cache folder, remembering what was written.
const writtenFiles: Record<string, string> = {};
jest.mock('expo-file-system', () => ({
  Paths: { cache: 'file:///cache' },
  File: class {
    uri: string;
    constructor(folder: string, name: string) {
      this.uri = `${folder}/${name}`;
    }
    create() {}
    write(text: string) {
      writtenFiles[this.uri] = text;
    }
  },
}));

/** Connects to the demo machine, then shows the screen under test. */
function DemoMachineLayout() {
  return (
    <WasherProvider>
      <ShowOnceDemoIsConnected />
    </WasherProvider>
  );
}

function ShowOnceDemoIsConnected() {
  const { connection, startDemo } = useWasher();
  useEffect(() => {
    if (connection.kind === 'signedOut') void startDemo();
  }, [connection.kind, startDemo]);
  return connection.kind === 'connected' ? <Slot /> : null;
}

describe('Settings screen', () => {
  it('starts with automatic reports off, and not switchable until they are set up', async () => {
    await renderRouter({ _layout: DemoMachineLayout, index: SettingsScreen }, { initialUrl: '/' });

    const crashReportsSwitch = await screen.findByLabelText('Crash reports');
    expect(crashReportsSwitch.props.value).toBe(false);
    expect(crashReportsSwitch.props.disabled).toBe(true);
    expect(screen.getByLabelText('Wash reports').props.value).toBe(false);
  });

  it('shares a report file', async () => {
    await renderRouter({ _layout: DemoMachineLayout, index: SettingsScreen }, { initialUrl: '/' });

    await fireEvent.press(await screen.findByText('Send a report'));

    await waitFor(() => expect(Sharing.shareAsync).toHaveBeenCalled());
    const [sharedFileUri] = (Sharing.shareAsync as jest.Mock).mock.calls[0];
    expect(sharedFileUri).toMatch(/^file:\/\/\/cache\/h-off-report-.*\.json$/);
    expect(JSON.parse(writtenFiles[sharedFileUri]).reportFormat).toBe(1);
  });
});
