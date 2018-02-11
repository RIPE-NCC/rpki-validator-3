import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {Observable} from 'rxjs/Observable';
import {ActivatedRoute} from '@angular/router';

import {MonitoringTaComponent} from './monitoring-ta.component';
import {SharedModule} from '../shared/shared.module';
import {TrustAnchorsService} from '../core/trust-anchors.service';

class TrustAnchorsServiceStub {
  getTrustAnchor(id: string) {
    return Observable.of({
      links: {
        self: 'http://localhost:4200/trust-anchors'
      },
      data: [{
        type: 'trust-anchor',
        id: 12,
        name: 'LACNIC RPKI Root',
        locations: ['rsync://repository.lacnic.net/rpki/lacnic/rta-lacnic-rpki.cer'],
        subjectPublicKeyInfo: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqZEzhYK0+PtDOPfub/KRc3MeWx3neXx4/wbnJWGbNAtbYqXg3uU5J4HFzPgk/VIppgSKAhlO0H60DRP48by9gr5/yDHu2KXhOmnMg46sYsUIpfgtBS9+VtrqWziJfb+pkGtuOWeTnj6zBmBNZKK+5AlMCW1WPhrylIcB+XSZx8tk9GS/3SMQ+YfMVwwAyYjsex14Uzto4GjONALE5oh1M3+glRQduD6vzSwOD+WahMbc9vCOTED+2McLHRKgNaQf0YJ9a1jG9oJIvDkKXEqdfqDRktwyoD74cV57bW3tBAexB7GglITbInyQAsmdngtfg2LUMrcROHHP86QPZINjDQIDAQAB',
        rsyncPrefetchUri: 'rsync://repository.lacnic.net/rpki/',
        preconfigured: false,
        initialCertificateTreeValidationRunCompleted: true,
        certificate: 'MIIELDCCAxSgAwIBAgIDBhqLMA0GCSqGSIb3DQEBCwUAMCUxIzAhBgNVBAMTGnJvb3QgdHJ1c3QgYW5jaG9yIE89bGFjbmljMCAXDTEyMTAxMDEzMDAwMFoYDzIxMTIxMDEwMTMwMDAwWjAlMSMwIQYDVQQDExpyb290IHRydXN0IGFuY2hvciBPPWxhY25pYzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKmRM4WCtPj7Qzj37m/ykXNzHlsd53l8eP8G5yVhmzQLW2Kl4N7lOSeBxcz4JP1SKaYEigIZTtB+tA0T+PG8vYK+f8gx7til4TppzIOOrGLFCKX4LQUvflba6ls4iX2/qZBrbjlnk54+swZgTWSivuQJTAltVj4a8pSHAfl0mcfLZPRkv90jEPmHzFcMAMmI7HsdeFM7aOBozjQCxOaIdTN/oJUUHbg+r80sDg/lmoTG3PbwjkxA/tjHCx0SoDWkH9GCfWtYxvaCSLw5ClxKnX6g0ZLcMqA++HFee21t7QQHsQexoJSE2yJ8kALJnZ4LX4Ni1DK3EThxz/OkD2SDYw0CAwEAAaOCAWEwggFdMB0GA1UdDgQWBBT8ipyz7RhOF9MO6h4Pp2Fc5LGvRzAfBgNVHSMEGDAWgBT8ipyz7RhOF9MO6h4Pp2Fc5LGvRzAPBgNVHRMBAf8EBTADAQH/MA4GA1UdDwEB/wQEAwIBBjCBkwYIKwYBBQUHAQsEgYYwgYMwNgYIKwYBBQUHMAWGKnJzeW5jOi8vcmVwb3NpdG9yeS5sYWNuaWMubmV0L3Jwa2kvbGFjbmljLzBJBggrBgEFBQcwCoY9cnN5bmM6Ly9yZXBvc2l0b3J5LmxhY25pYy5uZXQvcnBraS9sYWNuaWMvcnRhLWxhY25pYy1ycGtpLm1mdDAYBgNVHSABAf8EDjAMMAoGCCsGAQUFBw4CMCcGCCsGAQUFBwEHAQH/BBgwFjAJBAIAATADAwEAMAkEAgACMAMDAQAwIQYIKwYBBQUHAQgBAf8EEjAQoA4wDDAKAgEAAgUA/////zANBgkqhkiG9w0BAQsFAAOCAQEAcwMJ1s0j+glgELGKjfexHaoHwu3YPaG+Jk9GdNR78IZuxGzAblZ0DDN3Ax20bMCTb7zz0m3j+pwlWpqrPXnhyaHMBJoXrTXLl+2UrdhCLf74EFKw+3nnboYqQuNQBkIoPPIrVMKA3culWhih8PKH0m5PUHY+Iv9QzJl7lUlOTDlJzsEODunxPYnf/Xh6ZDYZpji59bJSqHhq2mAfnHosvmfa/ChF/ui8z2rb4nVy/U741cCPOqe3wo0eveKwbWc0h/mv1re3f4nl5Gnwvht/M7R6hHt4ZxJcvcQV2E7OB3ZxZ44rnQamzb417WNkjNOz6Av9j5VR2zulioO5DVsFdw==',
        links: {
          self: 'http://localhost:4200/trust-anchors/12'
        }
      }]
    })
  }
}

describe('MonitoringTaComponent', () => {
  let component: MonitoringTaComponent;
  let fixture: ComponentFixture<MonitoringTaComponent>;

  beforeEach(async(() => {
    let mockActivatedRoute = {
      snapshot: {
        url: [{path: 'trust-anchors'}, {path: 'monitor'}, {path: 15}]
      }
    };

    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      providers: [
        {provide: ActivatedRoute, useValue: mockActivatedRoute},
        {provide: TrustAnchorsService, useClass: TrustAnchorsServiceStub}
      ],
      declarations: [MonitoringTaComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(MonitoringTaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
