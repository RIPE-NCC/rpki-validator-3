import {async, ComponentFixture, TestBed} from '@angular/core/testing';
import {TranslateModule} from "@ngx-translate/core";
import {Observable} from "rxjs/Observable";

import {BgpPreviewComponent} from './bgp-preview.component';
import {BgpService} from "./bgp.service";
import {SharedModule} from "../shared/shared.module";

class BgpServiceStub {
  getBgp(firstRoa: string, page: number) {
    return Observable.of(
      {
        "links": {},
        "data": [
          {
            "asn": "string",
            "prefix": "string",
            "validity": "string"
          },
          {
            "asn": "string",
            "prefix": "string",
            "validity": "string"
          },
          {
            "asn": "string",
            "prefix": "string",
            "validity": "string"
          },
          {
            "asn": "string",
            "prefix": "string",
            "validity": "string"
          }
        ],
        "metadata": {
          "totalCount": 0
        }
      }
    )
  }
}

describe('BgpPreviewComponent', () => {
  let component: BgpPreviewComponent;
  let fixture: ComponentFixture<BgpPreviewComponent>;
  let bgpService: BgpService;
  let bgpServiceSpy;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      imports: [
        SharedModule,
        TranslateModule.forRoot()
      ],
      providers: [
        {provide: BgpService, useClass: BgpServiceStub}
      ],
      declarations: [BgpPreviewComponent]
    })
      .compileComponents();
    fixture = TestBed.createComponent(BgpPreviewComponent);
    component = fixture.componentInstance;
    bgpService = TestBed.get(BgpService);
    bgpServiceSpy = spyOn(bgpService, 'getBgp').and.callThrough();
    fixture.detectChanges();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(BgpPreviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
