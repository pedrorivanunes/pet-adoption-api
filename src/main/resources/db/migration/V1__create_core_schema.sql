-- =========================================================================
-- V1 -- Core schema: identity, organizations and pets.
--
-- A deliberate note: no statement uses "IF NOT EXISTS". A migration is
-- deterministic -- if the object already exists, something has moved and the
-- build should fail loudly instead of masking the drift.
-- =========================================================================


-- =========================================================================
-- IDENTITY AND PERMISSIONS
-- =========================================================================

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone         VARCHAR(30),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Uniqueness over lower(email): the application normalises email on write, but
-- the index is what guarantees that "Maria@x.com" and "maria@x.com" cannot
-- coexist even if normalisation is missed on some new code path.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));


-- Global roles, deliberately coarse. Fine-grained authorization over an
-- organization's resources comes from the membership
-- (organization_memberships), not from here.
--
-- The names do NOT carry the "ROLE_" prefix: that prefix is a Spring Security
-- internal convention, not a domain concept. The application uses
-- hasAuthority() with the exact name, with no implicit concatenation anywhere.
CREATE TABLE roles (
    id   BIGSERIAL   PRIMARY KEY,
    name VARCHAR(40) NOT NULL,

    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,

    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles (name) VALUES ('USER'), ('ADMIN');


-- =========================================================================
-- ORGANIZATIONS
--
-- An organization is not a user: it has no login and no credentials. Whoever
-- acts on its behalf is always a person, through the membership below.
-- =========================================================================

CREATE TABLE organizations (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    email       VARCHAR(180),
    phone       VARCHAR(30),
    address     VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The SINGLE source of truth about who may act on behalf of an organization.
-- There is no "admin_user_id" column on organizations: two sources for the
-- same fact drift apart sooner or later.
--
-- UNIQUE (organization_id, user_id): a person has at most one membership per
-- organization. The role is a column, not part of the key -- promoting from
-- STAFF to ADMIN is an UPDATE, not a second row.
CREATE TABLE organization_memberships (
    id              BIGSERIAL   PRIMARY KEY,
    organization_id BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    member_role     VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_org_membership_user  UNIQUE (organization_id, user_id),
    CONSTRAINT ck_org_membership_role  CHECK (member_role IN ('ADMIN', 'STAFF'))
);

CREATE INDEX idx_org_memberships_user ON organization_memberships (user_id);


-- =========================================================================
-- PETS
-- =========================================================================

CREATE TABLE pets (
    id                       BIGSERIAL    PRIMARY KEY,
    name                     VARCHAR(100) NOT NULL,
    species                  VARCHAR(30)  NOT NULL,
    breed                    VARCHAR(100),
    sex                      VARCHAR(10),
    size                     VARCHAR(20),

    -- A birth date rather than an age in years: a derived age never goes
    -- stale. A rescued animal rarely has an exact date, so the estimate is
    -- recorded explicitly instead of faking precision.
    birth_date               DATE,
    birth_date_estimated     BOOLEAN      NOT NULL DEFAULT true,

    status                   VARCHAR(20)  NOT NULL,

    -- The owner is a person OR an organization, never both and never neither.
    -- RESTRICT on both: deleting an owner that still has pets should fail
    -- explicitly. (With SET NULL the pet would be left with no owner at all
    -- and would violate the very CHECK below.)
    owner_user_id            BIGINT       REFERENCES users(id)         ON DELETE RESTRICT,
    owner_org_id             BIGINT       REFERENCES organizations(id) ON DELETE RESTRICT,

    -- Health factors used by the compatibility calculation. NOT NULL with a
    -- default: missing information becomes "false" at registration, rather
    -- than a NULL that spreads ternaries through the whole scoring rule.
    has_special_needs        BOOLEAN      NOT NULL DEFAULT false,
    has_continuous_treatment BOOLEAN      NOT NULL DEFAULT false,
    has_chronic_disease      BOOLEAN      NOT NULL DEFAULT false,
    requires_constant_care   BOOLEAN      NOT NULL DEFAULT false,

    -- A deliberate exception to the rule above: here NULL means "unknown".
    -- Telling "known not to get along" apart from "unknown" matters, because
    -- only the first is a blocker for adoption.
    good_with_other_animals  BOOLEAN,

    health_notes             VARCHAR(1000),

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_pets_status CHECK (status IN ('AVAILABLE', 'ADOPTED', 'LOST', 'DECEASED')),
    CONSTRAINT ck_pets_owner  CHECK (
           (owner_user_id IS NOT NULL AND owner_org_id IS NULL)
        OR (owner_user_id IS NULL     AND owner_org_id IS NOT NULL)
    )
);

CREATE INDEX idx_pets_status     ON pets (status);
CREATE INDEX idx_pets_owner_user ON pets (owner_user_id);
CREATE INDEX idx_pets_owner_org  ON pets (owner_org_id);
