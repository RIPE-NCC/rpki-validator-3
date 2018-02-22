import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from "@ngx-translate/core";

import {RepositoriesComponent} from './repositories.component';
import {SharedModule} from "../../shared/shared.module";
import {TrustAnchorsService} from "../../core/trust-anchors.service";
import {TrustAnchorsServiceStub} from "../../core/trust-anchors.service.stub";

describe('RepositoriesComponent', () => {
  let component: RepositoriesComponent;
  let fixture: ComponentFixture<RepositoriesComponent>;
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
      declarations: [RepositoriesComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(RepositoriesComponent);
    component = fixture.componentInstance;
    trustAnchorsService = TestBed.get(TrustAnchorsService);
    trustAnchorsSpy = spyOn(trustAnchorsService, 'getRepositories').and.callThrough();
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
