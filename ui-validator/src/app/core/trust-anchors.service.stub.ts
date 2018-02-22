/* Used exclusively to mock service in tests */
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/observable/of';

export class TrustAnchorsServiceStub {

  getTrustAnchors() {
    return Observable.of({
      links: {
        self: 'http://localhost:4200/trust-anchors'
      },
      data: [ {
        type: 'trust-anchor',
        id: 15,
        name: 'AfriNIC RPKI Root',
        locations: [ 'rsync://rpki.afrinic.net/repository/AfriNIC.cer' ],
        subjectPublicKeyInfo: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxsAqAhWIO+ON2Ef9oRDMpKxv+AfmSLIdLWJtjrvUyDxJPBjgR+kVrOHUeTaujygFUp49tuN5H2C1rUuQavTHvve6xNF5fU3OkTcqEzMOZy+ctkbde2SRMVdvbO22+TH9gNhKDc9l7Vu01qU4LeJHk3X0f5uu5346YrGAOSv6AaYBXVgXxa0s9ZvgqFpim50pReQe/WI3QwFKNgpPzfQL6Y7fDPYdYaVOXPXSKtx7P4s4KLA/ZWmRL/bobw/i2fFviAGhDrjqqqum+/9w1hElL/vqihVnV18saKTnLvkItA/Bf5i11Yhw2K7qv573YWxyuqCknO/iYLTR1DToBZcZUQIDAQAB',
        rsyncPrefetchUri: 'rsync://rpki.afrinic.net/repository/',
        preconfigured: false,
        initialCertificateTreeValidationRunCompleted: true,
        certificate: 'MIIEhDCCA2ygAwIBAgIJAKsRWpQDTttFMA0GCSqGSIb3DQEBCwUAMCMxITAfBgNVBAMTGEFmcmlOSUMtUm9vdC1DZXJ0aWZpY2F0ZTAeFw0xNzA5MTQxMTA0MTlaFw0yNzA5MTIxMTA0MTlaMCMxITAfBgNVBAMTGEFmcmlOSUMtUm9vdC1DZXJ0aWZpY2F0ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMbAKgIViDvjjdhH/aEQzKSsb/gH5kiyHS1ibY671Mg8STwY4EfpFazh1Hk2ro8oBVKePbbjeR9gta1LkGr0x773usTReX1NzpE3KhMzDmcvnLZG3XtkkTFXb2zttvkx/YDYSg3PZe1btNalOC3iR5N19H+brud+OmKxgDkr+gGmAV1YF8WtLPWb4KhaYpudKUXkHv1iN0MBSjYKT830C+mO3wz2HWGlTlz10ircez+LOCiwP2VpkS/26G8P4tnxb4gBoQ646qqrpvv/cNYRJS/76ooVZ1dfLGik5y75CLQPwX+YtdWIcNiu6r+e92FscrqgpJzv4mC00dQ06AWXGVECAwEAAaOCAbkwggG1ME8GA1UdIAEB/wRFMEMwQQYIKwYBBQUHDgIwNTAzBggrBgEFBQcCARYnaHR0cHM6Ly9ycGtpLmFmcmluaWMubmV0L3BvbGljeS9DUFMucGRmMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBTraA849dbHG7SxBri9BlhQEtoxtjCB1QYIKwYBBQUHAQsEgcgwgcUwUQYIKwYBBQUHMAWGRXJzeW5jOi8vcnBraS5hZnJpbmljLm5ldC9yZXBvc2l0b3J5LzA0RThCMEQ4MEY0RDExRTBCNjU3RDg5MzEzNjdBRTdELzBwBggrBgEFBQcwCoZkcnN5bmM6Ly9ycGtpLmFmcmluaWMubmV0L3JlcG9zaXRvcnkvMDRFOEIwRDgwRjREMTFFMEI2NTdEODkzMTM2N0FFN0QvNjJnUE9QWFd4eHUwc1FhNHZRWllVQkxhTWJZLm1mdDAhBggrBgEFBQcBCAEB/wQSMBCgDjAMMAoCAQACBQD/////MCcGCCsGAQUFBwEHAQH/BBgwFjAJBAIAATADAwEAMAkEAgACMAMDAQAwDQYJKoZIhvcNAQELBQADggEBAFg7z/npCp7OS/L+PEZZSXtvsQ3XacY7PJ0CHP18NMX6O5lljYhz/ZNJw2uufhFF7If+ZGMKfSEJUmDFoXX1RE7Od7LPiG/fFqIP71x1iwEhGUt7SFHU0L4mAoycthjrx0SsO0+YaeZTgm1pS/0uNHz9n8A9lLj/jqpVLahJiv2nQ/ZZMqJ/Ug7kb2jJaus4vuQhR6LSOcT2tl3/353p4uN/id7qHes9vVKQwSiO5ess126fGeURL1/KnaI7R4WJweJx+aocHd78T5VTXwYLRwVUjikbDdhm2L+Ho60PwVLQX877v5wfTA+ayuNqMVJyV25NmB5Q/zWUiY1v1QlZsxM=',
        links: {
          self: 'http://localhost:4200/trust-anchors/15'
        }
      }, {
        type: 'trust-anchor',
        id: 21334,
        name: 'LACNIC RPKI Root',
        locations: ['rsync://repository.lacnic.net/rpki/lacnic/rta-lacnic-rpki.cer'],
        subjectPublicKeyInfo: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqZEzhYK0+PtDOPfub/KRc3MeWx3neXx4/wbnJWGbNAtbYqXg3uU5J4HFzPgk/VIppgSKAhlO0H60DRP48by9gr5/yDHu2KXhOmnMg46sYsUIpfgtBS9+VtrqWziJfb+pkGtuOWeTnj6zBmBNZKK+5AlMCW1WPhrylIcB+XSZx8tk9GS/3SMQ+YfMVwwAyYjsex14Uzto4GjONALE5oh1M3+glRQduD6vzSwOD+WahMbc9vCOTED+2McLHRKgNaQf0YJ9a1jG9oJIvDkKXEqdfqDRktwyoD74cV57bW3tBAexB7GglITbInyQAsmdngtfg2LUMrcROHHP86QPZINjDQIDAQAB',
        rsyncPrefetchUri: 'rsync://repository.lacnic.net/rpki/',
        preconfigured: false,
        initialCertificateTreeValidationRunCompleted: false,
        certificate: 'MIIELDCCAxSgAwIBAgIDBhqLMA0GCSqGSIb3DQEBCwUAMCUxIzAhBgNVBAMTGnJvb3QgdHJ1c3QgYW5jaG9yIE89bGFjbmljMCAXDTEyMTAxMDEzMDAwMFoYDzIxMTIxMDEwMTMwMDAwWjAlMSMwIQYDVQQDExpyb290IHRydXN0IGFuY2hvciBPPWxhY25pYzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKmRM4WCtPj7Qzj37m/ykXNzHlsd53l8eP8G5yVhmzQLW2Kl4N7lOSeBxcz4JP1SKaYEigIZTtB+tA0T+PG8vYK+f8gx7til4TppzIOOrGLFCKX4LQUvflba6ls4iX2/qZBrbjlnk54+swZgTWSivuQJTAltVj4a8pSHAfl0mcfLZPRkv90jEPmHzFcMAMmI7HsdeFM7aOBozjQCxOaIdTN/oJUUHbg+r80sDg/lmoTG3PbwjkxA/tjHCx0SoDWkH9GCfWtYxvaCSLw5ClxKnX6g0ZLcMqA++HFee21t7QQHsQexoJSE2yJ8kALJnZ4LX4Ni1DK3EThxz/OkD2SDYw0CAwEAAaOCAWEwggFdMB0GA1UdDgQWBBT8ipyz7RhOF9MO6h4Pp2Fc5LGvRzAfBgNVHSMEGDAWgBT8ipyz7RhOF9MO6h4Pp2Fc5LGvRzAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjCBkwYIKwYBBQUHAQsEgYYwgYMwNgYIKwYBBQUHMAWGKnJzeW5jOi8vcmVwb3NpdG9yeS5sYWNuaWMubmV0L3Jwa2kvbGFjbmljLzBJBggrBgEFBQcwCoY9cnN5bmM6Ly9yZXBvc2l0b3J5LmxhY25pYy5uZXQvcnBraS9sYWNuaWMvcnRhLWxhY25pYy1ycGtpLm1mdDAYBgNVHSABAf8EDjAMMAoGCCsGAQUFBw4CMCcGCCsGAQUFBwEHAQH/BBgwFjAJBAIAATADAwEAMAkEAgACMAMDAQAwIQYIKwYBBQUHAQgBAf8EEjAQoA4wDDAKAgEAAgUA/////zANBgkqhkiG9w0BAQsFAAOCAQEAcwMJ1s0j+glgELGKjfexHaoHwu3YPaG+Jk9GdNR78IZuxGzAblZ0DDN3Ax20bMCTb7zz0m3j+pwlWpqrPXnhyaHMBJoXrTXLl+2UrdhCLf74EFKw+3nnboYqQuNQBkIoPPIrVMKA3culWhih8PKH0m5PUHY+Iv9QzJl7lUlOTDlJzsEODunxPYnf/Xh6ZDYZpji59bJSqHhq2mAfnHosvmfa/ChF/ui8z2rb4nVy/U741cCPOqe3wo0eveKwbWc0h/mv1re3f4nl5Gnwvht/M7R6hHt4ZxJcvcQV2E7OB3ZxZ44rnQamzb417WNkjNOz6Av9j5VR2zulioO5DVsFdw==',
        links: {
          self: 'http://localhost:9176/trust-anchors/21334'
        }
      }]
    })
  }

  getTrustAnchorsOverview() {
    return Observable.of({
      data: [
        {
          id: '21586',
          taName: 'AfriNIC RPKI Root',
          successful: 0,
          warnings: 7,
          errors: 0,
          lastUpdated: '2018-02-12 02:13:50'
        },
        {
          id: '3',
          taName: 'RIPE NCC RPKI Root',
          successful: 20685,
          warnings: 0,
          errors: 0,
          lastUpdated: '2018-02-12 02:13:50'
        }
      ]
    })
  }

  getTrustAnchor(id: string) {
    return Observable.of(
      {
        data : {
          type : 'trust-anchor',
          id : 8,
          name : 'APNIC RPKI Root',
          locations : [ 'rsync://rpki.apnic.net/repository/apnic-rpki-root-iana-origin.cer' ],
          subjectPublicKeyInfo : 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAx9RWSL61YAAYumEiU8z8qH2ETVIL01ilxZlzIL9JYSORMN5Cmtf8V2JblIealSqgOTGjvSjEsiV73s67zYQI7C/iSOb96uf3/s86NqbxDiFQGN8qG7RNcdgVuUlAidl8WxvLNI8VhqbAB5uSg/MrLeSOvXRja041VptAxIhcGzDMvlAJRwkrYK/Mo8P4E2rSQgwqCgae0ebY1CsJ3Cjfi67C1nw7oXqJJovvXJ4apGmEv8az23OLC6Ki54Ul/E6xk227BFttqFV3YMtKx42HcCcDVZZy01n7JjzvO8ccaXmHIgR7utnqhBRNNq5Xc5ZhbkrUsNtiJmrZzVlgU6Ou0wIDAQAB',
          rsyncPrefetchUri : 'rsync://rpki.apnic.net/member_repository/',
          preconfigured : false,
          initialCertificateTreeValidationRunCompleted : false,
          certificate : 'MIIEtzCCA5+gAwIBAgIJAKGXJn07P2TvMA0GCSqGSIb3DQEBCwUAMCYxJDAiBgNVBAMTG2FwbmljLXJwa2ktcm9vdC1pYW5hLW9yaWdpbjAeFw0xODAxMjMyMjM0MTFaFw0yMzAxMjIyMjM0MTFaMCYxJDAiBgNVBAMTG2FwbmljLXJwa2ktcm9vdC1pYW5hLW9yaWdpbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMfUVki+tWAAGLphIlPM/Kh9hE1SC9NYpcWZcyC/SWEjkTDeQprX/FdiW5SHmpUqoDkxo70oxLIle97Ou82ECOwv4kjm/ern9/7POjam8Q4hUBjfKhu0TXHYFblJQInZfFsbyzSPFYamwAebkoPzKy3kjr10Y2tONVabQMSIXBswzL5QCUcJK2CvzKPD+BNq0kIMKgoGntHm2NQrCdwo34uuwtZ8O6F6iSaL71yeGqRphL/Gs9tziwuioueFJfxOsZNtuwRbbahVd2DLSseNh3AnA1WWctNZ+yY87zvHHGl5hyIEe7rZ6oQUTTauV3OWYW5K1LDbYiZq2c1ZYFOjrtMCAwEAAaOCAeYwggHiMEoGA1UdIAEB/wRAMD4wPAYIKwYBBQUHDgIwMDAuBggrBgEFBQcCARYiaHR0cHM6Ly93d3cuYXBuaWMubmV0L1JQS0kvQ1BTLnBkZjAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUC5zKkN0Neoo3ZmsZIX/g2EA3t6IwggEGBggrBgEFBQcBCwSB+TCB9jBPBggrBgEFBQcwBYZDcnN5bmM6Ly9ycGtpLmFwbmljLm5ldC9yZXBvc2l0b3J5LzgzOERCMjE0MTY2NTExRTJCM0JDMjg2MTcyRkQxRkYyLzBuBggrBgEFBQcwCoZicnN5bmM6Ly9ycGtpLmFwbmljLm5ldC9yZXBvc2l0b3J5LzgzOERCMjE0MTY2NTExRTJCM0JDMjg2MTcyRkQxRkYyL0M1ektrTjBOZW9vM1ptc1pJWF9nMkVBM3Q2SS5tZnQwMwYIKwYBBQUHMA2GJ2h0dHBzOi8vcnJkcC5hcG5pYy5uZXQvbm90aWZpY2F0aW9uLnhtbDAhBggrBgEFBQcBCAEB/wQSMBCgDjAMMAoCAQECBQD/////MCcGCCsGAQUFBwEHAQH/BBgwFjAJBAIAATADAwEAMAkEAgACMAMDAQAwDQYJKoZIhvcNAQELBQADggEBAIHhf6MAHgKL11tYu+0OCjfsSRSrV959S7H0mV81gm/4E7H5jEZyTPRGCGYZMPB4A5r1N5s9BvP5EeNTeJQMNu5vN8KgoSjHYVL3ufSPjvlIegJb1868JQh1cBpImamwpkf78o3gXo/kSUOtYCscsY42ybTuesfNUftRWBUhdo+qmj5WEcGdkUqmjdmtMmeDRNwMjEE/xr+3VrBLT02/BfnZpaSAotPD7m2jjQxgIDxLtLIyIAstMXWkRjZi/8Cm17ZYK83d+PIeHmftIlY2MIJU7qztz3/aXWC5BJdKzD7g12o4tzB63/ZJN+iW/WzLXgta974UT+rNP2BQ/YEOaHo=',
          links : {
            self : 'http://localhost:4200/api/trust-anchors/8'
          }
        },
        includes : [ {
          type : 'trust-anchor-validation-run',
          startedAt : '2018-02-21T13:32:57.694Z',
          completedAt : '2018-02-21T13:33:00.283Z',
          status : 'SUCCEEDED',
          validationChecks : [ ],
          links : {
            self : 'http://localhost:4200/api/validation-runs/10'
          }
        } ]
      }
    )
  }

  getTrustAnchorValidationChecks(id: string) {
    return Observable.of(
      {
         links : {
           first : 'http://localhost:4200/api/trust-anchors/8/validation-checks?startFrom=0&pageSize=10&search=&sortBy=location&sortDirection=asc',
           prev : 'http://localhost:4200/api/trust-anchors/8/validation-checks?startFrom=0&pageSize=10&search=&sortBy=location&sortDirection=asc',
           next : 'http://localhost:4200/api/trust-anchors/8/validation-checks?startFrom=0&pageSize=10&search=&sortBy=location&sortDirection=asc',
           last : 'http://localhost:4200/api/trust-anchors/8/validation-checks?startFrom=0&pageSize=10&search=&sortBy=location&sortDirection=asc'
        },
        data : {
          validationChecks : [ {
            location : 'rsync://rpki.apnic.net/repository/B322A5F41D6611E2A3F27F7C72FD1FF2/U0ElRP4CPi5oGQIYxQ_lWFazoLk.cer',
            status : 'WARNING',
            key : 'validator.rpki.repository.pending',
            parameters : [ 'rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/' ],
            formattedMessage : 'The RPKI repository rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/ is still pending'
          }, {
            location : 'rsync://rpki.apnic.net/repository/B3A24F201D6611E28AC8837C72FD1FF2/oWhEB7GUTj5ZqlXo7X2VbNrJ9xw.cer',
            status : 'WARNING',
            key : 'validator.rpki.repository.pending',
            parameters : [ 'rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/' ],
            formattedMessage : 'The RPKI repository rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/ is still pending'
          }, {
            location : 'rsync://rpki.apnic.net/repository/B3A24F201D6611E28AC8837C72FD1FF2/wdVKNwz_y3md79I8u3zxaKiSaX8.cer',
            status : 'WARNING',
            key : 'validator.rpki.repository.pending',
            parameters : [ 'https://rpki.cnnic.cn/rrdp/notify.xml' ],
            formattedMessage : 'The RPKI repository https://rpki.cnnic.cn/rrdp/notify.xml is still pending'
          }, {
            location : 'rsync://rpki.apnic.net/repository/B41FE6101D6611E2A62F877C72FD1FF2/V9yZdt-1j-vKv_Awu9S_t5Lpfg8.cer',
            status : 'WARNING',
            key : 'validator.rpki.repository.pending',
            parameters : [ 'rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/' ],
            formattedMessage : 'The RPKI repository rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/ is still pending'
          }, {
            location : 'rsync://rpki.apnic.net/repository/B527EF581D6611E2BB468F7C72FD1FF2/F-TkmJq5ThVb8f2By1hVIMq_0zc.cer',
            status : 'WARNING',
            key : 'validator.rpki.repository.pending',
            parameters : [ 'rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/' ],
            formattedMessage : 'The RPKI repository rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/ is still pending'
          }, {
            location : 'rsync://rpki.apnic.net/repository/B527EF581D6611E2BB468F7C72FD1FF2/XIxCfob_Rv44hFi30IoT96bb2QQ.cer',
            status : 'WARNING',
            key : 'validator.rpki.repository.pending',
            parameters : [ 'https://rpki.cnnic.cn/rrdp/notify.xml' ],
            formattedMessage : 'The RPKI repository https://rpki.cnnic.cn/rrdp/notify.xml is still pending'
          } ]
        },
        metadata : {
          totalCount : 6
        }
      }
    )
  }

  getRepositories(id: string) {
    return Observable.of(
      {
        links: {
          first: 'http://localhost:4200/api/rpki-repositories?startFrom=0&pageSize=10&ta=72{&status}',
          prev: 'http://localhost:4200/api/rpki-repositories?startFrom=0&pageSize=10&ta=72{&status}',
          next: 'http://localhost:4200/api/rpki-repositories?startFrom=0&pageSize=10&ta=72{&status}',
          last: 'http://localhost:4200/api/rpki-repositories?startFrom=0&pageSize=10&ta=72{&status}'
        },
        data: [{
          type: 'RSYNC',
          id: 71,
          locationURI: 'rsync://rpki.apnic.net/member_repository/',
          status: 'DOWNLOADED',
          lastDownloadedAt: '2018-02-21T19:03:29.077Z',
          links: {
            self: 'http://localhost:4200/api/rpki-repositories/71'
          }
        }, {
          type: 'RRDP',
          id: 79,
          locationURI: 'https://rrdp.apnic.net/notification.xml',
          status: 'DOWNLOADED',
          lastDownloadedAt: '2018-02-21T19:03:57.462Z',
          rrdpSessionId: '183a3b96-db77-4077-82d4-976ffa65bb2f',
          rrdpSerial: 28595,
          links: {
            self: 'http://localhost:4200/api/rpki-repositories/79'
          }
        }, {
          type: 'RSYNC',
          id: 9326,
          locationURI: 'rsync://rpki-repository.nic.ad.jp/ap/A91A73810000/',
          status: 'PENDING',
          links: {
            self: 'http://localhost:4200/api/rpki-repositories/9326'
          }
        }, {
          type: 'RRDP',
          id: 12383,
          locationURI: 'https://rpki.cnnic.cn/rrdp/notify.xml',
          status: 'FAILED',
          lastDownloadedAt: '2018-02-21T19:04:05.146Z',
          links: {
            self: 'http://localhost:4200/api/rpki-repositories/12383'
          }
        }],
        metadata: {
          totalCount: 4
        }
      }
    )
  }
}
