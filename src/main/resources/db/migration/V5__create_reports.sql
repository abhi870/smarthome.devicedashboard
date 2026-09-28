-- Reports: DAILY (one per home device and home-local day, by the daily job) and CUSTOM_RANGE (on request).
CREATE TABLE report (
    id              UUID                     NOT NULL,
    home_device_id  UUID                     NOT NULL,
    type            VARCHAR(20)              NOT NULL, -- DAILY | CUSTOM_RANGE
    local_date      DATE,                              -- DAILY only (home-local day)
    period_start    TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end      TIMESTAMP WITH TIME ZONE NOT NULL, -- exclusive
    timezone        VARCHAR(64)              NOT NULL, -- snapshot of home.timezone
    metrics         VARCHAR(255)             NOT NULL, -- comma-separated metric names
    status          VARCHAR(20)              NOT NULL, -- QUEUED | RUNNING | SUCCEEDED | FAILED
    attempts        INTEGER                  NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    error           VARCHAR(1000),
    coverage_pct    DOUBLE PRECISION,
    requested_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    version         BIGINT                   NOT NULL,
    CONSTRAINT pk_report PRIMARY KEY (id),
    CONSTRAINT fk_report_home_device FOREIGN KEY (home_device_id) REFERENCES home_device (id),
    -- At most one DAILY report per device and day (local_date is NULL for CUSTOM_RANGE, and NULLs never collide):
    -- makes the daily job idempotent. Also serves the job's "which days are done" lookup.
    CONSTRAINT uk_report_daily_device_date UNIQUE (home_device_id, local_date)
);

-- "Reports of this device in a period", newest first.
CREATE INDEX ix_report_device_period ON report (home_device_id, period_start DESC);

CREATE TABLE report_metric_summary (
    report_id    UUID             NOT NULL,
    metric       VARCHAR(30)      NOT NULL,
    unit         VARCHAR(20)      NOT NULL,
    sample_count BIGINT           NOT NULL,
    min_value    DOUBLE PRECISION,
    max_value    DOUBLE PRECISION,
    avg_value    DOUBLE PRECISION,           -- time-weighted
    total_value  DOUBLE PRECISION,           -- kWh / on-minutes / counter increase; NULL for gauges
    coverage_pct DOUBLE PRECISION NOT NULL,
    CONSTRAINT pk_report_metric_summary PRIMARY KEY (report_id, metric),
    CONSTRAINT fk_report_metric_summary_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE CASCADE
);

-- Daily breakdown of CUSTOM_RANGE reports.
CREATE TABLE report_daily_value (
    report_id   UUID        NOT NULL,
    metric      VARCHAR(30) NOT NULL,
    local_date  DATE        NOT NULL,
    daily_value DOUBLE PRECISION,
    CONSTRAINT pk_report_daily_value PRIMARY KEY (report_id, metric, local_date),
    CONSTRAINT fk_report_daily_value_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE CASCADE
);

-- Read model: statistics per home device, home-local day and metric, written with the DAILY report.
CREATE TABLE daily_metric_rollup (
    id                  UUID                     NOT NULL,
    home_device_id      UUID                     NOT NULL,
    metric              VARCHAR(30)              NOT NULL,
    local_date          DATE                     NOT NULL,
    sample_count        BIGINT                   NOT NULL,
    min_value           DOUBLE PRECISION,
    max_value           DOUBLE PRECISION,
    avg_value           DOUBLE PRECISION,
    first_reading_value DOUBLE PRECISION,
    first_reading_time  TIMESTAMP WITH TIME ZONE,
    last_reading_value  DOUBLE PRECISION,
    last_reading_time   TIMESTAMP WITH TIME ZONE,
    total_value         DOUBLE PRECISION,
    covered_seconds     BIGINT                   NOT NULL,
    period_seconds      BIGINT                   NOT NULL,
    coverage_pct        DOUBLE PRECISION         NOT NULL,
    computed_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_daily_metric_rollup PRIMARY KEY (id),
    CONSTRAINT fk_daily_metric_rollup_home_device FOREIGN KEY (home_device_id) REFERENCES home_device (id),
    -- One row per device, day and metric; serves "rollups of this device between two days".
    CONSTRAINT uk_daily_metric_rollup UNIQUE (home_device_id, local_date, metric)
);
