import {Component, OnInit} from '@angular/core'
import {ActivatedRoute} from "@angular/router";
import {TranslateService} from "@ngx-translate/core";

@Component({
  template: `
    <h1 class="errorMessage">{{errorMsg | translate}} {{error}}</h1>
  `,
  styles: [`
    .errorMessage { 
      margin:200px 0; 
      font-size: 36px;
      text-align: center; 
    }`]
})
export class ErrorComponent implements OnInit {

  errorMsg: string = 'Page not found';
  error: string = '404';

  constructor(private _activatedRoute: ActivatedRoute,
              private _translateService: TranslateService) { }

  ngOnInit(): void {
    const errorStatus = this._activatedRoute.snapshot.params['error'];
    if (errorStatus && errorStatus !== '200') {
      switch (errorStatus) {
        case '504': {
          this.error = '';
          this.errorMsg = 'Error.BACKEND_DOWN';
          break;
        }
        case '0': {
          this.error = '';
          this.errorMsg = 'Error.FRONTEND_DOWN';
          break;
        }
        case 'undefined': {
          this.error = '';
          this.errorMsg = 'Error.UNKONOWN';
          break;
        }
        default: {
          this.error = errorStatus;
          this.errorMsg = this._activatedRoute.snapshot.params['errorMsg'];
        }
      }
    }
  }
}
