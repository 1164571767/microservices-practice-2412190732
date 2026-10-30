-- Preserve the published optional '+' and 6-20 digit phone contract.
ALTER TABLE users MODIFY COLUMN phone VARCHAR(21) NOT NULL;
