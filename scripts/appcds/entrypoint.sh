#!/bin/sh
set -eu

case "${PLIMAP_APP_CDS_ENABLED:-true}" in
    true)
        if [ ! -s /app/application.jsa ]; then
            echo 'event=APP_CDS_CONFIGURATION status=missing_archive' >&2
            exit 1
        fi
        echo 'event=APP_CDS_CONFIGURATION enabled=true archive=/app/application.jsa'
        exec java -Xshare:auto -XX:SharedArchiveFile=/app/application.jsa \
            -Xlog:cds=info -jar /app/app.jar "$@"
        ;;
    false)
        # Keep the JVM's default CDS; only disable the application archive.
        echo 'event=APP_CDS_CONFIGURATION enabled=false'
        exec java -Xlog:cds=info -jar /app/app.jar "$@"
        ;;
    *)
        echo 'PLIMAP_APP_CDS_ENABLED must be true or false.' >&2
        exit 64
        ;;
esac
