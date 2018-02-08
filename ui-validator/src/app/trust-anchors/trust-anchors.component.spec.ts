import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from "@ngx-translate/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {HttpClientModule} from "@angular/common/http";

import {TrustAnchorsComponent} from './trust-anchors.component';
import {SharedModule} from "../shared/shared.module";
import {TrustAnchorsService} from "./trust-anchors.service";
import {RouterTestingModule} from "@angular/router/testing";

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
        RouterTestingModule
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
