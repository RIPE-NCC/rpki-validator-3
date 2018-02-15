import {TestBed, async} from '@angular/core/testing';
import {RouterModule} from "@angular/router";
import {APP_BASE_HREF} from "@angular/common";
import {TranslateModule, TranslateService} from "@ngx-translate/core";

import {AppComponent} from './app.component';
import {CoreModule} from "./core/core.module";
import {RoasModule} from "./roas/roas.module";
import {appRoutes} from "./routes";
import {TrustAnchorsModule} from "./trust-anchors/trust-anchors.module";

describe('AppComponent', () => {
  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        CoreModule,
        TrustAnchorsModule,
        RoasModule,
        RouterModule.forRoot(appRoutes),
        TranslateModule.forRoot()
      ],
      providers: [{provide: APP_BASE_HREF, useValue: '/'}, TranslateService],
      declarations: [
        AppComponent
      ]
    }).compileComponents();
  }));
  it('should create the app', async(() => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.debugElement.componentInstance;
    expect(app).toBeTruthy();
  }));
});
