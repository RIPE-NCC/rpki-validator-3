#
# Two step Dockerfile
#   - first build step: check hash and unpack archive
#   - second build step: install dependencies, move files around, adjust config
#
# Unpacking in a separate build step makes sure the archive that is COPY-d does
# not become a layer in the final image.
#
FROM library/openjdk:11-slim as intermediate

ARG GENERIC_BUILD_ARCHIVE

COPY ${GENERIC_BUILD_ARCHIVE} /tmp/

RUN mkdir -p /opt/rpki-validator-3 \
    && tar -zxf /tmp/$(basename $GENERIC_BUILD_ARCHIVE) -C /opt/rpki-validator-3/ --strip-components=1

# Second build step: Move files into place
FROM library/openjdk:11-slim
# Keep the file name and sha256 in the metadata
ARG GENERIC_BUILD_ARCHIVE
LABEL validation.archive.file="$(basename ${GENERIC_BUILD_ARCHIVE})"

# Webserver on 8080
EXPOSE 8080

# JVM memory settings
ENV JVM_XMS=""
ENV JVM_XMX=""
# Used by `rpki-validator-3.sh`
ENV CONFIG_DIR="/config"

COPY --from=intermediate /opt/rpki-validator-3 /opt/rpki-validator-3
WORKDIR /opt/rpki-validator-3

# S6 init-like system for proper <C-c>
ADD https://github.com/just-containers/s6-overlay/releases/download/v1.21.8.0/s6-overlay-amd64.tar.gz /tmp/
RUN tar xzf /tmp/s6-overlay-amd64.tar.gz -C /

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
    # And rsync in /data/rsync
    && sed -i 's:rpki\.validator\.rsync\.local.storage\.directory=.:rpki.validator.rsync.local.storage.directory=/data/:g' ${CONFIG_DIR}/application.properties \
    && useradd -M -d /opt/rpki-validator-3 rpki

# https://github.com/just-containers/s6-overlay functionality
RUN echo "/data true rpki,32768:32768 0666 0777\n\
/config true rpki,32768:32768 0666 0777\n" > /etc/fix-attrs.d/01-rpki-validator-3

# Clean up xodus lockfile if it exists -- would block startup if it was there after
# container was killed.
RUN echo "#!/usr/bin/execlineb -P\n\
rm -f /data/db/xd.lck\n" > /etc/cont-init.d/02-remove-xodus-lockfile

# rpki-validator-3 process in container runs as daemon user
ENTRYPOINT ["/init"]
CMD s6-setuidgid rpki /opt/rpki-validator-3/rpki-validator-3.sh
# Volumes are initialized with the files in them from container build time
VOLUME /config /data
