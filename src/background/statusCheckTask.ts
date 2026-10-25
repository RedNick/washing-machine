import * as BackgroundTask from 'expo-background-task';
import * as TaskManager from 'expo-task-manager';

import { HonSession } from '../hon/auth/honSession';
import { HonCloudWasher } from '../hon/honCloudWasher';
import { updateFinishAlert } from '../notifications/finishAlertScheduler';
import { recordCycleIfJustFinished } from '../storage/cycleLog';
import { secureSessionStore } from '../storage/secureSessionStore';

/**
 * While the app is closed, Android wakes it up every 15 minutes or so (it
 * decides exactly when) to check the machine. That keeps the "wash finished"
 * alert accurate if the time left changes, and catches washes that ended
 * early.
 */
const STATUS_CHECK_TASK_NAME = 'washer-status-check';

/** Android won't run background tasks more often than this. */
const CHECK_EVERY_MINUTES = 15;

// Tasks must be defined when the JavaScript first loads, not inside a screen.
TaskManager.defineTask(STATUS_CHECK_TASK_NAME, async () => {
  try {
    const session = await HonSession.restore(secureSessionStore);
    // Not signed in (or using the demo machine): nothing to check.
    if (!session) return BackgroundTask.BackgroundTaskResult.Success;

    const washer = await HonCloudWasher.connect(session);
    const status = await washer.getStatusWithoutProgramName();
    await updateFinishAlert(status);
    await recordCycleIfJustFinished(status);
    return BackgroundTask.BackgroundTaskResult.Success;
  } catch (error) {
    console.warn('Background status check failed', error);
    return BackgroundTask.BackgroundTaskResult.Failed;
  }
});

/** Turns on the background check. Safe to call more than once. */
export async function startBackgroundStatusChecks(): Promise<void> {
  const status = await BackgroundTask.getStatusAsync();
  if (status !== BackgroundTask.BackgroundTaskStatus.Available) return;
  await BackgroundTask.registerTaskAsync(STATUS_CHECK_TASK_NAME, { minimumInterval: CHECK_EVERY_MINUTES });
}

export async function stopBackgroundStatusChecks(): Promise<void> {
  if (await TaskManager.isTaskRegisteredAsync(STATUS_CHECK_TASK_NAME)) {
    await BackgroundTask.unregisterTaskAsync(STATUS_CHECK_TASK_NAME);
  }
}
