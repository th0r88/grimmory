import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {EmailV2RecipientService} from './email-v2-recipient/email-v2-recipient.service';
import {User, UserService} from '../user-management/user.service';
import {EmailV2Component} from './email-v2.component';

describe('EmailV2Component', () => {
  let getRecipients: ReturnType<typeof vi.fn>;
  let currentUser: ReturnType<typeof vi.fn>;

  const adminUser = {
    id: 1,
    username: 'admin',
    permissions: {admin: true, canEmailBook: true},
    userSettings: {},
  } as unknown as User;

  beforeEach(() => {
    getRecipients = vi.fn(() => of([]));
    currentUser = vi.fn(() => adminUser);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createComponent() {
    TestBed.configureTestingModule({
      providers: [
        {provide: EmailV2RecipientService, useValue: {getRecipients}},
        {provide: UserService, useValue: {currentUser}},
      ],
    });

    return TestBed.runInInjectionContext(() => new EmailV2Component());
  }

  it('loads recipients with no arguments, regardless of admin status', () => {
    const component = createComponent();

    component.ngOnInit();

    expect(getRecipients).toHaveBeenCalledWith();
  });

  it('keeps the auto-email toggle disabled when the caller owns no recipients', () => {
    getRecipients.mockReturnValue(of([]));
    const component = createComponent();

    component.ngOnInit();

    expect(component.hasRecipient()).toBe(false);
  });
});
