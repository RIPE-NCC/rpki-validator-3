

The latest version is 3.1. Feel free to give it a try:

* Docker image: https://hub.docker.com/r/ripencc/rpki-validator-3-docker
* RPM: https://ftp.ripe.net/tools/rpki/validator3/prod/centos7/repo/
* Tarball: https://ftp.ripe.net/tools/rpki/validator3/prod/generic/

Latest changes in 3.1:

* Minimize memory consumption and disk requirement by dropping Hibernate/H2 database, and use Xodus https://github.com/JetBrains/xodus as persistence instead.
* Improve responsiveness, set default RPKI object clean up grace period to 48 hours (used to be 7 days).
* Store all the ignore filters and white lists in a slurm.json file instead of the database, so now the database can be deleted any time without losing any user-configured data.
* For migration, the content of the 'db' directory should be removed and export and re-import of SLURM is required.

Detailed change logs: https://github.com/RIPE-NCC/rpki-validator-3/blob/master/rpki-validator/Changelog.txt

More information on the RPKI Validator 3 project is documented in the wiki:
https://github.com/RIPE-NCC/rpki-validator-3/wiki

Change logs will be included on the [build](https://github.com/RIPE-NCC/rpki-validator-3/blob/master/rpki-validator/Changelog.txt).
