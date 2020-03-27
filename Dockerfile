# First build step: check hash and unpack arcive
#
# The hash ensures that args change even if file name is equal, otherwise docker
# would cach an image when GENERIC_BUILD_ARCHIVE changes even though the file
# changes.
FROM openjdk:8-jre-alpine as intermediate
ARG GENERIC_BUILD_ARCHIVE
ARG GENERIC_BUILD_SHA256

COPY ${GENERIC_BUILD_ARCHIVE} /tmp/

RUN apk --no-cache add coreutils \
    && export TMPDIR=$(mktemp -d) \
    && mkdir -p /opt/rpki-validator-3 \
    && if [ -z "$GENERIC_BUILD_SHA256" ]; then echo "Supply GENERIC_BUILD_SHA256"; exit 2; fi; \
       echo -n "${GENERIC_BUILD_SHA256} /tmp/$(basename ${GENERIC_BUILD_ARCHIVE})" | sha256sum --check \ 
    && tar -zxf /tmp/$(basename $GENERIC_BUILD_ARCHIVE) -C ${TMPDIR} \
    # Move files from the dir in the archive (like rpki-validator-3.1-2020.01.13.09.31.26) to target folder:
    && mv ${TMPDIR}/*/* /opt/rpki-validator-3

# Second build step: Move files into place
FROM openjdk:8-jre-alpine
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

RUN apk --no-cache add rsync \
    # Ash instead of bash
    && sed -i 's/env bash/env ash/g'  /opt/rpki-validator-3/rpki-validator-3.sh \
    # UseContainerSupport: important
    && sed -i '/MEM_OPTIONS=/c\MEM_OPTIONS="-Xms$JVM_XMS -Xmx$JVM_XMX -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport"' /opt/rpki-validator-3/rpki-validator-3.sh  \
    # Move about config and set defaults
    && mv /opt/rpki-validator-3/conf /config \
    && mv /opt/rpki-validator-3/preconfigured-tals/ /config \
    # Listen to 0.0.0.0 instead of just localhost
    && sed -i 's/server.address=localhost/server.address=0.0.0.0/g' ${CONFIG_DIR}/application.properties \
    # Load preconfigured-tals from /config
    && sed -i 's:rpki\.validator\.preconfigured\.trust\.anchors\.directory=./preconfigured-tals:rpki.validator.preconfigured.trust.anchors.directory=/config/preconfigured-tals:g' ${CONFIG_DIR}/application.properties \
    # Store data in /data
    && sed -i 's:rpki\.validator\.data\.path=.:rpki.validator.data.path=/data:g' ${CONFIG_DIR}/application.properties

CMD ["/opt/rpki-validator-3/rpki-validator-3.sh"]
VOLUME /config /data
