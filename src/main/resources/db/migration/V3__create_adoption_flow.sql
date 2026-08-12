-- =========================================================================
-- V3 -- Perfil do adotante, candidaturas e adoções efetivadas.
-- =========================================================================


-- =========================================================================
-- PERFIL DO ADOTANTE
--
-- Situação de vida e preferências ficam na mesma tabela de propósito: na
-- prática são um formulário só, preenchido de uma vez, e sempre lidos juntos
-- pelo cálculo de compatibilidade. Separar em duas tabelas 1:1 com o mesmo
-- usuário adicionaria um join e um endpoint sem comprar nada.
--
-- A chave primária é o próprio user_id: uma pessoa tem no máximo um perfil, e
-- é o banco quem garante isso, não uma verificação na aplicação.
-- =========================================================================

CREATE TABLE adopter_profiles (
    user_id                      BIGINT      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

    -- Como essa pessoa vive -- entra nos fatores sociais e impeditivos.
    housing_type                 VARCHAR(20) NOT NULL,
    has_children                 BOOLEAN     NOT NULL DEFAULT false,
    residents_count              INTEGER,
    has_other_pets               BOOLEAN     NOT NULL DEFAULT false,
    has_time_availability        BOOLEAN     NOT NULL DEFAULT true,

    -- O que essa pessoa procura -- entra na pontuação por característica.
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
-- CANDIDATURAS
--
-- Só pessoa física se candidata: não existe adopter_org_id aqui. Organização
-- é o lado da oferta; quando ela recebe um animal, isso é transferência de
-- guarda, outro evento com outras regras.
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

    -- Se há decisão, há data de decisão, e vice-versa. Sem isto, um registro
    -- pode afirmar que foi decidido sem dizer quando.
    CONSTRAINT ck_application_decision_consistent CHECK (
        (status = 'PENDING' AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_at IS NOT NULL)
    )
);

-- "Apenas um pet pode ser buscado para adoção por vez."
--
-- Índice único PARCIAL: a unicidade vale só enquanto o status é PENDING, então
-- candidaturas passadas não impedem uma nova. A aplicação também verifica isso
-- para devolver mensagem clara, mas quem garante de fato é este índice --
-- verificação em código perde para duas requisições simultâneas.
CREATE UNIQUE INDEX ux_applications_one_pending_per_adopter
    ON adoption_applications (adopter_user_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_applications_pet     ON adoption_applications (pet_id);
CREATE INDEX idx_applications_adopter ON adoption_applications (adopter_user_id);


-- =========================================================================
-- ADOÇÕES EFETIVADAS
--
-- Registro histórico criado quando uma candidatura é aprovada. É a partir da
-- data daqui que se conta o acompanhamento pós-adoção.
--
-- Todas as chaves com RESTRICT, ao contrário das candidaturas: candidatura
-- pendente é transitória e some junto com o pet, adoção é histórico e não
-- deve sumir por efeito colateral de um delete.
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
