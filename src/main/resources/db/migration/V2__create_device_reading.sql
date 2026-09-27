-- Readings of home devices: one value per (home device, metric, time). Append-only.
CREATE TABLE device_reading (
    id             UUID                     NOT NULL,
    home_device_id UUID                     NOT NULL,
    metric         VARCHAR(30)              NOT NULL,
    reading_time   TIMESTAMP WITH TIME ZONE NOT NULL,
    reading_value  VARCHAR(100)             NOT NULL, -- '1150.5' or 'ON'; CAST(... AS DOUBLE PRECISION) for numeric metrics
    unit           VARCHAR(20)              NOT NULL,
    collected_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_device_reading PRIMARY KEY (id),
    CONSTRAINT fk_device_reading_home_device FOREIGN KEY (home_device_id) REFERENCES home_device (id)
);

-- Serves "readings of this device and metric, newest first" (reports) and makes collection idempotent.
CREATE UNIQUE INDEX uk_device_reading_device_metric_time ON device_reading (home_device_id, metric, reading_time DESC);
-- Serves "all metrics of this device, newest first".
CREATE INDEX ix_device_reading_device_time ON device_reading (home_device_id, reading_time DESC);
