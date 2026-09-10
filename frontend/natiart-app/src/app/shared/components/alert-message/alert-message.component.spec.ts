import {ComponentFixture, fakeAsync, TestBed, tick} from '@angular/core/testing';

import { AlertMessageComponent } from './alert-message.component';

describe('AlertMessageComponent', () => {
  let component: AlertMessageComponent;
  let fixture: ComponentFixture<AlertMessageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AlertMessageComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AlertMessageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('clears pending auto-dismiss timers when destroyed', fakeAsync(() => {
    const alert = {type: 'error' as const, message: 'Something went wrong'};
    component.showAlert(alert, 3000);

    component.ngOnDestroy();
    tick(3000);

    expect(component.alertMessages).toEqual([alert]);
  }));
});
