import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { MonitoringTaComponent } from './monitoring-ta.component';
import {RouterTestingModule} from "@angular/router/testing";
import {SharedModule} from "../shared/shared.module";
import {TranslateModule} from "@ngx-translate/core";
import {HttpClientModule} from "@angular/common/http";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

describe('MonitoringTaComponent', () => {
  let component: MonitoringTaComponent;
  let fixture: ComponentFixture<MonitoringTaComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        NgbModule.forRoot(),
        TranslateModule.forRoot(),
        HttpClientModule,
        RouterTestingModule
      ],
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
