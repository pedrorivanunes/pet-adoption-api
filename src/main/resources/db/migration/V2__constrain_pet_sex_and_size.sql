-- =========================================================================
-- V2 -- Closes the pet sex and size domains in the database.
--
-- The application already treats them as enums; without a CHECK, any write
-- outside the API (a load script, a manual fix) could store a value Hibernate
-- cannot read, and the error would only surface on the next read. The pet
-- status was born with this protection in V1 -- here the other two catch up.
-- =========================================================================

ALTER TABLE pets
    ADD CONSTRAINT ck_pets_sex CHECK (sex IS NULL OR sex IN ('MALE', 'FEMALE', 'UNKNOWN'));

ALTER TABLE pets
    ADD CONSTRAINT ck_pets_size CHECK (size IS NULL OR size IN ('SMALL', 'MEDIUM', 'LARGE'));
