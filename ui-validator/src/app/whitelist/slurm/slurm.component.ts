import {Component, OnInit, ElementRef, ViewChild} from '@angular/core';
import {FormBuilder, FormGroup, Validators, ReactiveFormsModule } from "@angular/forms";
import {HttpClient, HttpParams} from '@angular/common/http';

@Component({
  selector: 'slurm',
  template: `
    <div class="pt-3 pb-4">
      <h3><strong>{{'SLURM' | translate}}</strong></h3>
      <p>{{'Slurm.DESCRIPTION' | translate}}</p>
      <form [formGroup]="form" (ngSubmit)="onSubmit()">
        <div class="form-group">
          <button type="button"
                  class="btn btn-primary"
                  href="/api/slurm"
                  download="SLURM.json"
                  (click)="downloadSlurm()">Download SLURM</button>
          <button type="submit"
            class="btn btn-primary">Upload SLURM<i class="fa fa-spinner fa-spin fa-fw" *ngIf="loading"></i>
          </button>
          <input type="file" id="slurmFile" (change)="onFileChange($event)" #fileInput>
        </div>
      </form>
    </div>
    `,
  styles: [`
    a {
      padding: 10px;
      border-radius: 5px;
    }
  `]
})
export class SlurmComponent implements OnInit {

  private _slurmUploadUrl = 'api/slurm/upload';

  form: FormGroup;
  formData: FormData;
  loading = false;

  constructor(private fb: FormBuilder, private _http: HttpClient) {
    this.createForm();
  }

  createForm() {
    this.form = this.fb.group({ file: null });
  }

  private prepareSave(): any {
    const input = new FormData();
    input.append('file', this.form.get('file').value);
    return input;
  }

  ngOnInit() {
  }

  onFileChange(event) {
    if (event.target.files.length > 0) {
      const file = event.target.files[0];
      this.form.get('file').setValue(file);
    }
  }

  downloadSlurm() {
    window.open('/api/slurm/download');
  }

  onSubmit() {
     const formModel = this.prepareSave();
     this.loading = true;
     setTimeout(() => {
       this._http.post(this._slurmUploadUrl, formModel).subscribe(
        res => {
          console.log(res);
        },
        err => {
          console.log('Error occured');
        }
      );
       this.loading = false;
     }, 1000);
   }
}
