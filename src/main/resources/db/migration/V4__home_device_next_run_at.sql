-- Rename the scheduling columns of home_device: next_poll_at -> next_run_at, last_polled_at -> last_run_at.
-- Done as add/copy/drop so it runs on both PostgreSQL and H2.
DROP INDEX ix_home_device_due;

ALTER TABLE home_device ADD COLUMN next_run_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE home_device ADD COLUMN last_run_at TIMESTAMP WITH TIME ZONE;
UPDATE home_device SET next_run_at = next_poll_at, last_run_at = last_polled_at;
ALTER TABLE home_device ALTER COLUMN next_run_at SET NOT NULL;
ALTER TABLE home_device DROP COLUMN next_poll_at;
ALTER TABLE home_device DROP COLUMN last_polled_at;

-- Collection scheduler: "enabled home devices whose next run is due", oldest first.
CREATE INDEX ix_home_device_due ON home_device (enabled, next_run_at);
