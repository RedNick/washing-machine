/**
 * Whether the automatic reports have somewhere to go in this build. The
 * build workflow passes these in from GitHub secrets once the services are
 * set up; until then the Settings switches are shown but can't be turned on.
 */
export const isCrashReportingSetUp = Boolean(process.env.EXPO_PUBLIC_SENTRY_DSN);
export const isWashReportingSetUp = Boolean(process.env.EXPO_PUBLIC_WASH_REPORT_URL);
