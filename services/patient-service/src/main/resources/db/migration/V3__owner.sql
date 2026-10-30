CREATE TABLE owner (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_owner_user UNIQUE (user_id),
    CONSTRAINT ck_owner_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_owner_phone_not_blank CHECK (CHAR_LENGTH(TRIM(phone)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pet (
    id BINARY(16) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    name VARCHAR(50) NOT NULL,
    species VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    weight_kg DECIMAL(5, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_pet_owner FOREIGN KEY (owner_id) REFERENCES owner (id) ON DELETE CASCADE,
    CONSTRAINT ck_pet_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_pet_species_not_blank CHECK (CHAR_LENGTH(TRIM(species)) > 0),
    CONSTRAINT ck_pet_weight_positive CHECK (weight_kg > 0 AND weight_kg <= 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_pet_owner_id ON pet (owner_id);

CREATE TABLE vaccination (
    id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    vaccine_name VARCHAR(100) NOT NULL,
    administered_on DATE NOT NULL,
    next_due_on DATE,
    note VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_vaccination_pet FOREIGN KEY (pet_id) REFERENCES pet (id) ON DELETE CASCADE,
    CONSTRAINT ck_vaccination_name_not_blank CHECK (CHAR_LENGTH(TRIM(vaccine_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_vaccination_pet_id ON vaccination (pet_id);

CREATE TABLE allergy (
    id BINARY(16) NOT NULL,
    pet_id BINARY(16) NOT NULL,
    allergen VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    note VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_allergy_pet FOREIGN KEY (pet_id) REFERENCES pet (id) ON DELETE CASCADE,
    CONSTRAINT ck_allergy_allergen_not_blank CHECK (CHAR_LENGTH(TRIM(allergen)) > 0),
    CONSTRAINT ck_allergy_severity CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_allergy_pet_id ON allergy (pet_id);
