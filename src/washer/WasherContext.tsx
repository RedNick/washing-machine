import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';

import { startBackgroundStatusChecks, stopBackgroundStatusChecks } from '../background/statusCheckTask';
import type { HonLoginTokens } from '../hon/auth/honLogin';
import { HonAuthError, HonSession } from '../hon/auth/honSession';
import { HonCloudWasher, NoWasherFoundError } from '../hon/honCloudWasher';
import { STATUS_POLL_WHILE_IDLE_MS, STATUS_POLL_WHILE_RUNNING_MS } from '../hon/honConstants';
import { prepareNotifications, resetFinishAlert, updateFinishAlert } from '../notifications/finishAlertScheduler';
import { clearCycleLog, recordCycleIfJustFinished } from '../storage/cycleLog';
import { secureSessionStore } from '../storage/secureSessionStore';
import { DemoWasher } from './demoWasher';
import type { MachineInfo, WasherService, WasherStatus } from './washerTypes';

/**
 * Holds the connection to the washing machine for the whole app: who is
 * signed in, the latest status, and how often to refresh it.
 */

type ConnectionState =
  | { kind: 'starting' }
  | { kind: 'signedOut'; message: string | null }
  /** We have a saved sign-in but couldn't reach h.on (e.g. no internet). */
  | { kind: 'unreachable'; message: string }
  | { kind: 'connected'; washer: WasherService; machineInfo: MachineInfo };

interface WasherContextValue {
  connection: ConnectionState;
  status: WasherStatus | null;
  /** Error from the latest status refresh, shown on the status screen. */
  statusError: string | null;
  isRefreshing: boolean;
  refreshStatus: () => Promise<void>;
  signInWithTokens: (tokens: HonLoginTokens) => Promise<void>;
  startDemo: () => Promise<void>;
  signOut: () => Promise<void>;
  retryConnection: () => Promise<void>;
}

const WasherContext = createContext<WasherContextValue | null>(null);

function describeError(error: unknown): string {
  if (error instanceof Error) return error.message;
  return 'Something went wrong. Please try again.';
}

export function WasherProvider({ children }: { children: ReactNode }) {
  const [connection, setConnection] = useState<ConnectionState>({ kind: 'starting' });
  const [status, setStatus] = useState<WasherStatus | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [appIsInForeground, setAppIsInForeground] = useState(AppState.currentState === 'active');

  const washer = connection.kind === 'connected' ? connection.washer : null;
  // Lets refreshStatus ignore answers that arrive after a sign-out.
  const activeWasherRef = useRef<WasherService | null>(null);
  activeWasherRef.current = washer;

  const connectWithSession = useCallback(async (session: HonSession) => {
    const cloudWasher = await HonCloudWasher.connect(session);
    const machineInfo = await cloudWasher.getMachineInfo();
    setConnection({ kind: 'connected', washer: cloudWasher, machineInfo });
    await prepareNotifications();
    await startBackgroundStatusChecks();
  }, []);

  /** On launch, pick up the saved sign-in if there is one. */
  const restoreSavedSignIn = useCallback(async () => {
    setConnection({ kind: 'starting' });
    try {
      const session = await HonSession.restore(secureSessionStore);
      if (!session) {
        setConnection({ kind: 'signedOut', message: null });
        return;
      }
      await connectWithSession(session);
    } catch (error) {
      if (error instanceof HonAuthError) {
        await secureSessionStore.clear();
        setConnection({ kind: 'signedOut', message: 'Your h.on sign-in expired. Please sign in again.' });
      } else {
        setConnection({ kind: 'unreachable', message: describeError(error) });
      }
    }
  }, [connectWithSession]);

  useEffect(() => {
    void restoreSavedSignIn();
  }, [restoreSavedSignIn]);

  const signInWithTokens = useCallback(
    async (tokens: HonLoginTokens) => {
      try {
        const session = await HonSession.fromLogin(tokens, secureSessionStore);
        await connectWithSession(session);
      } catch (error) {
        const message =
          error instanceof NoWasherFoundError
            ? 'Signed in, but there is no washing machine on this h.on account.'
            : describeError(error);
        await secureSessionStore.clear();
        setConnection({ kind: 'signedOut', message });
      }
    },
    [connectWithSession],
  );

  const startDemo = useCallback(async () => {
    const demoWasher = new DemoWasher();
    setConnection({ kind: 'connected', washer: demoWasher, machineInfo: await demoWasher.getMachineInfo() });
    await prepareNotifications();
  }, []);

  const signOut = useCallback(async () => {
    const wasDemo = washer?.isDemo ?? false;
    setConnection({ kind: 'signedOut', message: null });
    setStatus(null);
    setStatusError(null);
    await stopBackgroundStatusChecks();
    await resetFinishAlert();
    if (wasDemo) {
      await clearCycleLog();
    } else {
      await secureSessionStore.clear();
    }
  }, [washer]);

  const refreshStatus = useCallback(async () => {
    const washerAtStart = activeWasherRef.current;
    if (!washerAtStart) return;
    setIsRefreshing(true);
    try {
      const latestStatus = await washerAtStart.getStatus();
      if (activeWasherRef.current !== washerAtStart) return;
      setStatus(latestStatus);
      setStatusError(null);
      await updateFinishAlert(latestStatus);
      await recordCycleIfJustFinished(latestStatus);
    } catch (error) {
      if (activeWasherRef.current !== washerAtStart) return;
      if (error instanceof HonAuthError) {
        await secureSessionStore.clear();
        setConnection({ kind: 'signedOut', message: 'Your h.on sign-in expired. Please sign in again.' });
        return;
      }
      setStatusError(describeError(error));
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  // Track whether the app is on screen; we only poll while it is.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      setAppIsInForeground(nextState === 'active');
    });
    return () => subscription.remove();
  }, []);

  // Poll faster while a wash is running, slower otherwise.
  const pollIntervalMs =
    status?.state === 'running' || status?.state === 'paused' || status?.state === 'scheduled'
      ? STATUS_POLL_WHILE_RUNNING_MS
      : STATUS_POLL_WHILE_IDLE_MS;
  // The demo runs at high speed, so refresh it every few seconds.
  const effectivePollIntervalMs = washer?.isDemo ? 3000 : pollIntervalMs;

  useEffect(() => {
    if (!washer || !appIsInForeground) return;
    void refreshStatus();
    const timer = setInterval(() => void refreshStatus(), effectivePollIntervalMs);
    return () => clearInterval(timer);
  }, [washer, appIsInForeground, effectivePollIntervalMs, refreshStatus]);

  const value = useMemo<WasherContextValue>(
    () => ({
      connection,
      status,
      statusError,
      isRefreshing,
      refreshStatus,
      signInWithTokens,
      startDemo,
      signOut,
      retryConnection: restoreSavedSignIn,
    }),
    [connection, status, statusError, isRefreshing, refreshStatus, signInWithTokens, startDemo, signOut, restoreSavedSignIn],
  );

  return <WasherContext.Provider value={value}>{children}</WasherContext.Provider>;
}

export function useWasher(): WasherContextValue {
  const context = useContext(WasherContext);
  if (!context) throw new Error('useWasher must be used inside <WasherProvider>.');
  return context;
}

/** The connected washer, for screens that only show once connected. */
export function useConnectedWasher(): { washer: WasherService; machineInfo: MachineInfo } {
  const { connection } = useWasher();
  if (connection.kind !== 'connected') throw new Error('No washing machine connected yet.');
  return { washer: connection.washer, machineInfo: connection.machineInfo };
}
