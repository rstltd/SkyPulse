"""DuckDB connection helpers.

The `postgres` + `parquet` extensions are pre-installed into the image at build time
(see Dockerfile), so INSTALL is a no-op offline and LOAD works without network.
The Postgres attach is READ_ONLY — this service never writes to TimescaleDB.
"""
import duckdb

from config import pg_dsn


def new_connection() -> duckdb.DuckDBPyConnection:
    con = duckdb.connect()
    con.execute("INSTALL postgres; LOAD postgres;")
    return con


def attach_pg(con: duckdb.DuckDBPyConnection, alias: str = "pg") -> duckdb.DuckDBPyConnection:
    con.execute(f"ATTACH '{pg_dsn()}' AS {alias} (TYPE postgres, READ_ONLY)")
    return con
