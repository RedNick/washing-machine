import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import type { DiagnosticReport } from './buildDiagnosticReport';

/**
 * Saves the report as a JSON file in the app's cache and opens Android's
 * share sheet, so you choose where it goes (email, Drive, a chat, …).
 * Nothing is sent anywhere unless you pick something there.
 */
export async function shareDiagnosticReport(report: DiagnosticReport): Promise<void> {
  if (!(await Sharing.isAvailableAsync())) {
    throw new Error("This phone can't share files from h.off.");
  }

  const reportFile = new File(Paths.cache, reportFileName(new Date(report.createdAt)));
  reportFile.create({ overwrite: true });
  reportFile.write(JSON.stringify(report, null, 2));

  await Sharing.shareAsync(reportFile.uri, { mimeType: 'application/json', dialogTitle: 'Send h.off report' });
}

/** e.g. "h-off-report-2026-11-04-0930.json". */
export function reportFileName(createdAt: Date): string {
  const twoDigits = (value: number) => String(value).padStart(2, '0');
  const date = `${createdAt.getFullYear()}-${twoDigits(createdAt.getMonth() + 1)}-${twoDigits(createdAt.getDate())}`;
  const time = `${twoDigits(createdAt.getHours())}${twoDigits(createdAt.getMinutes())}`;
  return `h-off-report-${date}-${time}.json`;
}
