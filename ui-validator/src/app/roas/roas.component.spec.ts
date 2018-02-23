import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {Observable} from 'rxjs/Observable';
import 'rxjs/add/observable/of';

import {RoasComponent} from './roas.component';
import {SharedModule} from '../shared/shared.module';
import {RoasService} from './roas.service';
import {TrustAnchorsService} from '../core/trust-anchors.service';
import {ExportComponent} from "./export/export.component";
import {TrustAnchorsServiceStub} from "../core/trust-anchors.service.stub";

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
        ],
        metadata: {
          totalCount: 240
        }
      })
  }
}

describe('RoasComponent', () => {
  let component: RoasComponent;
  let fixture: ComponentFixture<RoasComponent>;
  let roasService: RoasService;
  let roasSpy;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      providers: [
        {provide: RoasService, useClass: RoasServiceStub},
        {provide: TrustAnchorsService, useClass: TrustAnchorsServiceStub}
      ],
      declarations: [RoasComponent, ExportComponent]
    })
    .compileComponents();
    fixture = TestBed.createComponent(RoasComponent);
    component = fixture.componentInstance;
    roasService = TestBed.get(RoasService);
    roasSpy = spyOn(roasService, 'getRoas').and.callThrough();
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load data', () => {
    component.loadData();
    fixture.detectChanges();
    expect(component.roas).not.toBeNull();
    expect(component.page).toEqual(1);
    expect(component.firstItemInTable).toEqual(0);
    expect(component.lastItemInTable).toEqual(10);
  });

  //FIXME
  it('should change page size', () => {
    component.onChangedPageSize(50);
    fixture.detectChanges();
    expect(component.roas).not.toBeNull();
    expect(component.page).toEqual(1);
    expect(component.firstItemInTable).toEqual(0);
  });

  it('should call roasService for changed page', () => {
    component.onChangePage(5);
    expect(roasSpy).toHaveBeenCalled();
    fixture.detectChanges();
    expect(component.roas).not.toBeNull();
    expect(component.page).toEqual(5);
    expect(component.firstItemInTable).toEqual(40);
    expect(component.lastItemInTable).toEqual(50);
  });
});
