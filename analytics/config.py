"""Runtime config for the SkyPulse analytics sidecar.

Everything comes from the environment (libpq-style PG* names so DuckDB's postgres
extension and any psql debugging share the same vars). Defaults target the docker
network in production (`postgres` host); override PGHOST for local runs.
"""
import os


class Settings:
    pg_host = os.getenv("PGHOST", "postgres")
    pg_port = int(os.getenv("PGPORT", "5432"))
    pg_database = os.getenv("PGDATABASE", "skypulse")
    pg_user = os.getenv("PGUSER", "skypulse")
    pg_password = os.getenv("PGPASSWORD", "skypulse_dev")
    lake_dir = os.getenv("LAKE_DIR", "/data/lake")
    materialize_interval_seconds = int(os.getenv("MATERIALIZE_INTERVAL_SECONDS", "3600"))
    tz = "Asia/Taipei"


settings = Settings()


def pg_dsn() -> str:
    return (
        f"host={settings.pg_host} port={settings.pg_port} "
        f"dbname={settings.pg_database} user={settings.pg_user} "
        f"password={settings.pg_password}"
    )
