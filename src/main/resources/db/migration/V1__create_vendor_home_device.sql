-- Vendors, homes, the supported-device catalogue (with metric mappings) and devices registered in homes. Portable across PostgreSQL and H2.

CREATE TABLE vendor (
    id         UUID                     NOT NULL,
    code       VARCHAR(20)              NOT NULL,
    name       VARCHAR(100)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT                   NOT NULL,
    CONSTRAINT pk_vendor PRIMARY KEY (id),
    CONSTRAINT uk_vendor_code UNIQUE (code)
);

CREATE TABLE home (
    id         UUID                     NOT NULL,
    name       VARCHAR(100)             NOT NULL,
    timezone   VARCHAR(64)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT                   NOT NULL,
    CONSTRAINT pk_home PRIMARY KEY (id)
);

-- Admin-managed catalogue of supported device models. One vendor has many devices.
CREATE TABLE device (
    id          UUID                     NOT NULL,
    vendor_id   UUID                     NOT NULL,
    device_type VARCHAR(20)              NOT NULL,
    model       VARCHAR(100)             NOT NULL,
    name        VARCHAR(100)             NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    version     BIGINT                   NOT NULL,
    CONSTRAINT pk_device PRIMARY KEY (id),
    CONSTRAINT fk_device_vendor FOREIGN KEY (vendor_id) REFERENCES vendor (id),
    CONSTRAINT uk_device_vendor_model UNIQUE (vendor_id, model)
);

-- How a catalogue device's vendor metrics map to canonical metrics (value objects owned by the device).
CREATE TABLE device_metric_mapping (
    device_id       UUID         NOT NULL,
    external_metric VARCHAR(100) NOT NULL,
    metric          VARCHAR(30)  NOT NULL,
    conversion      VARCHAR(30)  NOT NULL,
    CONSTRAINT pk_device_metric_mapping PRIMARY KEY (device_id, external_metric),
    CONSTRAINT fk_device_metric_mapping_device FOREIGN KEY (device_id) REFERENCES device (id) ON DELETE CASCADE
);

-- A physical appliance a user registered in a home. One home has many home devices;
-- one catalogue device has many home devices (home_device owns both foreign keys).
CREATE TABLE home_device (
    id                       UUID                     NOT NULL,
    home_id                  UUID                     NOT NULL,
    device_id                UUID                     NOT NULL,
    external_device_id       VARCHAR(100)             NOT NULL,
    name                     VARCHAR(100)             NOT NULL,
    polling_interval_seconds INTEGER                  NOT NULL,
    enabled                  BOOLEAN                  NOT NULL,
    next_poll_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    last_polled_at           TIMESTAMP WITH TIME ZONE,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    version                  BIGINT                   NOT NULL,
    CONSTRAINT pk_home_device PRIMARY KEY (id),
    CONSTRAINT fk_home_device_home FOREIGN KEY (home_id) REFERENCES home (id),
    CONSTRAINT fk_home_device_device FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT uk_home_device_device_external_id UNIQUE (device_id, external_device_id)
);

-- Foreign keys are not indexed automatically in PostgreSQL (vendor_id and device_id lead a unique index already).
CREATE INDEX ix_home_device_home ON home_device (home_id);
-- Collection scheduler: "enabled home devices whose next poll is due".
CREATE INDEX ix_home_device_due ON home_device (enabled, next_poll_at);
