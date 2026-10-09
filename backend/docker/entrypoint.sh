#!/bin/sh
set -eu

# Container restarts retain their writable layer. Never reuse last boot's readiness.
rm -f /tmp/natiart-qa-ready
if [ "${NATIART_QA_RESET_STORAGE:-false}" = true ]; then
    case ",${SPRING_PROFILES_ACTIVE:-}," in
        *,production,*) echo "QA storage cannot run with production" >&2; exit 1 ;;
    esac
    case ",${SPRING_PROFILES_ACTIVE:-}," in
        *,local-h2,*) ;; *) echo "QA storage requires local-h2" >&2; exit 1 ;;
    esac
    case ",${SPRING_PROFILES_ACTIVE:-}," in
        *,qa-h2,*) ;; *) echo "QA storage requires qa-h2" >&2; exit 1 ;;
    esac
    # Restrict reset to this container's disposable directory, never a mounted store.
    [ "$NATIART_STORAGE_ROOT" = /tmp/natiart-qa-product-images ] || exit 1
    rm -rf /tmp/natiart-qa-product-images
    mkdir /tmp/natiart-qa-product-images
    cp -R /app/qa-images/. /tmp/natiart-qa-product-images/
fi
exec java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar /app/app.jar "$@"
