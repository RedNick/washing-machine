-- One row per wash report sent by h.off. report_json holds the whole
-- report; the other columns are copied out of it for easy querying.
CREATE TABLE wash_reports (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  received_at TEXT NOT NULL,
  app_version TEXT NOT NULL,
  model_name TEXT NOT NULL,
  program_name TEXT,
  finished_at TEXT NOT NULL,
  report_json TEXT NOT NULL
);
CREATE INDEX wash_reports_received_at ON wash_reports (received_at);
