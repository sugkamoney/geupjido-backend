#!/usr/bin/env python3
"""Restore the selected local data into an empty Flyway-created dev database."""

import os
import shlex
import subprocess
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


TABLES = (
    "region",
    "city",
    "zone",
    "zone_adjacency",
    "complex",
    "apartment_trade",
    "apartment_presale_trade",
    "apartment_trade_conflict_seen",
    "apartment_trade_conflict",
)


def read_config(path):
    values = {}
    for line in path.read_text().splitlines():
        if not line or line.lstrip().startswith("#"):
            continue
        key, separator, raw_value = line.partition("=")
        if separator and key in {"DB_URL", "DB_USERNAME", "DB_PASSWORD"}:
            parsed = shlex.split(raw_value)
            if len(parsed) != 1:
                raise ValueError(f"Invalid {key} in environment file")
            values[key] = parsed[0]
    if set(values) != {"DB_URL", "DB_USERNAME", "DB_PASSWORD"}:
        raise ValueError("DB_URL, DB_USERNAME and DB_PASSWORD are required")
    return values


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: restore-dev-data.py ENV_FILE ARCHIVE")

    config = read_config(Path(sys.argv[1]))
    archive = Path(sys.argv[2])
    if not archive.is_file():
        raise ValueError("Archive does not exist")

    url = urlsplit(config["DB_URL"].removeprefix("jdbc:"))
    options = parse_qs(url.query)
    ca_path = options.get("sslrootcert", [None])[0]
    if (
        url.scheme != "postgresql"
        or not url.hostname
        or not url.path.strip("/")
        or options.get("sslmode") != ["verify-full"]
        or not ca_path
        or not Path(ca_path).is_file()
    ):
        raise ValueError("DB_URL must use PostgreSQL with a valid verify-full CA certificate")

    connection = [
        "-h", url.hostname,
        "-p", str(url.port or 5432),
        "-U", config["DB_USERNAME"],
        "-d", url.path.lstrip("/"),
    ]
    pg_env = os.environ.copy()
    pg_env.update({
        "PGPASSWORD": config["DB_PASSWORD"],
        "PGSSLMODE": "verify-full",
        "PGSSLROOTCERT": ca_path,
    })

    count_sql = " UNION ALL ".join(
        f"SELECT '{table}', count(*) FROM public.{table}" for table in TABLES
    )
    counts = subprocess.check_output(
        ["psql", "-X", "-w", "-A", "-t", "-v", "ON_ERROR_STOP=1", *connection, "-c", count_sql],
        env=pg_env,
        text=True,
    )
    if any(int(row.rsplit("|", 1)[1]) for row in counts.splitlines()):
        raise SystemExit("Target tables are not empty; restore was not started")

    subprocess.run(
        [
            "pg_restore", "--data-only", "--no-owner", "--no-privileges",
            "--exit-on-error", "--single-transaction", "--verbose",
            *connection, str(archive),
        ],
        env=pg_env,
        check=True,
    )
    print("Restored row counts:")
    print(subprocess.check_output(
        ["psql", "-X", "-w", "-A", "-t", "-v", "ON_ERROR_STOP=1", *connection, "-c", count_sql],
        env=pg_env,
        text=True,
    ), end="")


if __name__ == "__main__":
    main()
