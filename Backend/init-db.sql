-- SmartEye Database Initialization Script

-- Create development database
CREATE DATABASE smarteye_dev;

-- Create a separate user for development (if needed)
DO $$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'smarteye_dev') THEN
      CREATE USER smarteye_dev WITH PASSWORD 'dev_password';
   END IF;
END
$$;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE smarteye_dev TO smarteye_dev;
GRANT ALL PRIVILEGES ON DATABASE smarteye_db TO smarteye;

-- Connect to smarteye_db and grant schema permissions
\c smarteye_db;
GRANT ALL ON SCHEMA public TO smarteye;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO smarteye;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO smarteye;

-- Connect to smarteye_dev and grant schema permissions
\c smarteye_dev;
GRANT ALL ON SCHEMA public TO smarteye_dev;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO smarteye_dev;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO smarteye_dev;

-- Create default development user for Swagger/API testing
-- This resolves the user_id NOT NULL constraint issue
INSERT INTO users (username, email, display_name, is_active, created_at, updated_at)
VALUES ('dev_user', 'dev@smarteye.com', '개발 테스트 사용자', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

-- Connect to production database and add default user there too
\c smarteye_db;
INSERT INTO users (username, email, display_name, is_active, created_at, updated_at)
VALUES ('dev_user', 'dev@smarteye.com', '개발 테스트 사용자', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;