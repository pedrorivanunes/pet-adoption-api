-- =========================================================================
-- V4 -- Retrato da compatibilidade no momento da candidatura.
--
-- O score fica gravado na candidatura em vez de ser recalculado a cada
-- leitura: é o valor que existia quando a pessoa se candidatou. Se ela editar
-- as preferências amanhã, ou o abrigo corrigir os dados de saúde do animal, o
-- histórico não deve mudar retroativamente -- quem decidiu decidiu com base no
-- que estava na tela naquele dia.
--
-- A busca "quero adotar" é outra coisa: ali o cálculo é ao vivo, porque o
-- objetivo é refletir o estado atual do catálogo.
-- =========================================================================

ALTER TABLE adoption_applications
    ADD COLUMN compatibility_score INTEGER;

ALTER TABLE adoption_applications
    ADD COLUMN has_blocking_factor BOOLEAN NOT NULL DEFAULT false;

-- Candidatura com fator impeditivo não é recusada automaticamente: fica
-- registrada e sinalizada, para quem cuida do animal decidir com a informação
-- à vista. Pode haver contexto que o cadastro não captura.
COMMENT ON COLUMN adoption_applications.has_blocking_factor IS
    'Havia fator impeditivo quando a candidatura foi feita';

-- O relatório de compatibilidade ordena as candidaturas de um pet por score.
CREATE INDEX idx_applications_pet_score
    ON adoption_applications (pet_id, compatibility_score DESC);
