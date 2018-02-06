import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import 'rxjs/add/operator/filter';

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

  ngOnInit(): void {}
}

