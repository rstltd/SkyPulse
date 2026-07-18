FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
# Install deps, then pre-download the DuckDB postgres extension into the image so the
# container needs no network at runtime to LOAD it (parquet/icu are built in).
RUN pip install --no-cache-dir -r requirements.txt \
 && python -c "import duckdb; duckdb.connect().execute('INSTALL postgres')"

COPY . .

ENV LAKE_DIR=/data/lake PYTHONUNBUFFERED=1
EXPOSE 8000

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
