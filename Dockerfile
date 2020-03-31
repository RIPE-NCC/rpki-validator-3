#
# Two step Dockerfile
#   - first build step: check hash and unpack archive
#   - second build step: install dependencies, move files around, adjust config
#
# Unpacking in a separate build step makes sure the archive that is COPY-d does
# not become a layer in the final image.
#
FROM adoptopenjdk:11-jre-hotspot as intermediate

# The hash as an build-arg ensures that args change even when the file name is
# equal, otherwise docker would cache an image when GENERIC_BUILD_ARCHIVE does
ARG GENERIC_BUILD_ARCHIVE
ARG GENERIC_BUILD_SHA256

COPY ${GENERIC_BUILD_ARCHIVE} /tmp/

RUN export TMPDIR=$(mktemp -d) \
    && mkdir -p /opt/rpki-validator-3 \
    && if [ -z "$GENERIC_BUILD_SHA256" ]; then echo "Supply GENERIC_BUILD_SHA256"; exit 2; fi; \
       echo -n "${GENERIC_BUILD_SHA256} /tmp/$(basename ${GENERIC_BUILD_ARCHIVE})" | sha256sum --check \ 
    && tar -zxf /tmp/$(basename $GENERIC_BUILD_ARCHIVE) -C ${TMPDIR} \
    # Move files from the dir in the archive (like rpki-validator-3.1-2020.01.13.09.31.26) to target folder:
    && mv ${TMPDIR}/*/* /opt/rpki-validator-3

# Second build step: Move files into place
FROM adoptopenjdk:11-jre-hotspot
# Keep the file name and sha256 in the metadata
ARG GENERIC_BUILD_ARCHIVE
ARG GENERIC_BUILD_SHA256
LABEL validation.archive.file="$(basename ${GENERIC_BUILD_ARCHIVE})"
LABEL validation.archive.sha256="${GENERIC_BUILD_SHA256}"

# Webserver on 8080
EXPOSE 8080

# JVM memory settings
ENV JVM_XMS=""
ENV JVM_XMX=""
# Used by `rpki-validator-3.sh`
ENV CONFIG_DIR="/config"

COPY --from=intermediate /opt/rpki-validator-3 /opt/rpki-validator-3
WORKDIR /opt/rpki-validator-3

RUN apt-get update && apt-get install --no-install-recommends --yes rsync \
    # Clean apt cache
    && rm -rf /var/lib/apt/lists/* \
    # UseContainerSupport: important
    && sed -i '/MEM_OPTIONS=/c\MEM_OPTIONS="-Xms$JVM_XMS -Xmx$JVM_XMX -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport"' /opt/rpki-validator-3/rpki-validator-3.sh  \
    # Move about config and set defaults (creates /config)
    && mv /opt/rpki-validator-3/conf /config \
    && mv /opt/rpki-validator-3/preconfigured-tals/ /config \
    # Create data dir
    && mkdir /data \
    # Listen to 0.0.0.0 instead of just localhost
    && sed -i 's/server.address=localhost/server.address=0.0.0.0/g' ${CONFIG_DIR}/application.properties \
    # Load preconfigured-tals from /config
    && sed -i 's:rpki\.validator\.preconfigured\.trust\.anchors\.directory=./preconfigured-tals:rpki.validator.preconfigured.trust.anchors.directory=/config/preconfigured-tals:g' ${CONFIG_DIR}/application.properties \
    # Store data in /data
    && sed -i 's:rpki\.validator\.data\.path=.:rpki.validator.data.path=/data:g' ${CONFIG_DIR}/application.properties \
    && useradd -M -d /opt/rpki-validator-3 rpki \
    && chown -R rpki:rpki /opt/rpki-validator-3 /config /data

# Do not run as root
USER rpki

CMD ["/opt/rpki-validator-3/rpki-validator-3.sh"]
# Volumes are initialized with the files in them from container build time
VOLUME /config /data
