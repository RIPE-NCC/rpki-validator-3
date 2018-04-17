RIPE NCC RPKI Validator 3 - Using the Generic Build
====================================================

You can run the validator using the following command in this directory:

    ./rpki-validator-3.sh

This script will assume that all resources can be found where this package is unpacked, and that java is available on
the path. It use port 8080 by default for the UI and API interfaces which can be accessed as follows:

    UI  http://localhost:8080/
    API http://localhost:8080/swagger-ui.html#/

See below about changing these settings.

Automate running as a Daemon
============================

The provided shell script is designed to be used with systemd on Centos 7, but this should not be very different from
integrating with systemd on other systems. You can find the centos 7 service file we use for this here:

https://raw.githubusercontent.com/RIPE-NCC/rpki-validator-3/master/rpki-validator/src/main/resources/packaging/centos7/etc/systemd/system/rpki-validator-3.service

If you use this, make sure to review the Environment settings for the location of resources (see below).

For other systems you may want to wrap the provided script and execute it in the background, while saving a .pid file,
etc.. or copy over the relevant bits from this script to something that works for your environment. The script is only
concerned with configuration that is needed before running the executable (locations, and JVM memory settings). These
should be fairly stable. Runtime configuration for the application itself is kept in a configuration file.


Relocating Resources:
=====================
If you want to use an explicit java version (we test with Openjdk 8), then set the following environment variable:
JAVA_CMD=/path/to/bin/java

If you wish to relocate the Java package (default: ./lib/rpki-validator-3.jar) of the validator then set:
JAR=/path/to/rpki-validator-3.jar

If you wish to relocate the configuration directory (default: ./conf), then set:
CONFIG_DIR=/path/to/conf

Finally also review the configuration file itself, default: ./conf/application.properties. This file contains
configuration for where the embedded database keeps its data, and where rsync can store objects retrieved. You can
also change the HTTP port here. Note that HTTPS is not supported. We recommend that you use an industry standard
proxy (e.g. apache or nginx) for this, and disallow direct access to the HTTP port.

Do NOT modify ./conf/defaults-application.properties. This may include new required properties (with defaults) in
future releases. Make sure that it is copied when a new version is installed. Besides defaults it is also provided
as a reference to settings you can override locally in ./conf/application.properties
