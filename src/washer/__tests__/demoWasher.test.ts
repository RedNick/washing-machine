import { DemoWasher } from '../demoWasher';
import { WasherCommandError } from '../washerTypes';

/** A clock the test can move forward. */
function createClock() {
  let currentMs = Date.UTC(2026, 9, 25, 9, 0, 0);
  return {
    now: () => currentMs,
    advanceSeconds: (seconds: number) => {
      currentMs += seconds * 1000;
    },
  };
}

describe('DemoWasher', () => {
  it('runs a program from start to finish, one machine minute per real second', async () => {
    const clock = createClock();
    const washer = new DemoWasher(clock.now);

    await washer.startProgram('PROGRAMS.WM_WD.CARE_14', { temp: '30' });
    let status = await washer.getStatus();
    expect(status).toMatchObject({ state: 'running', programName: 'Care 14', remainingMinutes: 14, temperatureCelsius: 30 });

    clock.advanceSeconds(10);
    status = await washer.getStatus();
    expect(status.remainingMinutes).toBe(4);

    clock.advanceSeconds(5);
    status = await washer.getStatus();
    expect(status).toMatchObject({ state: 'finished', programName: 'Care 14', remainingMinutes: 0 });

    const usage = await washer.getUsageSummary();
    expect(usage.totalCycles).toBe(46);
  });

  it('stands still while paused', async () => {
    const clock = createClock();
    const washer = new DemoWasher(clock.now);
    await washer.startProgram('PROGRAMS.WM_WD.RINSE', {});
    clock.advanceSeconds(5);
    await washer.pause();
    clock.advanceSeconds(100);
    expect(await washer.getStatus()).toMatchObject({ state: 'paused', remainingMinutes: 20 });

    await washer.resume();
    clock.advanceSeconds(5);
    expect((await washer.getStatus()).remainingMinutes).toBe(15);
  });

  it('refuses a second program while one is running', async () => {
    const washer = new DemoWasher(createClock().now);
    await washer.startProgram('PROGRAMS.WM_WD.RINSE', {});
    await expect(washer.startProgram('PROGRAMS.WM_WD.COTTONS', {})).rejects.toBeInstanceOf(WasherCommandError);
  });

  it('records the commands it was sent, newest first', async () => {
    const washer = new DemoWasher(createClock().now);
    await washer.startProgram('PROGRAMS.WM_WD.RINSE', {});
    await washer.stop();
    const commands = await washer.getRecentCommands();
    expect(commands.map((command) => command.commandName)).toEqual(['stopProgram', 'startProgram']);
    expect((await washer.getStatus()).state).toBe('idle');
  });
});
