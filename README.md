**We will archive RIPE NCC RPKI Validator 2.x and 3.x on 1 July 2021. Please migrate to alternative RPKI Relying Party software.**

These are a number of actively maintained Open Source alternatives:
  * [Routinator](https://github.com/NLnetLabs/routinator/)
  * [FORT](https://github.com/NICMx/FORT-validator/)
  * [OctoRPKI](https://github.com/cloudflare/cfrpki)
  * [RPKI-client](https://rpki-client.org/)
  * [Prover](https://github.com/lolepezy/rpki-prover)
  * [Rpstir2](https://github.com/bgpsecurity/rpstir2)

You can read more about archiving of the RIPE NCC RPKI Validator in our [RIPE labs article](https://labs.ripe.net/Members/nathalie_nathalie/life-cycle-of-the-ripe-ncc-rpki-validator-1)

---------------------------------------

The latest version is 3.2. Feel free to give it a try using one of our builds:

* Docker image: https://hub.docker.com/r/ripencc/rpki-validator-3-docker
* RPM: https://ftp.ripe.net/tools/rpki/validator3/prod/centos7/repo/
* DEB: https://ftp.ripe.net/tools/rpki/validator3/prod/deb/
* Tarball: https://ftp.ripe.net/tools/rpki/validator3/prod/generic/

Or follow the step-by-step [installation instructions](https://github.com/RIPE-NCC/rpki-validator-3/wiki/RIPE-NCC-RPKI-Validator-3-Production).

Changes in 3.2:
---------------
  * Strict validation by default as specified [draft-ietf-sidrops-6486bis-03](https://www.ietf.org/archive/id/draft-ietf-sidrops-6486bis-03.txt),
    however objects mentioned in the manifest are only checked against the SHA256 hash in the manifest. So objects that match the hash but are
    otherwise invalid or unrecognized do not cause the complete manifest to fail validation.
  * Validate manifest entry filenames against pattern [a-zA-Z_-]+\.[a-z]{3}.
  * Automatically re-run validation when objects are about to expire.
  * Decrease bootstrap time.
  * Dependency updates and bug fixes.

Latest changes in 3.1:
----------------------

* Reduced CPU consumption by +-25%.
* Improvements in memory consumption.
* Prometheus endpoint on `/metrics` for both validator and rtr-server.


We keep a [change log](https://github.com/RIPE-NCC/rpki-validator-3/blob/master/rpki-validator/Changelog.txt)
of changes that are relevant to our users and include this in the build.

More information on the RPKI Validator 3 project is documented in the [wiki](https://github.com/RIPE-NCC/rpki-validator-3/wiki).
