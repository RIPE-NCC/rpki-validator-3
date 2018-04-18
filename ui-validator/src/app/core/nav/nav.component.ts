import {Component, OnInit} from '@angular/core';
import 'rxjs/add/operator/filter';

@Component({
  selector: 'app-nav',
  templateUrl: './nav.component.html',
  styleUrls: ['./nav.component.scss']
})
export class NavComponent implements OnInit {

  navbarCollapsed = true;

  constructor() {
  }

  ngOnInit(): void {}
}

