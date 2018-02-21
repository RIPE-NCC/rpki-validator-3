import {Component, Input} from '@angular/core'

@Component({
  selector: 'loading-spinner',
  template:
      `<span [hidden]='!loading' class='fa fa-circle-o-notch fa-spin'></span>`,
  styles: [
    'span {font-size:30px;color:#ddd}'
  ]
})
export class LoadingSpinnerComponent {
  @Input() loading: boolean
}
