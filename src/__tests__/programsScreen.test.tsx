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

    // Grouped, with no favourites yet. Each group has a heading and a jump button.
    expect(await screen.findByLabelText('Jump to Everyday')).toBeTruthy();
    expect(screen.queryByLabelText('Jump to Favourites')).toBeNull();

    await fireEvent.press(screen.getByLabelText('Add Care 14 to favourites'));
    expect(await screen.findByLabelText('Jump to Favourites')).toBeTruthy();

    await fireEvent.press(screen.getByText('Edit list'));
    await fireEvent.press((await screen.findAllByText('Hide'))[0]);
    await fireEvent.press(await screen.findByText('Done'));
    expect(await screen.findByText('1 hidden program · Show')).toBeTruthy();
  });

  it('finds programs by name', async () => {
    await renderRouter({ _layout: DemoMachineLayout, index: ProgramsScreen }, { initialUrl: '/' });

    await fireEvent.changeText(await screen.findByLabelText('Search programs'), 'eco');
    expect(await screen.findByText('Matching programs')).toBeTruthy();
    expect(screen.getByText('Eco 40-60')).toBeTruthy();
    expect(screen.queryByText('Cottons')).toBeNull();
    expect(screen.queryByLabelText('Jump to Everyday')).toBeNull();

    await fireEvent.changeText(screen.getByLabelText('Search programs'), 'zzz');
    expect(await screen.findByText('No programs match "zzz".')).toBeTruthy();
  });
});
