-- =========================================================================
-- V3 -- Adopter profile, applications and completed adoptions.
-- =========================================================================


-- =========================================================================
-- ADOPTER PROFILE
--
-- Living situation and preferences share one table on purpose: in practice
-- they are a single form, filled in at once, and always read together by the
-- compatibility calculation. Splitting them into two 1:1 tables on the same
-- user would add a join and an endpoint and buy nothing.
--
-- The primary key is the user_id itself: a person has at most one profile, and
-- the database is what guarantees that, not a check in the application.
-- =========================================================================

CREATE TABLE adopter_profiles (
    user_id                      BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

    -- How this person lives -- feeds the social factors and the blockers.
    housing_type                 VARCHAR(20) NOT NULL,
    has_children                 BOOLEAN     NOT NULL DEFAULT false,
    residents_count              INTEGER,
    has_other_pets               BOOLEAN     NOT NULL DEFAULT false,
    has_time_availability        BOOLEAN     NOT NULL DEFAULT true,

    -- What this person is looking for -- feeds the per-characteristic scoring.
    preferred_species            VARCHAR(30),
    preferred_breed              VARCHAR(100),
    preferred_size               VARCHAR(20),
    preferred_sex                VARCHAR(10),
    accepts_special_needs        BOOLEAN     NOT NULL DEFAULT false,
    accepts_continuous_treatment BOOLEAN     NOT NULL DEFAULT false,
    accepts_chronic_disease      BOOLEAN     NOT NULL DEFAULT false,

    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_adopter_housing_type CHECK (housing_type IN ('HOUSE', 'APARTMENT', 'RURAL')),
    CONSTRAINT ck_adopter_residents    CHECK (residents_count IS NULL OR residents_count >= 0),
    CONSTRAINT ck_adopter_pref_size    CHECK (preferred_size IS NULL OR preferred_size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT ck_adopter_pref_sex     CHECK (preferred_sex IS NULL OR preferred_sex IN ('MALE', 'FEMALE', 'UNKNOWN'))
);


-- =========================================================================
-- APPLICATIONS
--
-- Only a private individual applies: there is no adopter_org_id here. An
-- organization is the supply side; when it takes an animal in, that is a
-- transfer of custody, a different event with different rules.
-- =========================================================================

CREATE TABLE adoption_applications (
    id                 BIGSERIAL     PRIMARY KEY,
    pet_id             BIGINT        NOT NULL REFERENCES pets(id)  ON DELETE CASCADE,
    adopter_user_id    BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    status             VARCHAR(20)   NOT NULL,
    message            VARCHAR(1000),

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    decided_at         TIMESTAMPTZ,
    decided_by_user_id BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    decision_note      VARCHAR(1000),

    CONSTRAINT ck_application_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED')),

    -- If there is a decision, there is a decision date, and vice versa.
    -- Without this, a row could claim it was decided without saying when.
    CONSTRAINT ck_application_decision_consistent CHECK (
        (status = 'PENDING' AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_at IS NOT NULL)
    )
);

-- "Only one pet may be sought for adoption at a time."
--
-- A PARTIAL unique index: uniqueness holds only while the status is PENDING,
-- so past applications do not block a new one. The application checks this too,
-- in order to return a clear message, but this index is what actually
-- guarantees it -- a check in code loses to two simultaneous requests.
CREATE UNIQUE INDEX ux_applications_one_pending_per_adopter
    ON adoption_applications (adopter_user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_applications_pet     ON adoption_applications (pet_id);
CREATE INDEX idx_applications_adopter ON adoption_applications (adopter_user_id);


-- =========================================================================
-- COMPLETED ADOPTIONS
--
-- A historical record created when an application is approved. The date here
-- is what the post-adoption follow-up counts from.
--
-- Every key uses RESTRICT, unlike applications: a pending application is
-- transient and goes away with the pet, whereas an adoption is history and
-- should not vanish as a side effect of a delete.
-- =========================================================================

CREATE TABLE adoptions (
    id              BIGSERIAL   PRIMARY KEY,
    application_id  BIGINT      NOT NULL UNIQUE REFERENCES adoption_applications(id) ON DELETE RESTRICT,
    pet_id          BIGINT      NOT NULL REFERENCES pets(id)  ON DELETE RESTRICT,
    adopter_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    adopted_on      DATE        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_adoptions_pet     ON adoptions (pet_id);
CREATE INDEX idx_adoptions_adopter ON adoptions (adopter_user_id);
