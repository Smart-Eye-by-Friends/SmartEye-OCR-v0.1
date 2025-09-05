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