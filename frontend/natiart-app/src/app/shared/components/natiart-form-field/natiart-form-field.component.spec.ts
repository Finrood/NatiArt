import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NatiartFormFieldComponent } from './natiart-form-field.component';

describe('NatiartFormFieldComponent', () => {
  let component: NatiartFormFieldComponent;
  let fixture: ComponentFixture<NatiartFormFieldComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NatiartFormFieldComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(NatiartFormFieldComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
