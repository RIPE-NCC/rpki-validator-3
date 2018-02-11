import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from "@ngx-translate/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {HttpClientModule} from "@angular/common/http";
import {RouterTestingModule} from "@angular/router/testing";

import {TrustAnchorsComponent} from './trust-anchors.component';
import {SharedModule} from "../shared/shared.module";
import {CoreModule} from "../core/core.module";
import {TrustAnchorsService} from "../core/trust-anchors.service";

describe('TrustAnchorsComponent', () => {
  let component: TrustAnchorsComponent;
  let fixture: ComponentFixture<TrustAnchorsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        NgbModule.forRoot(),
        TranslateModule.forRoot(),
        HttpClientModule,
        RouterTestingModule,
        CoreModule
      ],
      providers: [TrustAnchorsService],
      declarations: [TrustAnchorsComponent]
    })
      .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(TrustAnchorsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
