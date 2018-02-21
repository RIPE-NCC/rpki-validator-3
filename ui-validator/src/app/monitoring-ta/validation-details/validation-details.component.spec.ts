import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ValidationDetailsComponent } from './validation-details.component';
import {SharedModule} from "../../shared/shared.module";
import {TranslateModule} from "@ngx-translate/core";

describe('ValidationDetailsComponent', () => {
  let component: ValidationDetailsComponent;
  let fixture: ComponentFixture<ValidationDetailsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      declarations: [ValidationDetailsComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(ValidationDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
