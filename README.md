# rpki-validator-3

Welcome to the RIPE NCC RPKI Validator 3.

This project is still under heavy development. If you want to help us beta test, please have a look at this wiki page:
https://github.com/RIPE-NCC/rpki-validator-3/wiki/RIPE-NCC-RPKI-Validator-3-beta-tester-page

This project consists of the following projects:

* rpki-validator

The validation engine itself. The validator is set up to run as a daemon, and has the following features:
** Supports all current RPKI objects: certificates, manifests, CRLs, ROAs, router certificates and ghostbuster records
** Supports the RRDP delta protocol
** Supports caching RPKI data in case a repository is unavailable
** Uses an asynchronous strategy to retrieve (often delegated) repositories, so that unavaible repositories do not block validation
** Features an API
** Work on a UI is planned

* rpki-rtr-server

A separate daemon, that allows routers to connect using the RPKI-RTR protocol. It's set up as a separate instance
because not everyone needs to run this, but more importantly, if you do need to run this then a separate daemon
allows one to run more than one instance for redundancy (it keeps state even when the validator is down).




