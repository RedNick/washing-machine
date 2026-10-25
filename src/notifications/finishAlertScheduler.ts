import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import type { WasherStatus } from '../washer/washerTypes';
import {
  EMPTY_FINISH_ALERT_MEMORY,
  planFinishAlert,
  type FinishAlertMemory,
  type FinishAlertStep,
} from './planFinishAlert';

/** Matches the defaultChannel set for expo-notifications in app.json. */
export const WASH_FINISHED_CHANNEL_ID = 'wash-finished';
const MEMORY_STORAGE_KEY = 'finishAlert.memory';

/** Show our alerts even when the app is open. */
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

/**
 * Creates the Android notification channel and asks for permission.
 * Returns false if the user said no.
 */
export async function prepareNotifications(): Promise<boolean> {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(WASH_FINISHED_CHANNEL_ID, {
      name: 'Wash finished',
      description: 'Tells you when the washing machine is done',
      importance: Notifications.AndroidImportance.HIGH,
    });
  }
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;
  const requested = await Notifications.requestPermissionsAsync();
  return requested.granted;
}

async function loadMemory(): Promise<FinishAlertMemory> {
  try {
    const saved = await AsyncStorage.getItem(MEMORY_STORAGE_KEY);
    return saved ? { ...EMPTY_FINISH_ALERT_MEMORY, ...JSON.parse(saved) } : EMPTY_FINISH_ALERT_MEMORY;
  } catch {
    return EMPTY_FINISH_ALERT_MEMORY;
  }
}

function finishedMessage(programName: string | null): { title: string; body: string } {
  return {
    title: 'Washing is done',
    body: programName ? `${programName} has finished. Time to unload.` : 'Time to unload the machine.',
  };
}

/** Carries out one step from the planner, returning a new notification id if any. */
async function performStep(step: FinishAlertStep): Promise<string | null> {
  switch (step.kind) {
    case 'cancelScheduled':
      await Notifications.cancelScheduledNotificationAsync(step.notificationId);
      return null;
    case 'schedule':
      return Notifications.scheduleNotificationAsync({
        content: finishedMessage(step.programName),
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.DATE,
          date: new Date(step.fireAtMs),
          channelId: WASH_FINISHED_CHANNEL_ID,
        },
      });
    case 'showNow': {
      const content =
        step.reason === 'finished'
          ? finishedMessage(step.programName)
          : {
              title: 'Washing machine problem',
              body: step.errorCode
                ? `The machine reports error ${step.errorCode}.`
                : 'The machine reports an error.',
            };
      await Notifications.scheduleNotificationAsync({ content, trigger: null });
      return null;
    }
  }
}

/**
 * Call with every new status (from the open app or the background check)
 * to keep the "wash finished" alert in step with the machine.
 */
export async function updateFinishAlert(status: WasherStatus, nowMs: number = Date.now()): Promise<void> {
  const memory = await loadMemory();
  const { steps, nextMemory } = planFinishAlert(memory, status, nowMs);

  for (const step of steps) {
    const newNotificationId = await performStep(step);
    if (step.kind === 'schedule') nextMemory.scheduledNotificationId = newNotificationId;
  }
  await AsyncStorage.setItem(MEMORY_STORAGE_KEY, JSON.stringify(nextMemory));
}

/** Clears any pending alert, e.g. when signing out. */
export async function resetFinishAlert(): Promise<void> {
  const memory = await loadMemory();
  if (memory.scheduledNotificationId) {
    await Notifications.cancelScheduledNotificationAsync(memory.scheduledNotificationId);
  }
  await AsyncStorage.removeItem(MEMORY_STORAGE_KEY);
}
