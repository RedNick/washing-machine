import * as Sentry from '@sentry/react-native';
import Constants from 'expo-constants';

import { loadDiagnosticsSettings } from '../storage/diagnosticsSettings';
import { redactPrivateDetails } from './redactPrivateDetails';
import { isCrashReportingSetUp } from './reportingServices';

/**
 * Crash reports, sent to Sentry (sentry.io) only when you've switched
 * "Crash reports" on in Settings. Until then Sentry is never started, so
 * nothing at all is sent.
 *
 * Reports carry the error, where in the app it happened and what the app
 * did just before. Screenshots, the screen layout, session tracking and
 * performance tracing are all off, and every report goes through the same
 * private-detail removal as "Send a report" before it leaves the phone.
 */

let isCrashReportingRunning = false;

/** Called once when the app opens: starts crash reporting if you've switched it on. */
export async function startCrashReportingIfAllowed(): Promise<void> {
  const settings = await loadDiagnosticsSettings();
  setCrashReportingEnabled(settings.sendCrashReports);
}

/** Starts or stops crash reporting straight away, e.g. when the Settings switch changes. */
export function setCrashReportingEnabled(isOn: boolean): void {
  const shouldRun = isOn && isCrashReportingSetUp;
  if (shouldRun && !isCrashReportingRunning) {
    Sentry.init({
      dsn: process.env.EXPO_PUBLIC_SENTRY_DSN,
      release: describeRelease(),
      sendDefaultPii: false,
      attachScreenshot: false,
      attachViewHierarchy: false,
      enableAutoSessionTracking: false,
      // Only crashes and errors, no performance data.
      tracesSampleRate: 0,
      beforeSend: (event) => redactPrivateDetails(event),
      beforeBreadcrumb: (breadcrumb) => redactPrivateDetails(breadcrumb),
    });
    isCrashReportingRunning = true;
  } else if (!shouldRun && isCrashReportingRunning) {
    void Sentry.close();
    isCrashReportingRunning = false;
  }
}

/** e.g. "uk.rdfn.h.off@1.0.0+44", so reports can be matched to a build. */
function describeRelease(): string {
  const versionName = Constants.expoConfig?.version ?? 'unknown';
  const buildNumber = process.env.EXPO_PUBLIC_BUILD_NUMBER ?? 'local';
  return `uk.rdfn.h.off@${versionName}+${buildNumber}`;
}
