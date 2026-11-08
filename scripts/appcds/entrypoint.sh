#!/bin/sh
set -eu

case "${PLIMAP_SPRING_AOT_ENABLED:-false}" in
    false) aot_enabled=false ;;
    true)
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
        # Combined measurements must fail rather than silently fall back to no archive.
        share_mode=auto
        if [ "$aot_enabled" = true ]; then share_mode=on; fi
        exec java "-Dspring.aot.enabled=$aot_enabled" "-Xshare:$share_mode" -XX:SharedArchiveFile=/app/application.jsa \
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
