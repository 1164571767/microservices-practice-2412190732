-- Role ownership belongs to identity, independent of the pharmacy schema.
ALTER TABLE user_role DROP CHECK ck_user_role_role;
ALTER TABLE user_role ADD CONSTRAINT ck_user_role_role
    CHECK (role IN ('OWNER', 'DOCTOR', 'STAFF', 'ADMIN', 'PHARMACY'));
