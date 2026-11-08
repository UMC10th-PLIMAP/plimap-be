#!/bin/sh
set -eu

case "${PLIMAP_SPRING_AOT_ENABLED:-false}" in
    false) aot_enabled=false ;;
    true)
        if [ "${PLIMAP_APP_CDS_ENABLED:-true}" != false ]; then
            echo 'Spring AOT experiments require PLIMAP_APP_CDS_ENABLED=false' >&2
            exit 64
        fi
        if [ ! -f /app/spring-aot-profile ] || [ "$(cat /app/spring-aot-profile)" != prod ]; then
            echo 'event=SPRING_AOT_CONFIGURATION status=missing_prod_artifact' >&2
            exit 1
        fi
        aot_enabled=true
        ;;
    *)
        echo 'PLIMAP_SPRING_AOT_ENABLED must be true or false.' >&2
        exit 64
        ;;
esac
echo "event=SPRING_AOT_CONFIGURATION enabled=$aot_enabled"

case "${PLIMAP_APP_CDS_ENABLED:-true}" in
    true)
        if [ ! -s /app/application.jsa ]; then
            echo 'event=APP_CDS_CONFIGURATION status=missing_archive' >&2
            exit 1
        fi
        echo 'event=APP_CDS_CONFIGURATION enabled=true archive=/app/application.jsa'
        exec java -Dspring.aot.enabled=false -Xshare:auto -XX:SharedArchiveFile=/app/application.jsa \
            -Xlog:cds=info -jar /app/app.jar "$@"
        ;;
    false)
        # Keep the JVM's default CDS; only disable the application archive.
        echo 'event=APP_CDS_CONFIGURATION enabled=false'
        exec java "-Dspring.aot.enabled=$aot_enabled" -Xlog:cds=info -jar /app/app.jar "$@"
        ;;
    *)
        echo 'PLIMAP_APP_CDS_ENABLED must be true or false.' >&2
        exit 64
        ;;
esac
