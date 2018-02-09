import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from "@ngx-translate/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

import {RoasListComponent} from './roas-list.component';
import {SharedModule} from "../shared/shared.module";
import {RoasService} from "./roas.service";
import {HttpClientModule} from "@angular/common/http";
import {CoreModule} from "../core/core.module";

describe('RoasListComponent', () => {
  let component: RoasListComponent;
  let fixture: ComponentFixture<RoasListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        NgbModule.forRoot(),
        TranslateModule.forRoot(),
        HttpClientModule,
        CoreModule
      ],
      providers: [RoasService],
      declarations: [RoasListComponent]
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
