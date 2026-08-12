-- =========================================================================
-- V2 -- Fecha o domínio de sexo e porte do pet no banco.
--
-- A aplicação já os trata como enum; sem CHECK, qualquer escrita fora da API
-- (script de carga, correção manual) poderia gravar um valor que o Hibernate
-- não sabe ler, e o erro só apareceria na leitura seguinte. O status do pet já
-- nasceu com essa proteção na V1 — aqui os outros dois campos alcançam.
-- =========================================================================

ALTER TABLE pets
    ADD CONSTRAINT ck_pets_sex CHECK (sex IS NULL OR sex IN ('MALE', 'FEMALE', 'UNKNOWN'));

ALTER TABLE pets
    ADD CONSTRAINT ck_pets_size CHECK (size IS NULL OR size IN ('SMALL', 'MEDIUM', 'LARGE'));
