RIPE NCC RPKI RTR Server - Using the Generic Build
====================================================

You can run the validator using the following command in this directory:

    ./rpki-rtr-server.sh

This script will assume that all resources can be found where this package is unpacked, and that java is available on
the path. It will run the RTR service on port 8323, the API will be available on port 8081. It will assume that you
are running a RIPE NCC RPKI Validator 3 on localhost port 8080.

You can access the API here:
http://localhost:8081/swagger-ui.html#/

See below about changing these settings.


Automate running as a Daemon
============================

The provided shell script is designed to be used with systemd on Centos 7, but this should not be very different from
integrating with systemd on other systems. You can find the centos 7 service file we use for this here:

https://raw.githubusercontent.com/RIPE-NCC/rpki-validator-3/master/rpki-rtr-server/src/main/resources/packaging/centos7/etc/systemd/system/rpki-rtr-server.service

If you use this, make sure to review the Environment settings for the location of resources (see below).

For other systems you may want to wrap the provided script and execute it in the background, while saving a .pid file,
etc.. or copy over the relevant bits from this script to something that works for your environment. The script is only
concerned with configuration that is needed before running the executable (locations, and JVM memory settings). These
should be fairly stable. Runtime configuration for the application itself is kept in a configuration file.


Relocating Resources:
=====================
If you want to use an explicit java version (we test with Openjdk 8), then set the following environment variable:
JAVA_CMD=/path/to/bin/java

If you wish to relocate the Java package (default: ./lib/rpki-rtr-server.jar) of the validator then set:
JAR=/path/to/rpki-validator-3.jar

If you wish to relocate the configuration directory (default: ./conf), then set:
CONFIG_DIR=/path/to/conf

Finally also review the configuration file itself, default: ./conf/application.properties. Here you can change
the ports the RPKI RTR Server should use, and the URL for the validator.
