import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MonitoringTaComponent } from './monitoring-ta.component';

describe('MonitoringTaComponent', () => {
  let component: MonitoringTaComponent;
  let fixture: ComponentFixture<MonitoringTaComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ MonitoringTaComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(MonitoringTaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
