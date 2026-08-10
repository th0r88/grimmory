import {Component, computed, inject, OnInit} from '@angular/core';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {RadioButton} from 'primeng/radiobutton';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Tooltip} from 'primeng/tooltip';
import {Select} from 'primeng/select';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {EmailV2RecipientService} from './email-v2-recipient.service';
import {EmailRecipient} from '../email-recipient.model';
import {DialogLauncherService} from '../../../../shared/services/dialog-launcher.service';
import {UserService} from '../../user-management/user.service';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

/** Sentinel owner id for a recipient the backend did not attribute to a user (should not occur
 *  in practice, but keeps the per-owner radio keying from collapsing `undefined` into one group). */
const UNASSIGNED_OWNER_ID = -1;

type OwnedEmailRecipient = EmailRecipient & { userId: number };

interface OwnerOption {
  userId: number;
  ownerUsername: string;
}

@Component({
  selector: 'app-email-v2-recipient',
  imports: [
    Button,
    RadioButton,
    ReactiveFormsModule,
    TableModule,
    Tooltip,
    FormsModule,
    Select,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './email-v2-recipient.component.html',
  styleUrl: './email-v2-recipient.component.scss'
})
export class EmailV2RecipientComponent implements OnInit {
  recipientEmails: OwnedEmailRecipient[] = [];
  editingRecipientIds: number[] = [];
  ref: DynamicDialogRef | undefined | null;
  owners: OwnerOption[] = [];
  selectedOwnerId: number | null = null;
  defaultRecipientByOwner: Record<number, number | null> = {};

  private dialogLauncherService = inject(DialogLauncherService);
  private emailRecipientService = inject(EmailV2RecipientService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  readonly isAdmin = computed(() => this.userService.currentUser()?.permissions.admin ?? false);

  ngOnInit(): void {
    this.loadRecipientEmails();
  }

  loadRecipientEmails(): void {
    const isAdmin = this.isAdmin();
    const request = !isAdmin
      ? this.emailRecipientService.getRecipients()
      : this.selectedOwnerId !== null
        ? this.emailRecipientService.getRecipients({userId: this.selectedOwnerId})
        : this.emailRecipientService.getRecipients({scopeAll: true});
    const populateOwners = isAdmin && this.selectedOwnerId === null;

    request.subscribe({
      next: (recipients: EmailRecipient[]) => {
        this.recipientEmails = recipients.map((recipient) => ({
          ...recipient,
          userId: recipient.userId ?? UNASSIGNED_OWNER_ID,
          isEditing: false,
        }));
        this.rebuildDefaultsByOwner();
        if (populateOwners) {
          this.rebuildOwnerOptions();
        }
      },
      error: (err: {status?: number}) => {
        if (isAdmin && this.selectedOwnerId !== null && err?.status === 400) {
          this.selectedOwnerId = null;
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('settingsEmail.recipient.ownerFilterError'),
          });
          this.loadRecipientEmails();
          return;
        }
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsEmail.recipient.loadError'),
        });
      },
    });
  }

  onOwnerFilterChange(): void {
    this.loadRecipientEmails();
  }

  private rebuildOwnerOptions(): void {
    const seen = new Map<number, string>();
    for (const recipient of this.recipientEmails) {
      if (!seen.has(recipient.userId)) {
        seen.set(recipient.userId, recipient.ownerUsername ?? '');
      }
    }
    this.owners = Array.from(seen.entries())
      .map(([userId, ownerUsername]) => ({userId, ownerUsername}))
      .sort((a, b) => a.userId - b.userId);
  }

  private rebuildDefaultsByOwner(): void {
    const defaults: Record<number, number | null> = {};
    for (const recipient of this.recipientEmails) {
      if (recipient.defaultRecipient) {
        defaults[recipient.userId] = recipient.id;
      } else if (!(recipient.userId in defaults)) {
        defaults[recipient.userId] = null;
      }
    }
    this.defaultRecipientByOwner = defaults;
  }

  toggleEditRecipient(recipient: EmailRecipient): void {
    recipient.isEditing = !recipient.isEditing;
    if (recipient.isEditing) {
      this.editingRecipientIds.push(recipient.id);
    } else {
      this.editingRecipientIds = this.editingRecipientIds.filter((id) => id !== recipient.id);
    }
  }

  saveRecipient(recipient: EmailRecipient): void {
    this.emailRecipientService.updateRecipient(recipient).subscribe({
      next: () => {
        recipient.isEditing = false;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('settingsEmail.recipient.updateSuccess'),
        });
        this.loadRecipientEmails();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsEmail.recipient.updateError'),
        });
      },
    });
  }

  deleteRecipient(recipient: EmailRecipient): void {
    if (confirm(this.t.translate('settingsEmail.recipient.deleteConfirm', {email: recipient.email}))) {
      this.emailRecipientService.deleteRecipient(recipient.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('common.success'),
            detail: this.t.translate('settingsEmail.recipient.deleteSuccess', {email: recipient.email}),
          });
          this.loadRecipientEmails();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('settingsEmail.recipient.deleteError'),
          });
        },
      });
    }
  }

  openAddRecipientDialog() {
    this.ref = this.dialogLauncherService.openEmailRecipientDialog();
    this.ref?.onClose.subscribe((result) => {
      if (result) {
        this.loadRecipientEmails();
      }
    });
  }

  setDefaultRecipient(recipient: OwnedEmailRecipient) {
    this.emailRecipientService.setDefaultRecipient(recipient.id).subscribe(() => {
      this.defaultRecipientByOwner[recipient.userId] = recipient.id;
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsEmail.recipient.defaultSetSummary'),
        detail: this.t.translate('settingsEmail.recipient.defaultSetDetail', {email: recipient.email}),
      });
    });
  }
}
