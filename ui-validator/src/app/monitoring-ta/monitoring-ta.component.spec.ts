import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {Observable} from 'rxjs/Observable';
import {ActivatedRoute} from '@angular/router';

import {MonitoringTaComponent} from './monitoring-ta.component';
import {SharedModule} from '../shared/shared.module';
import {TrustAnchorsService} from '../core/trust-anchors.service';
import {ValidationDetailsComponent} from "./validation-details/validation-details.component";

class TrustAnchorsServiceStub {
  getTrustAnchor(id: string) {
    return Observable.of(
      {
        data: {
          type: 'trust-anchor',
          id: 21586,
          name: 'AfriNIC RPKI Root',
          locations: [ 'rsync://rpki.afrinic.net/repository/AfriNIC.cer' ],
          subjectPublicKeyInfo: 'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxsAqAhWIO+ON2Ef9oRDMpKxv+AfmSLIdLWJtjrvUyDxJPBjgR+kVrOHUeTaujygFUp49tuN5H2C1rUuQavTHvve6xNF5fU3OkTcqEzMOZy+ctkbde2SRMVdvbO22+TH9gNhKDc9l7Vu01qU4LeJHk3X0f5uu5346YrGAOSv6AaYBXVgXxa0s9ZvgqFpim50pReQe/WI3QwFKNgpPzfQL6Y7fDPYdYaVOXPXSKtx7P4s4KLA/ZWmRL/bobw/i2fFviAGhDrjqqqum+/9w1hElL/vqihVnV18saKTnLvkItA/Bf5i11Yhw2K7qv573YWxyuqCknO/iYLTR1DToBZcZUQIDAQAB',
          rsyncPrefetchUri: 'rsync://rpki.afrinic.net/repository/',
          preconfigured: false,
          initialCertificateTreeValidationRunCompleted: true,
          certificate: 'MIIEhDCCA2ygAwIBAgIJAKsRWpQDTttFMA0GCSqGSIb3DQEBCwUAMCMxITAfBgNVBAMTGEFmcmlOSUMtUm9vdC1DZXJ0aWZpY2F0ZTAeFw0xNzA5MTQxMTA0MTlaFw0yNzA5MTIxMTA0MTlaMCMxITAfBgNVBAMTGEFmcmlOSUMtUm9vdC1DZXJ0aWZpY2F0ZTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMbAKgIViDvjjdhH/aEQzKSsb/gH5kiyHS1ibY671Mg8STwY4EfpFazh1Hk2ro8oBVKePbbjeR9gta1LkGr0x773usTReX1NzpE3KhMzDmcvnLZG3XtkkTFXb2zttvkx/YDYSg3PZe1btNalOC3iR5N19H+brud+OmKxgDkr+gGmAV1YF8WtLPWb4KhaYpudKUXkHv1iN0MBSjYKT830C+mO3wz2HWGlTlz10ircez+LOCiwP2VpkS/26G8P4tnxb4gBoQ646qqrpvv/cNYRJS/76ooVZ1dfLGik5y75CLQPwX+YtdWIcNiu6r+e92FscrqgpJzv4mC00dQ06AWXGVECAwEAAaOCAbkwggG1ME8GA1UdIAEB/wRFMEMwQQYIKwYBBQUHDgIwNTAzBggrBgEFBQcCARYnaHR0cHM6Ly9ycGtpLmFmcmluaWMubmV0L3BvbGljeS9DUFMucGRmMA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBTraA849dbHG7SxBri9BlhQEtoxtjCB1QYIKwYBBQUHAQsEgcgwgcUwUQYIKwYBBQUHMAWGRXJzeW5jOi8vcnBraS5hZnJpbmljLm5ldC9yZXBvc2l0b3J5LzA0RThCMEQ4MEY0RDExRTBCNjU3RDg5MzEzNjdBRTdELzBwBggrBgEFBQcwCoZkcnN5bmM6Ly9ycGtpLmFmcmluaWMubmV0L3JlcG9zaXRvcnkvMDRFOEIwRDgwRjREMTFFMEI2NTdEODkzMTM2N0FFN0QvNjJnUE9QWFd4eHUwc1FhNHZRWllVQkxhTWJZLm1mdDAhBggrBgEFBQcBCAEB/wQSMBCgDjAMMAoCAQACBQD/////MCcGCCsGAQUFBwEHAQH/BBgwFjAJBAIAATADAwEAMAkEAgACMAMDAQAwDQYJKoZIhvcNAQELBQADggEBAFg7z/npCp7OS/L+PEZZSXtvsQ3XacY7PJ0CHP18NMX6O5lljYhz/ZNJw2uufhFF7If+ZGMKfSEJUmDFoXX1RE7Od7LPiG/fFqIP71x1iwEhGUt7SFHU0L4mAoycthjrx0SsO0+YaeZTgm1pS/0uNHz9n8A9lLj/jqpVLahJiv2nQ/ZZMqJ/Ug7kb2jJaus4vuQhR6LSOcT2tl3/353p4uN/id7qHes9vVKQwSiO5ess126fGeURL1/KnaI7R4WJweJx+aocHd78T5VTXwYLRwVUjikbDdhm2L+Ho60PwVLQX877v5wfTA+ayuNqMVJyV25NmB5Q/zWUiY1v1QlZsxM=',
          links: {
            self: 'http://localhost:4200/trust-anchors/21586'
          }
        },
        includes: [ {
          type: 'trust-anchor-validation-run',
          startedAt: '2018-02-12T14:23:39.018Z',
          completedAt: '2018-02-12T14:23:40.648Z',
          status: 'SUCCEEDED',
          validationChecks: [ ],
          links: {
            self: 'http://localhost:4200/validation-runs/22337',
            'trust-anchor': 'http://localhost:4200/trust-anchors/21586'
          }
        }]
      }
    )
  }
}

describe('MonitoringTaComponent', () => {
  let component: MonitoringTaComponent;
  let fixture: ComponentFixture<MonitoringTaComponent>;
  let trustAnchorsService: TrustAnchorsService;
  let trustAnchorsSpy;
  let mockActivatedRoute = {
    snapshot: {
      url: [{path: 'trust-anchors'}, {path: 'monitor'}, {path: 21586}]
    }
  };

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      providers: [
        {provide: ActivatedRoute, useValue: mockActivatedRoute},
        {provide: TrustAnchorsService, useClass: TrustAnchorsServiceStub}
      ],
      declarations: [MonitoringTaComponent, ValidationDetailsComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(MonitoringTaComponent);
    component = fixture.componentInstance;
    trustAnchorsService = TestBed.get(TrustAnchorsService);
    trustAnchorsSpy = spyOn(trustAnchorsService, 'getTrustAnchor').and.callThrough();
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call trustAnchorsService on init', () => {
    expect(trustAnchorsSpy).toHaveBeenCalled();
    expect(trustAnchorsSpy).toHaveBeenCalledWith(21586);
    fixture.detectChanges();
    expect(component.monitoringTrustAnchor).not.toBeNull();
  });
});
