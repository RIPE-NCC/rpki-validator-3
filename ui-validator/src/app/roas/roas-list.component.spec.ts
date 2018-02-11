import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {NgbModule} from '@ng-bootstrap/ng-bootstrap';
import {HttpClientModule} from '@angular/common/http';
import {CoreModule} from '../core/core.module';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/observable/of';

import {RoasListComponent} from './roas-list.component';
import {SharedModule} from '../shared/shared.module';
import {RoasService} from './roas.service';
import {TrustAnchorsService} from '../core/trust-anchors.service';

class RoasServiceStub {
  getRoas(firstRoa: string, page: number) {
    return Observable.of(
      {
        links: {
          first: 'http://localhost:4200/roas/?startFrom=0&pageSize=10',
          prev: 'http://localhost:4200/roas/?startFrom=80&pageSize=10',
          next: 'http://localhost:4200/roas/?startFrom=90&pageSize=10',
          last: 'http://localhost:4200/roas/?startFrom=90&pageSize=10'
        },
        data: [{
          asn: '33764',
          prefix: '2001:42d0::/48',
          length: 48,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F3634D22/1D5F7C26047311E580C18A06F8AEA228/46369958DA7311E7A052751DF8AEA228.roa'
        }, {
          asn: '33764',
          prefix: '2001:43f8:d00::/48',
          length: 48,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F3634D22/1D5F7C26047311E580C18A06F8AEA228/BA6B8560DA7111E7B4C12E1AF8AEA228.roa'
        }, {
          asn: '36874',
          prefix: '196.6.121.0/24',
          length: 24,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F36472E5/8DB6547A56D711E585356D60F8AEA228/0475EE6856D811E5B0E8AA60F8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '154.72.112.0/20',
          length: 24,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F36E635C/4FBA0B42035811E7A5CA0C2EF8AEA228/3A4AB3520E4C11E7804ACA3FF8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '156.38.64.0/19',
          length: 24,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F3668231/7002A3AC6B8E11E79C20EC65F8AEA228/DA08B89A6B8E11E7952D2166F8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '160.119.160.0/19',
          length: 24,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F366F191/2E3AA74E07F711E798F5CC0AF8AEA228/B6C8C0D60E4C11E7B18E4B41F8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '196.250.64.0/18',
          length: 24,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F36E635C/4FBA0B42035811E7A5CA0C2EF8AEA228/C4900AE60E4A11E785F0EA3DF8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '2c0f:f038::/32',
          length: 48,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F366F191/1DA5A51E07F711E79CDBB30AF8AEA228/E7F9C2D60E4C11E795956341F8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '2c0f:f0f8::/32',
          length: 48,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F3668231/3AA8BA206B8E11E793C1C565F8AEA228/99C2D1746B9011E7A18C6069F8AEA228.roa'
        }, {
          asn: '36924',
          prefix: '2c0f:f260::/32',
          length: 48,
          trustAnchor: 'AfriNIC RPKI Root',
          uri: 'rsync://rpki.afrinic.net/repository/member_repository/F36E635C/4FBA0B42035811E7A5CA0C2EF8AEA228/7075A0180E4C11E7A368E93FF8AEA228.roa'
        }
        ]
      })
  }
}

class TrustAnchorsServiceStub {
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
}


describe('RoasListComponent', () => {
  let component: RoasListComponent;
  let fixture: ComponentFixture<RoasListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        NgbModule.forRoot(),
        TranslateModule.forRoot(),
        HttpClientModule,
        CoreModule
      ],
      providers: [
        {provide: RoasService, useClass: RoasServiceStub},
        {provide: TrustAnchorsService, useClass: TrustAnchorsServiceStub}
      ],
      declarations: [RoasListComponent]
    })
      .compileComponents();
    fixture = TestBed.createComponent(RoasListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
