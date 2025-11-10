import os
from pydantic_settings import BaseSettings
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

class Settings(BaseSettings):
    # Database
    DATABASE_URL: str

    # JWT
    SECRET_KEY: str
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30

    # Application settings
    APP_TITLE: str = "SmartEye Backend"
    APP_VERSION: str = "0.1.0"

    class Config:
        # This will look for a .env file in the same directory
        # as this config.py file, or any parent directory.
        # For this project, it should be placed in Project/backend/
        env_file = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env')
        env_file_encoding = 'utf-8'

# Create a single instance of the settings
settings = Settings()
