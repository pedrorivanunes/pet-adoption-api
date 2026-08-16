-- =========================================================================
-- V4 -- A snapshot of compatibility at the moment of application.
--
-- The score is stored on the application instead of being recomputed on every
-- read: it is the value that existed when the person applied. If they edit
-- their preferences tomorrow, or the shelter corrects the animal's health data,
-- the history should not change retroactively -- whoever decided, decided based
-- on what was on the screen that day.
--
-- The "I want to adopt" search is a different thing: there the calculation is
-- live, because the goal is to reflect the current state of the catalogue.
-- =========================================================================

ALTER TABLE adoption_applications
    ADD COLUMN compatibility_score INTEGER;

ALTER TABLE adoption_applications
    ADD COLUMN has_blocking_factor BOOLEAN NOT NULL DEFAULT false;

-- An application with a blocker is not rejected automatically: it is recorded
-- and flagged, so whoever cares for the animal decides with the information in
-- plain sight. There may be context the records do not capture.
COMMENT ON COLUMN adoption_applications.has_blocking_factor IS
    'A blocking factor was present when the application was made';

-- The compatibility report orders a pet's applications by score.
CREATE INDEX idx_applications_pet_score
    ON adoption_applications (pet_id, compatibility_score DESC);
