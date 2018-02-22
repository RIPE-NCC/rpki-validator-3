import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from '@ngx-translate/core';
import {ActivatedRoute} from '@angular/router';

import {MonitoringTaComponent} from './monitoring-ta.component';
import {SharedModule} from '../shared/shared.module';
import {TrustAnchorsService} from '../core/trust-anchors.service';
import {ValidationDetailsComponent} from "./validation-details/validation-details.component";
import {RepositoriesComponent} from "./repositories/repositories.component";
import {TrustAnchorsServiceStub} from "../core/trust-anchors.service.stub";

describe('MonitoringTaComponent', () => {
  let component: MonitoringTaComponent;
  let fixture: ComponentFixture<MonitoringTaComponent>;
  let trustAnchorsService: TrustAnchorsService;
  let trustAnchorsSpy;
  let trustAnchorsSpy2;
  let mockActivatedRoute = {
    snapshot: {
      url: [{path: 'trust-anchors'}, {path: 'monitor'}, {path: 8}]
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
      declarations: [MonitoringTaComponent, ValidationDetailsComponent, RepositoriesComponent]
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
    expect(component.taId.toString()).toEqual('8');
    expect(trustAnchorsSpy).toHaveBeenCalled();
    expect(trustAnchorsSpy).toHaveBeenCalledWith(8);
    fixture.detectChanges();
    expect(component.monitoringTrustAnchor).not.toBeNull();
  });
});
