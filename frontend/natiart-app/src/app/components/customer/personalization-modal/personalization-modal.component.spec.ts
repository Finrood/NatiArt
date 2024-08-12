import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PersonalizationModalComponent } from './personalization-modal.component';

describe('PersonalizationModalComponent', () => {
  let component: PersonalizationModalComponent;
  let fixture: ComponentFixture<PersonalizationModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PersonalizationModalComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PersonalizationModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
