import {TestBed} from '@angular/core/testing';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {EmailV2RecipientService} from './email-v2-recipient.service';
import {EmailRecipient} from '../email-recipient.model';
import {User, UserService} from '../../user-management/user.service';
import {EmailV2RecipientComponent} from './email-v2-recipient.component';

describe('EmailV2RecipientComponent', () => {
  let getRecipients: ReturnType<typeof vi.fn>;
  let currentUser: ReturnType<typeof vi.fn>;
  let messageService: {add: ReturnType<typeof vi.fn>};
  let translate: TranslocoService['translate'];

  const adminUser = {id: 1, username: 'admin', permissions: {admin: true}} as User;
  const memberUser = {id: 2, username: 'reader', permissions: {admin: false}} as User;

  beforeEach(() => {
    messageService = {add: vi.fn()};
    translate = ((key: string) => key) as TranslocoService['translate'];
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
        {provide: DialogLauncherService, useValue: {}},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    return TestBed.runInInjectionContext(() => new EmailV2RecipientComponent());
  }

  it('loads its own recipients with no arguments for a non-admin', () => {
    currentUser = vi.fn(() => memberUser);
    getRecipients = vi.fn(() => of([recipient({id: 1, userId: 2})]));
    const component = createComponent();

    component.ngOnInit();

    expect(getRecipients).toHaveBeenCalledWith();
    expect(component.isAdmin()).toBe(false);
  });

  it('loads every owner for an admin and exposes the distinct owner list', () => {
    currentUser = vi.fn(() => adminUser);
    getRecipients = vi.fn(() => of([
      recipient({id: 1, userId: 1, ownerUsername: 'admin'}),
      recipient({id: 2, userId: 2, ownerUsername: 'reader'}),
    ]));
    const component = createComponent();

    component.ngOnInit();

    expect(getRecipients).toHaveBeenCalledWith({scopeAll: true});
    expect(component.isAdmin()).toBe(true);
    expect(component.owners).toEqual([
      {userId: 1, ownerUsername: 'admin'},
      {userId: 2, ownerUsername: 'reader'},
    ]);
  });

  it('switches to a filtered owner request when the owner filter changes', () => {
    currentUser = vi.fn(() => adminUser);
    getRecipients = vi.fn(() => of([recipient({id: 1, userId: 1, ownerUsername: 'admin'})]));
    const component = createComponent();
    component.ngOnInit();
    getRecipients.mockClear();
    getRecipients.mockReturnValue(of([recipient({id: 2, userId: 2, ownerUsername: 'reader'})]));

    component.selectedOwnerId = 2;
    component.onOwnerFilterChange();

    expect(getRecipients).toHaveBeenCalledWith({userId: 2});
  });

  it('renders both owners as default when each owns a default recipient', () => {
    currentUser = vi.fn(() => adminUser);
    getRecipients = vi.fn(() => of([
      recipient({id: 1, userId: 1, ownerUsername: 'admin', defaultRecipient: true}),
      recipient({id: 2, userId: 2, ownerUsername: 'reader', defaultRecipient: true}),
    ]));
    const component = createComponent();

    component.ngOnInit();

    expect(component.defaultRecipientByOwner).toEqual({1: 1, 2: 2});
  });

  it('resets a stale owner filter to "all" and toasts on a 400 from the filtered read', () => {
    currentUser = vi.fn(() => adminUser);
    getRecipients = vi.fn(() => of([recipient({id: 1, userId: 1, ownerUsername: 'admin'})]));
    const component = createComponent();
    component.ngOnInit();

    getRecipients.mockReturnValueOnce(throwError(() => ({status: 400})));
    getRecipients.mockReturnValueOnce(of([recipient({id: 1, userId: 1, ownerUsername: 'admin'})]));

    component.selectedOwnerId = 99;
    component.onOwnerFilterChange();

    expect(component.selectedOwnerId).toBeNull();
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
  });
});

function recipient(overrides: Partial<EmailRecipient> = {}): EmailRecipient {
  return {
    id: 1,
    email: 'reader@example.com',
    name: 'Reader',
    defaultRecipient: false,
    isEditing: false,
    ...overrides,
  };
}
