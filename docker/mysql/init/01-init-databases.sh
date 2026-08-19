#!/usr/bin/env bash

set -e
set -o pipefail

ff_mysql_fail() {
    printf 'MySQL initialization error: %s\n' "$1" >&2
    return 1
}

ff_mysql_required_variables=(
    MYSQL_ROOT_PASSWORD
    AUTH_DB_USERNAME
    AUTH_DB_PASSWORD
    FLAG_DB_USERNAME
    FLAG_DB_PASSWORD
    AUDIT_DB_USERNAME
    AUDIT_DB_PASSWORD
    ANALYTICS_DB_USERNAME
    ANALYTICS_DB_PASSWORD
    NOTIFICATION_DB_USERNAME
    NOTIFICATION_DB_PASSWORD
)

for ff_mysql_variable_name in "${ff_mysql_required_variables[@]}"; do
    if [[ -z "${!ff_mysql_variable_name:-}" ]]; then
        ff_mysql_fail "required variable ${ff_mysql_variable_name} is missing or blank"
    fi
done

ff_mysql_service_specs=(
    'auth_db:AUTH_DB_USERNAME:AUTH_DB_PASSWORD'
    'flag_db:FLAG_DB_USERNAME:FLAG_DB_PASSWORD'
    'audit_db:AUDIT_DB_USERNAME:AUDIT_DB_PASSWORD'
    'analytics_db:ANALYTICS_DB_USERNAME:ANALYTICS_DB_PASSWORD'
    'notification_db:NOTIFICATION_DB_USERNAME:NOTIFICATION_DB_PASSWORD'
)

declare -A ff_mysql_seen_usernames=()

for ff_mysql_service_spec in "${ff_mysql_service_specs[@]}"; do
    IFS=':' read -r ff_mysql_database ff_mysql_username_variable ff_mysql_password_variable \
        <<< "$ff_mysql_service_spec"

    ff_mysql_username="${!ff_mysql_username_variable}"
    ff_mysql_username_key="${ff_mysql_username,,}"

    if [[ ! "$ff_mysql_username" =~ ^[A-Za-z0-9_]{1,32}$ ]]; then
        ff_mysql_fail "${ff_mysql_username_variable} must contain only 1-32 ASCII letters, digits, or underscores"
    fi

    if [[ "$ff_mysql_username_key" == 'root' ]]; then
        ff_mysql_fail "${ff_mysql_username_variable} must not be root"
    fi

    if [[ -n "${ff_mysql_seen_usernames[$ff_mysql_username_key]:-}" ]]; then
        ff_mysql_fail 'service database usernames must be unique'
    fi

    ff_mysql_seen_usernames["$ff_mysql_username_key"]=1
done

ff_mysql_provision_service_database() {
    local ff_mysql_database="$1"
    local ff_mysql_username_variable="$2"
    local ff_mysql_password_variable="$3"
    local ff_mysql_username="${!ff_mysql_username_variable}"
    local ff_mysql_password="${!ff_mysql_password_variable}"
    local ff_mysql_password_base64
    local ff_mysql_database_identifier="\`${ff_mysql_database}\`"
    local ff_mysql_grant_database="${ff_mysql_database//_/\\_}"
    local ff_mysql_grant_identifier="\`${ff_mysql_grant_database}\`"

    ff_mysql_password_base64="$(printf '%s' "$ff_mysql_password" | base64 | tr -d '\n')"

    printf 'Provisioning MySQL database %s and its scoped service account.\n' "$ff_mysql_database"

    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
        --protocol=socket \
        --host=localhost \
        --user=root \
        --default-character-set=utf8mb4 <<SQL
CREATE DATABASE IF NOT EXISTS ${ff_mysql_database_identifier}
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SET @ff_service_password = CONVERT(
    FROM_BASE64('${ff_mysql_password_base64}') USING utf8mb4
);
SET @ff_create_user = CONCAT(
    'CREATE USER ''${ff_mysql_username}''@''%'' IDENTIFIED BY ',
    QUOTE(@ff_service_password)
);
PREPARE ff_create_user_statement FROM @ff_create_user;
EXECUTE ff_create_user_statement;
DEALLOCATE PREPARE ff_create_user_statement;

GRANT ALL PRIVILEGES ON ${ff_mysql_grant_identifier}.*
    TO '${ff_mysql_username}'@'%';
SQL
}

for ff_mysql_service_spec in "${ff_mysql_service_specs[@]}"; do
    IFS=':' read -r ff_mysql_database ff_mysql_username_variable ff_mysql_password_variable \
        <<< "$ff_mysql_service_spec"
    ff_mysql_provision_service_database \
        "$ff_mysql_database" \
        "$ff_mysql_username_variable" \
        "$ff_mysql_password_variable"
done

printf 'MySQL service database initialization completed.\n'
