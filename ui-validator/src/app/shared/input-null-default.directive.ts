import {Directive, ElementRef, HostListener} from "@angular/core";
import {NgControl} from "@angular/forms";

@Directive({
  selector: 'input[sanitize]'
})
export class InputSanitizeDirective {
  constructor(private el: ElementRef, private control: NgControl) {}

  // trim value and in case of empty string putting null
  @HostListener('input', ['$event'])
  onKeyDown(event: KeyboardEvent){
    const value = (event.target as HTMLInputElement).value.trim();
    this.control.control.patchValue((value === '') ? null : value);
  }
}
