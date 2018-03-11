import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AnnouncementPreviewComponent } from './announcement-preview.component';

describe('AnnouncementPreviewComponent', () => {
  let component: AnnouncementPreviewComponent;
  let fixture: ComponentFixture<AnnouncementPreviewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AnnouncementPreviewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AnnouncementPreviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
