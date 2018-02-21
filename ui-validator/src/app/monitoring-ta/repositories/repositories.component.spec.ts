import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RepositoriesComponent } from './repositories.component';
import {SharedModule} from "../../shared/shared.module";
import {TranslateModule} from "@ngx-translate/core";

describe('RepositoriesComponent', () => {
  let component: RepositoriesComponent;
  let fixture: ComponentFixture<RepositoriesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      declarations: [RepositoriesComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(RepositoriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
