-- =========================================================================
-- V5 -- Histórico de rastreabilidade, saúde do animal e acompanhamento
--       pós-adoção.
-- =========================================================================


-- =========================================================================
-- PERMANÊNCIAS (RASTREABILIDADE)
--
-- A linha do tempo do animal é modelada como intervalos, não como eventos
-- pontuais: a pergunta do domínio é "onde esteve e por quanto tempo", e
-- duração se extrai de um intervalo, não de um instante.
--
-- started_on pode ser anterior ao cadastro do pet no sistema -- o resgate
-- quase sempre é. Por isso a data é informada, e não derivada de created_at.
-- =========================================================================

CREATE TABLE pet_stays (
    id                BIGSERIAL    PRIMARY KEY,
    pet_id            BIGINT       NOT NULL REFERENCES pets(id) ON DELETE CASCADE,

    kind              VARCHAR(20)  NOT NULL,
    location          VARCHAR(255) NOT NULL,

    -- Quem tinha a guarda. Ambos nulos é permitido de propósito: o período de
    -- rua antes do resgate não tem responsável, e fingir que tem seria mentir.
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

-- Um animal está num lugar de cada vez: no máximo uma permanência aberta.
-- Mesmo recurso usado para "uma candidatura pendente por pessoa" -- índice
-- único parcial, porque a regra vale só para as linhas em aberto.
CREATE UNIQUE INDEX ux_pet_stays_single_open
    ON pet_stays (pet_id)
    WHERE ended_on IS NULL;

CREATE INDEX idx_pet_stays_pet ON pet_stays (pet_id, started_on);


-- =========================================================================
-- ORIGEM DA ADOÇÃO
--
-- Quem entregou o animal. Ao aprovar, a tutoria passa para o adotante -- e
-- sem este registro se perderia justamente quem tem o dever de acompanhar os
-- seis meses seguintes.
-- =========================================================================

ALTER TABLE adoptions ADD COLUMN origin_user_id BIGINT REFERENCES users(id)         ON DELETE RESTRICT;
ALTER TABLE adoptions ADD COLUMN origin_org_id  BIGINT REFERENCES organizations(id) ON DELETE RESTRICT;

-- "No máximo um", e não "exatamente um": adoções registradas antes desta
-- migration não têm origem, e uma restrição que as invalidasse faria o deploy
-- falhar em qualquer ambiente que já tivesse dados.
ALTER TABLE adoptions ADD CONSTRAINT ck_adoption_origin CHECK (
    NOT (origin_user_id IS NOT NULL AND origin_org_id IS NOT NULL)
);


-- =========================================================================
-- SAÚDE DO ANIMAL
--
-- Pendurado no pet, e não na adoção: vacina e castração são história do
-- animal para a vida inteira, não de um contrato de adoção. O relatório de
-- acompanhamento apenas recorta a janela que lhe interessa.
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
-- ACOMPANHAMENTO PÓS-ADOÇÃO
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
