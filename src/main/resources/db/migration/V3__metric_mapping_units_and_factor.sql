-- Metric mappings store the full conversion recipe (internal = external * factor + offset) instead of a
-- Conversion enum name. Existing rows are back-filled from their old conversion, then the column is dropped.
ALTER TABLE device_metric_mapping ADD COLUMN external_unit VARCHAR(20);
ALTER TABLE device_metric_mapping ADD COLUMN internal_unit VARCHAR(20);
ALTER TABLE device_metric_mapping ADD COLUMN factor NUMERIC(30, 15);
ALTER TABLE device_metric_mapping ADD COLUMN value_offset NUMERIC(30, 15);

UPDATE device_metric_mapping SET internal_unit = CASE metric
    WHEN 'TEMPERATURE' THEN 'C'
    WHEN 'POWER' THEN 'W'
    WHEN 'ENERGY' THEN 'kWh'
    WHEN 'HUMIDITY' THEN '%'
    WHEN 'RUNTIME' THEN 'min'
    WHEN 'DOOR_OPEN_COUNT' THEN 'count'
    WHEN 'SWITCH' THEN 'on/off'
END;

UPDATE device_metric_mapping SET
    external_unit = CASE conversion
        WHEN 'F_TO_C' THEN 'F'
        WHEN 'K_TO_C' THEN 'K'
        WHEN 'KW_TO_W' THEN 'kW'
        WHEN 'WH_TO_KWH' THEN 'Wh'
        WHEN 'SECONDS_TO_MINUTES' THEN 's'
        WHEN 'HOURS_TO_MINUTES' THEN 'h'
        ELSE internal_unit
    END,
    factor = CASE conversion
        WHEN 'F_TO_C' THEN 0.555555555555556
        WHEN 'KW_TO_W' THEN 1000
        WHEN 'WH_TO_KWH' THEN 0.001
        WHEN 'SECONDS_TO_MINUTES' THEN 0.016666666666667
        WHEN 'HOURS_TO_MINUTES' THEN 60
        ELSE 1
    END,
    value_offset = CASE conversion
        WHEN 'F_TO_C' THEN -17.777777777777778
        WHEN 'K_TO_C' THEN -273.15
        ELSE 0
    END;

ALTER TABLE device_metric_mapping ALTER COLUMN external_unit SET NOT NULL;
ALTER TABLE device_metric_mapping ALTER COLUMN internal_unit SET NOT NULL;
ALTER TABLE device_metric_mapping ALTER COLUMN factor SET NOT NULL;
ALTER TABLE device_metric_mapping ALTER COLUMN value_offset SET NOT NULL;
ALTER TABLE device_metric_mapping DROP COLUMN conversion;
