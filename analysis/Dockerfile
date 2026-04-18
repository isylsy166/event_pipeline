FROM python:3.11-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends fonts-nanum && \
    rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir psycopg2-binary matplotlib && \
    python -c "import matplotlib.font_manager; matplotlib.font_manager._load_fontmanager(try_read_cache=False)"

WORKDIR /app