import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RoasListComponent } from './roas-list.component';

describe('RoasListComponent', () => {
  let component: RoasListComponent;
  let fixture: ComponentFixture<RoasListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RoasListComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RoasListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
