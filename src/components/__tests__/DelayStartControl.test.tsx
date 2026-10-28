import { fireEvent, render, screen } from '@testing-library/react-native';
import { useState } from 'react';

import { DelayStartControl } from '../DelayStartControl';

const delayOption = {
  kind: 'range' as const,
  key: 'delayTime',
  label: 'Delay start',
  minimum: 0,
  maximum: 1410,
  step: 30,
  defaultValue: 0,
  unit: 'min',
};

/** Holds the delay like the program screen does, and reports each change. */
function DelayStartHarness({ onDelayChange }: { onDelayChange: (delayMinutes: number) => void }) {
  const [delayMinutes, setDelayMinutes] = useState(0);
  return (
    <DelayStartControl
      option={delayOption}
      estimatedMinutes={120}
      delayMinutes={delayMinutes}
      onChange={(newDelay) => {
        setDelayMinutes(newDelay);
        onDelayChange(newDelay);
      }}
    />
  );
}

describe('DelayStartControl', () => {
  it('sets the delay in the machine’s 30-minute steps, for either a start or a finish time', async () => {
    const onDelayChange = jest.fn();
    await render(<DelayStartHarness onDelayChange={onDelayChange} />);
    expect(screen.getByText(/Starts straight away/)).toBeTruthy();

    // Picking a finish time starts from the shortest delay, then + adds a step.
    await fireEvent.press(screen.getByText('Finish by'));
    expect(onDelayChange).toHaveBeenLastCalledWith(30);
    await fireEvent.press(screen.getByLabelText('Later'));
    expect(onDelayChange).toHaveBeenLastCalledWith(60);
    expect(screen.getByText(/so it's done by/)).toBeTruthy();

    // It never goes below one step while a time is picked.
    await fireEvent.press(screen.getByLabelText('Earlier'));
    await fireEvent.press(screen.getByLabelText('Earlier'));
    expect(onDelayChange).toHaveBeenLastCalledWith(30);

    await fireEvent.press(screen.getByText('Start now'));
    expect(onDelayChange).toHaveBeenLastCalledWith(0);
  });

  it('only offers a finish time when the program length is known', async () => {
    await render(<DelayStartControl option={delayOption} estimatedMinutes={null} delayMinutes={0} onChange={jest.fn()} />);
    expect(screen.queryByText('Finish by')).toBeNull();
    expect(screen.getByText('Start at')).toBeTruthy();
  });
});
