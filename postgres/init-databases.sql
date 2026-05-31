-- Creates additional databases on first volume init.
-- The default 'superchat' database is created by POSTGRES_DB env var.
-- This script runs once via /docker-entrypoint-initdb.d/.

CREATE DATABASE users;
CREATE DATABASE notifications;
CREATE DATABASE keycloak;
CREATE DATABASE admin;
CREATE DATABASE moderation;
CREATE DATABASE compliance;

GRANT ALL PRIVILEGES ON DATABASE users TO superchat;
GRANT ALL PRIVILEGES ON DATABASE notifications TO superchat;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO superchat;
GRANT ALL PRIVILEGES ON DATABASE admin TO superchat;
GRANT ALL PRIVILEGES ON DATABASE moderation TO superchat;
GRANT ALL PRIVILEGES ON DATABASE compliance TO superchat;
