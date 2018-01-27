import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';

import { HttpClientModule, HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { FooterComponent } from './footer/footer.component';
import {HomeModule} from './home/home.module';
import { NavComponent } from './nav/nav.component';
import {TrustAnchorsModule} from './trust-anchors/trust-anchors.module';
import {RoasModule} from "./roas/roas.module";
import {TrustAnchorsComponent} from "./trust-anchors/trust-anchors.component";
import {RoasListComponent} from "./roas/roas-list.component";

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http);
}

@NgModule({
  declarations: [
    AppComponent,
    NavComponent,
    FooterComponent
  ],
  imports: [
    BrowserModule,
    HomeModule,
    TrustAnchorsModule,
    RoasModule,
    HttpClientModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    RouterModule.forRoot([
      { path: 'home', component: HomeComponent},
      { path: 'trust-anchors', component: TrustAnchorsComponent},
      { path: 'roas', component: RoasListComponent},
      { path: '**', redirectTo: 'home', pathMatch: 'full'}
    ])
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
