echo "Running Stirling PDF with DISABLE_ADDITIONAL_FEATURES=${DISABLE_ADDITIONAL_FEATURES} and VERSION_TAG=${VERSION_TAG}"

# Skip external download when no version tag is provided (local builds already include proprietary code)
if [ -z "$VERSION_TAG" ]; then
    echo "VERSION_TAG not provided; skipping remote security jar download"
    exit 0
fi

# Check for DISABLE_ADDITIONAL_FEATURES and download the appropriate JAR if required
if [ "$VERSION_TAG" != "alpha" ] && [ "$VERSION_TAG" != "ALPHA" ]; then
    if [ "$DISABLE_ADDITIONAL_FEATURES" = "false" ] || [ "$DISABLE_ADDITIONAL_FEATURES" = "FALSE" ] || [ "$DOCKER_ENABLE_SECURITY" = "true" ] || [ "$DOCKER_ENABLE_SECURITY" = "TRUE" ]; then
        if [ ! -f app-security.jar ]; then
            echo "Trying to download from: https://files.stirlingpdf.com/v$VERSION_TAG/Stirling-PDF-with-login.jar"
            if ! curl -fsSL -o app-security.jar https://files.stirlingpdf.com/v$VERSION_TAG/Stirling-PDF-with-login.jar; then
                echo "Trying to download from: https://files.stirlingpdf.com/$VERSION_TAG/Stirling-PDF-with-login.jar"
                if ! curl -fsSL -o app-security.jar https://files.stirlingpdf.com/$VERSION_TAG/Stirling-PDF-with-login.jar; then
                    echo "Failed to download remote security jar; continuing with bundled jar"
                    rm -f app-security.jar
                    exit 0
                fi
            fi

            rm -f app.jar
            ln -s app-security.jar app.jar
            chown stirlingpdfuser:stirlingpdfgroup app.jar || true
            chmod 755 app.jar || true
        fi
    fi
fi
