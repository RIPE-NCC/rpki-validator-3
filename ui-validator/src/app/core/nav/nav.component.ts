import {Component, OnInit} from '@angular/core';
import {NavigationEnd, Router} from '@angular/router';
import 'rxjs/add/operator/filter';

import {NavigationUrlEnum} from "./nav.urls.enum";

@Component({
  selector: 'app-nav',
  templateUrl: './nav.component.html',
  styleUrls: ['./nav.component.scss']
})
export class NavComponent implements OnInit {

  pageTitle = 'Nav.TITLE_HOME';
  navbarCollapsed = true;

  constructor(private router: Router) {
  }

  ngOnInit(): void {
    this.router.events.filter((event) => event instanceof NavigationEnd).subscribe(val =>
      this.setTitle(val['urlAfterRedirects'])
    )
  }

  setTitle(url: string) {
    switch (url) {
      case NavigationUrlEnum.HOME:
        this.pageTitle = 'Nav.TITLE_HOME';
        break;
      case NavigationUrlEnum.TRUST_ANCHORS:
        this.pageTitle = 'Nav.TITLE_TRUST_ANCHORS';
        break;
      case NavigationUrlEnum.ROAS:
        this.pageTitle = 'Nav.TITLE_ROAS';
        break;
      case NavigationUrlEnum.IGNORE_FILTERS:
        this.pageTitle = 'Nav.TITLE_IGNORE_FILTERS';
        break;
      case NavigationUrlEnum.WHITELIST:
        this.pageTitle = 'Nav.TITLE_WHITELIST';
        break;
      case NavigationUrlEnum.BGP_PREVIEW:
        this.pageTitle = 'Nav.TITLE_BGP_PREVIEW';
        break;
      case NavigationUrlEnum.EXPORT_API:
        this.pageTitle = 'Nav.TITLE_EXPORT_API';
        break;
      case NavigationUrlEnum.ROUTER_SESSIONS:
        this.pageTitle = 'Nav.TITLE_ROUTER_SESSIONS';
        break;
    }
  }
}

