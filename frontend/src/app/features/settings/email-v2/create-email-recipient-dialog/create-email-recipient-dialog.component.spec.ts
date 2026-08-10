import {TestBed} from '@angular/core/testing';
import {ReactiveFormsModule} from '@angular/forms';
import {TranslocoService} from '@jsverse/transloco';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {
  createDynamicDialogHarness,
  createMessageServiceProvider,
  createMessageServiceSpy,
} from '../../../../core/testing/dialog-testing';
import {EmailV2RecipientService} from '../email-v2-recipient/email-v2-recipient.service';
import {User, UserService} from '../../user-management/user.service';
import {CreateEmailRecipientDialogComponent} from './create-email-recipient-dialog.component';

describe('CreateEmailRecipientDialogComponent', () => {
  let createRecipient: ReturnType<typeof vi.fn>;
  let getUsers: ReturnType<typeof vi.fn>;
  let currentUser: ReturnType<typeof vi.fn>;
  let dialogHarness: ReturnType<typeof createDynamicDialogHarness<null>>;
  let messageService: ReturnType<typeof createMessageServiceSpy>;
  let translate: TranslocoService['translate'];

  const adminUser = {id: 1, username: 'admin', permissions: {admin: true}} as User;
  const memberUser = {id: 2, username: 'reader', permissions: {admin: false}} as User;

  beforeEach(() => {
    createRecipient = vi.fn(() => of({
      id: 9,
      name: 'Reader Kindle',
      email: 'reader@example.com',
      defaultRecipient: false,
    }));
    getUsers = vi.fn(() => of([adminUser, memberUser]));
    currentUser = vi.fn(() => memberUser);
    dialogHarness = createDynamicDialogHarness<null>(null);
    messageService = createMessageServiceSpy();
    translate = (<T = string>(key: string, params?: Record<string, unknown>) => {
      if (!params) {
        return `translated:${key}` as T;
      }

      return `translated:${key}:${JSON.stringify(params)}` as T;
    }) as TranslocoService['translate'];
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
      providers: [
        ...dialogHarness.providers,
        {
          provide: EmailV2RecipientService,
          useValue: {
            createRecipient,
          },
        },
        {
          provide: UserService,
          useValue: {
            currentUser,
            getUsers,
          },
        },
        createMessageServiceProvider(messageService),
        {
          provide: TranslocoService,
          useValue: {
            translate,
          },
        },
      ],
    });

    return TestBed.runInInjectionContext(() => new CreateEmailRecipientDialogComponent());
  }

  it('hides the assign-to picker and never calls getUsers for a non-admin', () => {
    currentUser.mockReturnValue(memberUser);
    const component = createComponent();

    expect(component.isAdmin).toBe(false);
    expect(component.emailRecipientForm.contains('userId')).toBe(false);
    expect(getUsers).not.toHaveBeenCalled();
  });

  it('shows the assign-to picker and loads users for an admin, defaulting to self', () => {
    currentUser.mockReturnValue(adminUser);
    const component = createComponent();

    expect(component.isAdmin).toBe(true);
    expect(component.emailRecipientForm.contains('userId')).toBe(true);
    expect(component.emailRecipientForm.get('userId')?.value).toBe(adminUser.id);
    expect(getUsers).toHaveBeenCalledOnce();
    expect(component.users).toEqual([adminUser, memberUser]);
  });

  it('never includes userId in the create payload for a non-admin submission', () => {
    currentUser.mockReturnValue(memberUser);
    const component = createComponent();
    component.emailRecipientForm.setValue({
      name: 'Reader Kindle',
      email: 'reader@example.com',
      defaultRecipient: false,
    });

    component.createEmailRecipient();

    expect(createRecipient).toHaveBeenCalledWith({
      name: 'Reader Kindle',
      email: 'reader@example.com',
      defaultRecipient: false,
    });
  });

  it('includes the selected userId in the create payload for an admin submission', () => {
    currentUser.mockReturnValue(adminUser);
    const component = createComponent();
    component.emailRecipientForm.patchValue({
      name: 'Reader Kindle',
      email: 'reader@example.com',
      defaultRecipient: false,
      userId: memberUser.id,
    });

    component.createEmailRecipient();

    expect(createRecipient).toHaveBeenCalledWith({
      name: 'Reader Kindle',
      email: 'reader@example.com',
      defaultRecipient: false,
      userId: memberUser.id,
    });
  });

  it('shows an error toast when loading users for the assign-to picker fails', () => {
    getUsers.mockReturnValue(throwError(() => new Error('network down')));
    currentUser.mockReturnValue(adminUser);

    const component = createComponent();

    expect(component.users).toEqual([]);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'translated:common.error',
      detail: 'translated:settingsEmail.recipient.create.assignToLoadError',
    });
  });
});
