import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ValidationDetailsComponent } from './validation-details.component';

describe('ValidationDetailsComponent', () => {
  let component: ValidationDetailsComponent;
  let fixture: ComponentFixture<ValidationDetailsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ValidationDetailsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ValidationDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
