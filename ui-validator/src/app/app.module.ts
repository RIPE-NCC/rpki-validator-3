import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';
import {HttpClient, HttpClientModule} from '@angular/common/http';
import {TranslateLoader, TranslateModule} from '@ngx-translate/core';
import {TranslateHttpLoader} from '@ngx-translate/http-loader';
import {BrowserAnimationsModule} from "@angular/platform-browser/animations";
import {ToastrModule} from "ngx-toastr";

import {AppComponent} from './app.component';
import {appRoutes} from './routes'
import {CoreModule} from './core/core.module';
import {SharedModule} from './shared/shared.module';
import {TrustAnchorsModule} from './trust-anchors/trust-anchors.module';
import {RoasModule} from './roas/roas.module';
import {IgnoreFiltersModule} from './ignore-filters/ignore-filters.module';
import {BgpPreviewModule} from './bgp-preview/bgp-preview.module';
import {AnnouncementPreviewModule} from './announcement-preview/announcement-preview.module';
import {WhitelistModule} from './whitelist/whitelist.module';
import {MonitoringTaModule} from './monitoring-ta/monitoring-ta.module';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http);
}

@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    CoreModule,
    SharedModule,
    TrustAnchorsModule,
    MonitoringTaModule,
    RoasModule,
    IgnoreFiltersModule,
    WhitelistModule,
    BgpPreviewModule,
    AnnouncementPreviewModule,
    RouterModule.forRoot(appRoutes),
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    BrowserAnimationsModule,
    ToastrModule.forRoot()
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {
}
