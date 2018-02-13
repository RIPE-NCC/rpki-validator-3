import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {HttpClientModule} from '@angular/common/http';
import {RouterTestingModule} from '@angular/router/testing';
import {Observable} from 'rxjs/Observable';

import {TrustAnchorsComponent} from './trust-anchors.component';
import {SharedModule} from '../shared/shared.module';
import {TrustAnchorsService} from '../core/trust-anchors.service';
import {Router} from "@angular/router";
import createSpy = jasmine.createSpy;

class TrustAnchorsServiceStub {
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
}

describe('TrustAnchorsComponent', () => {
  let component: TrustAnchorsComponent;
  let fixture: ComponentFixture<TrustAnchorsComponent>;
  let trustAnchorsService: TrustAnchorsService;
  let trustAnchorsSpy;
  let router = {
    navigate: createSpy('navigate')
  }

  beforeEach(async(() => {

    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot(),
        HttpClientModule,
        RouterTestingModule
      ],
      providers: [
        {provide: TrustAnchorsService, useClass: TrustAnchorsServiceStub},
        {provide: Router, useValue: router}
      ],
      declarations: [TrustAnchorsComponent]
    })
    .compileComponents();
    fixture = TestBed.createComponent(TrustAnchorsComponent);
    component = fixture.componentInstance;
    trustAnchorsService = TestBed.get(TrustAnchorsService);
    trustAnchorsSpy = spyOn(trustAnchorsService, 'getTrustAnchorsOverview').and.callThrough();
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call getTrustAnchorsOverview from TrustAnchorsService on init', () => {
    expect(trustAnchorsSpy).toHaveBeenCalled();
    fixture.detectChanges();
    expect(component.trustAnchorsOverview).not.toBeNull();
    expect(component.trustAnchorsOverview.length).toEqual(2);
  });

  it('should call getTrustAnchor on click on specific ta', () => {
    component.openTADetails('21586');
    fixture.detectChanges();
    expect(router.navigate).toHaveBeenCalledWith(['/trust-anchors/monitor', '21586']);
  });
});
