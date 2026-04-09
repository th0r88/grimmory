import {Component, computed, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {EmailV2ProviderComponent} from './email-v2-provider/email-v2-provider.component';
import {EmailV2RecipientComponent} from './email-v2-recipient/email-v2-recipient.component';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {UserService} from '../user-management/user.service';
import {TranslocoDirective} from '@jsverse/transloco';


@Component({
  selector: 'app-email-v2',
  imports: [
    FormsModule,
    TableModule,
    ToggleSwitch,
    EmailV2ProviderComponent,
    EmailV2RecipientComponent,
    ExternalDocLinkComponent,
    TranslocoDirective
  ],
  templateUrl: './email-v2.component.html',
  styleUrls: ['./email-v2.component.scss'],
})
export class EmailV2Component {
  private userService = inject(UserService);

  readonly hasPermission = computed(() => {
    const user = this.userService.currentUser();
    return !!(user?.permissions.canEmailBook || user?.permissions.admin);
  });

  readonly autoEmailOnBookAdd = computed(() => {
    const user = this.userService.currentUser();
    return user?.userSettings?.autoEmailOnBookAdd === true || user?.userSettings?.autoEmailOnBookAdd === 'true';
  });

  onAutoEmailToggle(checked: boolean): void {
    const user = this.userService.currentUser();
    if (user) {
      this.userService.updateUserSetting(user.id, 'autoEmailOnBookAdd', String(checked));
    }
  }
}
