import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IgnoreFiltersComponent } from './ignore-filters.component';

describe('IgnoreFiltersComponent', () => {
  let component: IgnoreFiltersComponent;
  let fixture: ComponentFixture<IgnoreFiltersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IgnoreFiltersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IgnoreFiltersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
