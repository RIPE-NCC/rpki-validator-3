import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import {TranslateModule} from "@ngx-translate/core";

import { ValidationDetailsComponent } from './validation-details.component';
import {SharedModule} from "../../shared/shared.module";
import {TrustAnchorsService} from "../../core/trust-anchors.service";
import {TrustAnchorsServiceStub} from "../../core/trust-anchors.service.stub";

describe('ValidationDetailsComponent', () => {
  let component: ValidationDetailsComponent;
  let fixture: ComponentFixture<ValidationDetailsComponent>;
  let trustAnchorsService: TrustAnchorsService;
  let trustAnchorsSpy;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      providers: [
        {provide: TrustAnchorsService, useClass: TrustAnchorsServiceStub}
      ],
      declarations: [ValidationDetailsComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(ValidationDetailsComponent);
    component = fixture.componentInstance;
    trustAnchorsService = TestBed.get(TrustAnchorsService);
    trustAnchorsSpy = spyOn(trustAnchorsService, 'getTrustAnchorValidationChecks').and.callThrough();
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
