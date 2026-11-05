import { checkWashReport } from './checkWashReport';

/**
 * Receives the optional "wash reports" from h.off and stores them in a D1
 * database. It only accepts new reports: there is no way to read them back
 * over the web. Reports are read with the "Export wash reports" GitHub
 * workflow, which uses the Cloudflare API, and are deleted after
 * KEEP_REPORTS_FOR_DAYS by a daily scheduled run.
 */

interface Env {
  WASH_REPORTS: D1Database;
}

const KEEP_REPORTS_FOR_DAYS = 180;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname !== '/reports') return new Response('Not found', { status: 404 });
    if (request.method !== 'POST')
      return new Response('Method not allowed', { status: 405, headers: { Allow: 'POST' } });

    const result = checkWashReport(await request.text());
    if (!result.ok) return new Response(result.reason, { status: 400 });

    const { report } = result;
    await env.WASH_REPORTS.prepare(
      'INSERT INTO wash_reports (received_at, app_version, model_name, program_name, finished_at, report_json) VALUES (?, ?, ?, ?, ?, ?)',
    )
      .bind(
        new Date().toISOString(),
        report.appVersion,
        report.modelName,
        report.programName,
        report.finishedAt,
        report.reportJson,
      )
      .run();
    return new Response(null, { status: 204 });
  },

  /** Daily: deletes reports older than KEEP_REPORTS_FOR_DAYS. */
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    const oldestKept = new Date(Date.now() - KEEP_REPORTS_FOR_DAYS * 24 * 60 * 60 * 1000).toISOString();
    await env.WASH_REPORTS.prepare('DELETE FROM wash_reports WHERE received_at < ?').bind(oldestKept).run();
  },
} satisfies ExportedHandler<Env>;
