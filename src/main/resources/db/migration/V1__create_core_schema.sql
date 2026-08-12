-- =========================================================================
-- V1 -- Núcleo do schema: identidade, organizações e pets.
--
-- Nota deliberada: nenhuma instrução usa "IF NOT EXISTS". Migration é
-- determinística -- se o objeto já existe, algo saiu do lugar e o build
-- deve falhar alto em vez de mascarar o desvio.
-- =========================================================================


-- =========================================================================
-- IDENTIDADE E PERMISSÕES
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

-- Unicidade sobre lower(email): a aplicação normaliza o e-mail na escrita,
-- mas o índice garante que "Maria@x.com" e "maria@x.com" não coexistam nem
-- que a normalização falhe em algum caminho novo.
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));


-- Papéis globais, propositalmente grossos. Autorização fina sobre recursos
-- de uma organização vem do vínculo (organization_memberships), não daqui.
--
-- Os nomes NÃO carregam o prefixo "ROLE_": o prefixo é convenção interna do
-- Spring Security, não conceito do domínio. A aplicação usa hasAuthority()
-- com o nome exato, sem concatenação implícita em lugar nenhum.
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
-- ORGANIZAÇÕES
--
-- Organização não é usuário: não tem login nem credencial. Quem age em nome
-- dela é sempre uma pessoa, através do vínculo abaixo.
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

-- Fonte ÚNICA da verdade sobre quem pode agir em nome de uma organização.
-- Não existe coluna "admin_user_id" em organizations: duas fontes para o
-- mesmo fato divergem cedo ou tarde.
--
-- UNIQUE (organization_id, user_id): uma pessoa tem no máximo um vínculo por
-- organização. O papel é coluna, não parte da chave -- promover de STAFF para
-- ADMIN é um UPDATE, não uma segunda linha.
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

    -- Data de nascimento em vez de idade em anos: idade derivada nunca fica
    -- desatualizada. Animal resgatado raramente tem data exata, então a
    -- estimativa é registrada explicitamente em vez de fingir precisão.
    birth_date               DATE,
    birth_date_estimated     BOOLEAN      NOT NULL DEFAULT true,

    status                   VARCHAR(20)  NOT NULL,

    -- Dono é pessoa OU organização, nunca ambos e nunca nenhum.
    -- RESTRICT nos dois: apagar um dono que ainda tem pets deve falhar de
    -- forma explícita. (Com SET NULL o pet ficaria sem dono nenhum e violaria
    -- o próprio CHECK abaixo -- foi exatamente o defeito do schema anterior.)
    owner_user_id            BIGINT       REFERENCES users(id)         ON DELETE RESTRICT,
    owner_org_id             BIGINT       REFERENCES organizations(id) ON DELETE RESTRICT,

    -- Fatores de saúde usados no cálculo de compatibilidade. NOT NULL com
    -- default: ausência de informação vira "false" no cadastro, e não NULL
    -- espalhando ternário por toda a regra de pontuação.
    has_special_needs        BOOLEAN      NOT NULL DEFAULT false,
    has_continuous_treatment BOOLEAN      NOT NULL DEFAULT false,
    has_chronic_disease      BOOLEAN      NOT NULL DEFAULT false,
    requires_constant_care   BOOLEAN      NOT NULL DEFAULT false,

    -- Exceção proposital à regra acima: aqui NULL significa "não se sabe".
    -- Distinguir "sabidamente não convive" de "desconhecido" importa, porque
    -- só o primeiro é fator impeditivo de adoção.
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
