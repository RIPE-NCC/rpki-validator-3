CREATE TABLE rpki_object (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    serial_number DECIMAL(40, 0) NOT NULL,
    sha256 BINARY(32) NOT NULL,
    encoded BINARY,
    CONSTRAINT rpki_object__pk PRIMARY KEY (id)
);

CREATE TABLE rpki_object_locations (
    rpki_object_id BIGINT NOT NULL,
    locations VARCHAR(16000) NOT NULL,
    CONSTRAINT rpki_object_locations__pk PRIMARY KEY (rpki_object_id, locations),
    CONSTRAINT rpki_object_locations__rpki_object_fk FOREIGN KEY (rpki_object_id) REFERENCES rpki_object (id) ON DELETE CASCADE
);

CREATE TABLE trust_anchor (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    name VARCHAR_IGNORECASE(1000) NOT NULL,
    subject_public_key_info VARCHAR(2000) NOT NULL,
    certificate BINARY,
    CONSTRAINT trust_anchor__pk PRIMARY KEY (id)
);
CREATE INDEX trust_anchor__name ON trust_anchor (name ASC);

CREATE TABLE trust_anchor_locations (
    trust_anchor_id BIGINT NOT NULL,
    locations_order INT NOT NULL,
    locations VARCHAR(16000) NOT NULL,
    CONSTRAINT trust_anchor_locations__pk PRIMARY KEY (trust_anchor_id, locations_order),
    CONSTRAINT trust_anchor_locations__trust_anchor_fk FOREIGN KEY (trust_anchor_id) REFERENCES trust_anchor (id) ON DELETE CASCADE
);

CREATE TABLE validation_run (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    trust_anchor_id BIGINT NOT NULL,
    trust_anchor_certificate_uri VARCHAR(16000) NOT NULL,
    status VARCHAR NOT NULL,
    CONSTRAINT validation_run__pk PRIMARY KEY (id),
    CONSTRAINT validation_run__trust_anchor_fk FOREIGN KEY (trust_anchor_id) REFERENCES trust_anchor (id) ON DELETE RESTRICT
);
CREATE INDEX validation_run__trust_anchor_id_idx ON validation_run (trust_anchor_id ASC, created_at DESC);

CREATE TABLE validation_check (
    id BIGINT NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    validation_run_id BIGINT NOT NULL,
    rpki_object_id BIGINT,
    location VARCHAR(16000) NOT NULL,
    status VARCHAR NOT NULL,
    key VARCHAR NOT NULL,
    CONSTRAINT validation_check__pk PRIMARY KEY (id),
    CONSTRAINT validation_check__validation_run_fk FOREIGN KEY (validation_run_id) REFERENCES validation_run (id) ON DELETE CASCADE,
    CONSTRAINT validation_check__rpki_object_fk FOREIGN KEY (rpki_object_id) REFERENCES rpki_object (id) ON DELETE SET NULL
);
CREATE INDEX validation_check__validation_run_id_idx ON validation_check (validation_run_id ASC, id ASC);

CREATE TABLE validation_check_parameters (
    validation_check_id BIGINT NOT NULL,
    parameters_order INT NOT NULL,
    parameters VARCHAR NOT NULL,
    CONSTRAINT validation_check_parameters__pk PRIMARY KEY (validation_check_id, parameters_order),
    CONSTRAINT validation_check_parameters__validation_check_fk FOREIGN KEY (validation_check_id) REFERENCES validation_check (id) ON DELETE CASCADE
);
