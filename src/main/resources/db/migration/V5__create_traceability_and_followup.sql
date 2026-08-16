-- =========================================================================
-- V5 -- Traceability history, animal health and post-adoption follow-up.
-- =========================================================================


-- =========================================================================
-- STAYS (TRACEABILITY)
--
-- The animal's timeline is modelled as intervals rather than point events: the
-- domain question is "where was it and for how long", and duration comes out of
-- an interval, not an instant.
--
-- started_on may predate the pet's registration in the system -- a rescue
-- almost always does. That is why the date is supplied rather than derived from
-- created_at.
-- =========================================================================

CREATE TABLE pet_stays (
    id                BIGSERIAL    PRIMARY KEY,
    pet_id            BIGINT       NOT NULL REFERENCES pets(id) ON DELETE CASCADE,

    kind              VARCHAR(20)  NOT NULL,
    location          VARCHAR(255) NOT NULL,

    -- Who held custody. Both null is allowed on purpose: the time on the street
    -- before the rescue has nobody responsible, and pretending otherwise would
    -- be a lie.
    custodian_user_id BIGINT       REFERENCES users(id)         ON DELETE SET NULL,
    custodian_org_id  BIGINT       REFERENCES organizations(id) ON DELETE SET NULL,

    started_on        DATE         NOT NULL,
    ended_on          DATE,
    notes             VARCHAR(1000),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_stay_kind      CHECK (kind IN ('RESCUE', 'SHELTER', 'FOSTER', 'ADOPTION', 'OTHER')),
    CONSTRAINT ck_stay_period    CHECK (ended_on IS NULL OR ended_on >= started_on),
    CONSTRAINT ck_stay_custodian CHECK (NOT (custodian_user_id IS NOT NULL AND custodian_org_id IS NOT NULL))
);

-- An animal is in one place at a time: at most one open stay. The same device
-- used for "one pending application per person" -- a partial unique index,
-- because the rule holds only for the open rows.
CREATE UNIQUE INDEX ux_pet_stays_single_open
    ON pet_stays (pet_id)
    WHERE ended_on IS NULL;

CREATE INDEX idx_pet_stays_pet ON pet_stays (pet_id, started_on);


-- =========================================================================
-- ADOPTION ORIGIN
--
-- Who handed the animal over. On approval, guardianship passes to the adopter
-- -- and without this record we would lose precisely the party who owes the
-- follow-up over the next six months.
-- =========================================================================

ALTER TABLE adoptions ADD COLUMN origin_user_id BIGINT REFERENCES users(id)         ON DELETE RESTRICT;
ALTER TABLE adoptions ADD COLUMN origin_org_id  BIGINT REFERENCES organizations(id) ON DELETE RESTRICT;

-- "At most one", not "exactly one": adoptions recorded before this migration
-- have no origin, and a constraint that invalidated them would make the deploy
-- fail in any environment that already had data.
ALTER TABLE adoptions ADD CONSTRAINT ck_adoption_origin CHECK (
    NOT (origin_user_id IS NOT NULL AND origin_org_id IS NOT NULL)
);


-- =========================================================================
-- ANIMAL HEALTH
--
-- Hung off the pet, not off the adoption: vaccination and neutering are the
-- animal's history for life, not an adoption contract's. The follow-up report
-- merely crops the window it cares about.
-- =========================================================================

CREATE TABLE pet_health_records (
    id                  BIGSERIAL     PRIMARY KEY,
    pet_id              BIGINT        NOT NULL REFERENCES pets(id) ON DELETE CASCADE,
    kind                VARCHAR(20)   NOT NULL,
    occurred_on         DATE          NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    recorded_by_user_id BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_health_kind CHECK (
        kind IN ('VACCINATION', 'NEUTERING', 'ILLNESS', 'TREATMENT', 'CHECKUP', 'OTHER'))
);

CREATE INDEX idx_health_records_pet ON pet_health_records (pet_id, occurred_on);


-- =========================================================================
-- POST-ADOPTION FOLLOW-UP
-- =========================================================================

CREATE TABLE adoption_followups (
    id                  BIGSERIAL   PRIMARY KEY,
    adoption_id         BIGINT      NOT NULL REFERENCES adoptions(id) ON DELETE CASCADE,
    kind                VARCHAR(20) NOT NULL,
    occurred_on         DATE        NOT NULL,
    notes               VARCHAR(1000),
    recorded_by_user_id BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_followup_kind CHECK (kind IN ('VISIT', 'CALL', 'MESSAGE', 'OTHER'))
);

CREATE INDEX idx_followups_adoption ON adoption_followups (adoption_id, occurred_on);
