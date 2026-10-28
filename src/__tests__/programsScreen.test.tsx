import { Slot } from 'expo-router';
import { fireEvent, renderRouter, screen } from 'expo-router/testing-library';
import { useEffect } from 'react';

import ProgramsScreen from '../app/(tabs)/programs';
import { WasherProvider, useWasher } from '../washer/WasherContext';

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

describe('Programs screen', () => {
  it('lets you star and hide programs', async () => {
    await renderRouter({ _layout: DemoMachineLayout, index: ProgramsScreen }, { initialUrl: '/' });

    // Grouped, with no favourites yet.
    expect(await screen.findByText('Everyday')).toBeTruthy();
    expect(screen.queryByText('Favourites')).toBeNull();

    await fireEvent.press(screen.getByLabelText('Add Care 14 to favourites'));
    expect(await screen.findByText('Favourites')).toBeTruthy();

    await fireEvent.press(screen.getByText('Edit list'));
    await fireEvent.press((await screen.findAllByText('Hide'))[0]);
    await fireEvent.press(await screen.findByText('Done'));
    expect(await screen.findByText('1 hidden program · Show')).toBeTruthy();
  });
});
