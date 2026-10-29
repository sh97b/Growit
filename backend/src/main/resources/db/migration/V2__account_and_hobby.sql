ALTER TABLE users CHANGE COLUMN username email VARCHAR(255) NOT NULL;
ALTER TABLE users DROP INDEX uk_users_username;
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
ALTER TABLE users ADD COLUMN display_name VARCHAR(30) NULL AFTER email;

ALTER TABLE hobbies ADD COLUMN archived_at DATETIME NULL;

UPDATE records SET feeling = 'RELIEVED' WHERE feeling = 'REGRET';
