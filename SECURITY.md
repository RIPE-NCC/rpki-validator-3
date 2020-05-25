## Vulnerability

After a change in our build infrastructure, the CentOS (rpm) artifact contained world-writable systemd service files 
that would allow users with write access to the machine to elevate privileges and get local code execution. 
Version affected: [3.1-2020.05.08.09.26.49](https://ftp.ripe.net/tools/rpki/validator3/archive/centos7/rpki-validator-3.1-2020.05.08.09.26.49.noarch.rpm)
Fixed version: [3.1-2020.05.22.11.25](https://ftp.ripe.net/tools/rpki/validator3/prod/centos7/repo/rpki-validator-3.1-2020.05.22.11.25.noarch.rpm)
