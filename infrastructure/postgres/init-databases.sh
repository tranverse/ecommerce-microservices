#!/bin/sh
set -eu

create_service_database() {
  database_name="$1"
  database_user="$2"
  database_password="$3"

  psql \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=database_name="$database_name" \
    --set=database_user="$database_user" \
    --set=database_password="$database_password" \
    --set=ON_ERROR_STOP=1 <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'database_user', :'database_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'database_user')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I ENCODING %L TEMPLATE template0',
              :'database_name', :'database_user', 'UTF8')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'database_name')
\gexec

SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'database_name')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'database_user')
\gexec
SQL
}

create_service_database auth_db "$AUTH_DB_USERNAME" "$AUTH_DB_PASSWORD"
create_service_database user_db "$USER_DB_USERNAME" "$USER_DB_PASSWORD"
create_service_database product_db "$PRODUCT_DB_USERNAME" "$PRODUCT_DB_PASSWORD"
create_service_database inventory_db "$INVENTORY_DB_USERNAME" "$INVENTORY_DB_PASSWORD"
create_service_database order_db "$ORDER_DB_USERNAME" "$ORDER_DB_PASSWORD"
create_service_database payment_db "$PAYMENT_DB_USERNAME" "$PAYMENT_DB_PASSWORD"
create_service_database notification_db "$NOTIFICATION_DB_USERNAME" "$NOTIFICATION_DB_PASSWORD"
